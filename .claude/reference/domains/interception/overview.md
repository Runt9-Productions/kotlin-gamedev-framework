---
title: Interception
type: note
permalink: interception/overview
tags: [interception, effects, extensibility]
verified: 2026-08-13
branch: master
coverage: partial
sources:
  - core/src/main/kotlin/com/runt9/kgdf/intercept/Interceptor.kt
  - core/src/main/kotlin/com/runt9/kgdf/intercept/InterceptableContext.kt
  - core/src/main/kotlin/com/runt9/kgdf/intercept/InterceptorHolder.kt
  - core/src/main/kotlin/com/runt9/kgdf/intercept/InterceptorHook.kt
  - core/src/main/kotlin/com/runt9/kgdf/intercept/OnChangeInterceptable.kt
---

# Interception

> **Scope.** All five files in `com.runt9.kgdf.intercept` were read in full. Not covered: how any consuming project defines its own hooks and contexts (that belongs in the consumer's KB), and the `BaseInterceptorHook.ON_UI_SCALE_CHANGE` path, which is named but never traced to a caller.

The extension point the whole framework hangs off: a consumer registers interceptors against a hook and a context type, and dispatch runs the ones that match. Read this before adding a hook, a context, or anything that registers an interceptor.

**Everything that goes wrong here is silent.** The type system is deliberately bypassed at registration, so a mismatch produces no compile error, no exception and no log line — just an interceptor that never runs. There is no symptom to notice.

## Observations

- [invariant] `canIntercept` is **exact runtime class equality**. The `intercept()` factory defines it as `context::class == T::class`, with `T` reified from the lambda's declared parameter. Not a supertype, not a subtype #silent-failure.
- [trap] The type that decides matching is the parameter annotation in the lambda — `addInterceptor(SOME_HOOK) { ctx: SomeContext -> ... }`. Declaring a supertype there registers successfully and never fires.
- [fact] `InterceptorHolder.addInterceptor` casts `Interceptor<T>` to `Interceptor<InterceptableContext>` under a file-level `@Suppress("UNCHECKED_CAST")`, so the compiler cannot catch a mismatch and `canIntercept` is the only guard standing between a wrong context and a bad cast at dispatch.
- [trap] **Four functions are named `intercept` and two share arity.** The top-level factory `intercept(hook, lambda)` *builds* an `Interceptor`; the method `InterceptableContext.intercept(hook, context)` *dispatches*. `OnChangeInterceptable.onChange` calls the factory — a reader skimming for dispatch sites will misread it.
- [trap] `InterceptableContext.intercept(hook, context)` iterates `context.interceptors[hook]` and invokes every one **with no `canIntercept` check at all**, unlike the single-argument `intercept(hook)` which filters. Whether exact-class matching applies therefore depends on which overload the caller used.

The four `intercept`-named declarations, registration through to both dispatch paths — this is the shape a skimming reader misreads:

```mermaid
flowchart TD
    Reg["Consumer: addInterceptor(hook) { ctx: T -> ... }\n3-arg overload on InterceptorHolder"]
    Reg --> Factory["top-level intercept(hook, interceptorFn)\nInterceptor.kt:10 -- BUILDS an Interceptor of T\ncanIntercept = context::class == T::class"]
    Factory --> Store["stored in interceptors map, keyed by hook\nInterceptorHolder.interceptors"]

    Store --> Dispatch1
    Store --> Dispatch2

    Dispatch1["ctx.intercept(hook)\n1-arg -- InterceptableContext.kt:5\nFILTERING dispatch"]
    Dispatch1 --> Check{"canIntercept(ctx):\nctx::class == T::class ?"}
    Check -->|"true"| Run1["interceptor.intercept(ctx) runs"]
    Check -->|"false"| Skip["silently skipped -- no error, no log"]

    Dispatch2["ctx.intercept(hook, context)\n2-arg -- InterceptableContext.kt:9\nNON-FILTERING dispatch"]
    Dispatch2 --> RunAll["every interceptor for hook runs unconditionally\nno canIntercept call at all"]
```

- [fact] As of this reading, nothing in kgdfw calls the non-filtering two-argument overload, and a survey of a consuming project found none either — all 29 of its dispatch sites use the single-arg form or their own wrapper. It is dead API rather than a live hazard, but it will bypass matching if someone reaches for it.
- [fact] Interceptors are stored as `MutableMap<InterceptorHook, MutableSet<Interceptor<InterceptableContext>>>`, keyed per hook.
- [trap] The `intercept()` factory returns a fresh anonymous `object : Interceptor<T>` on every call, with default identity equality, so the backing `MutableSet` does **not** deduplicate logically-identical registrations. Registering the same interceptor twice yields two members and it fires twice #silent-failure.
- [fact] `removeInterceptors(holder)` calls `removeAll` on the holder's own interceptor instances, so removal only works when the caller still holds the exact instances that were added.
- [fact] `addInterceptors(holder)` unions another holder's whole map into this one, per hook. It is the mechanism a consumer uses to aggregate interceptors onto a freshly built context.
- [fact] `InterceptorHook` is an empty marker interface; consumers declare their own enums implementing it. `BaseInterceptorHook` ships two values, `ON_CHANGE` and `ON_UI_SCALE_CHANGE`.
- [fact] `InterceptorHolderAdapter` supplies an empty mutable map; `InterceptableAdapter` extends it and adds `InterceptableContext`, and is the normal base class for a context type.
- [fact] `OnChangeInterceptable.update(consumer)` applies a mutation and then dispatches `BaseInterceptorHook.ON_CHANGE` through the *filtering* overload. Its `(this as T)` is an unchecked cast on an unbounded type parameter.

## Relations

- see_also [[View Binding]]
- see_also [[Events, Async and State]]
