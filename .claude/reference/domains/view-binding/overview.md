---
title: View Binding
type: note
permalink: view-binding/overview
tags: [view-binding, mvvm, ui]
verified: 2026-08-13
branch: master
coverage: partial
sources:
  - core/src/main/kotlin/com/runt9/kgdf/ui/viewModel/ViewModel.kt
  - core/src/main/kotlin/com/runt9/kgdf/ui/Updatable.kt
  - core/src/main/kotlin/com/runt9/kgdf/ext/ui/BindingExt.kt
---

# View Binding

> **Scope.** `ViewModel.kt`, `Updatable.kt` and `BindingExt.kt` were read in full. Not covered: the `ui/view/` classes (`View`, `TableView`, `DialogView`, `GroupView`, `ScreenView`), the Scene2D/KTX layer these helpers sit on, and which thread a binding may safely be mutated from.

How a `ViewModel` field notifies the UI when it changes. Read this before concluding that a "state changed but the screen did not update" report is a state bug — usually the state is fine and the notification is what went missing, silently.

## Observations

- [invariant] `Binding.set` opens with `if (value == currentValue) return`, **before** notifying anything. A binding re-set to an equal value updates no subscriber, marks nothing dirty, and reports nothing #silent-failure. This is the single most important line in the file.
- [trap] Consequently, **mutating a collection in place and re-setting the same instance is a no-op**. The value equals the current one and `set` returns early. Build a new collection instead.
- [trap] Consequently also, **matching is only as good as the value type's `equals`**. If a type's `equals` ignores the fields the UI actually displays, a binding over it never fires when those fields change. This is not a framework bug and the framework cannot detect it — it is a contract the consumer has to hold up.
- [trap] **Reading a binding inside an `Updatable` subscribes to it.** `Updatable` declares `operator fun <T : Any> ViewModel.Binding<T>.invoke(): T { bind(this@Updatable); return get() }`, so writing `someBinding()` inside an updater block both registers the updater and returns the value. Subscription is a side effect of what looks like a getter, and there is no `bind` call at the site to notice #silent-failure.
- [fact] `Binding.bind(updateFn)` wraps the lambda in an `Updatable`, binds it, and calls `update()` immediately — bindings fire eagerly at bind time, before any later change.
- [fact] `bindUpdatable` and `bindUpdatables` in `BindingExt` default `evaluateOnCall = true` and likewise call `update()` right after binding.
- [fact] `Binding.binds` is a `mutableSetOf<Updatable>`, and the `updatable { }` factory returns a fresh anonymous object per call with default identity equality. Each call therefore adds a **distinct** subscriber; the set cannot collapse two logically-identical updaters.
- [risk] That is the most plausible reading of the unresolved `// TODO: Looks like bindings are stacking up over time?` above `bindLabelText`. Rebuilding a view re-runs the helper, each run creates a new `Updatable`, and nothing removes the previous ones short of `Binding.dispose()`. Not confirmed against a reproduction — treat the helper as known-suspect rather than asserting a leak.
- [fact] `ListBinding.bindAdd` replays every current item at bind time (`currentValue.forEach(updatable::update)`); `bindRemove` registers without replaying.
- [fact] `ListBinding.add`/`remove` route through `set` with a newly constructed list, so the equality short-circuit does not suppress them, and they then notify their own add/remove subscribers.
- [trap] `Binding.dispose()` clears `binds` **and** resets `currentValue` back to `savedValue`. A view re-shown without re-binding renders the saved value, not the live one.
- [fact] Every `Binding` registers itself into its `ViewModel`'s `fields` list on construction, which is what makes `ViewModel.dispose()` and `saveCurrent()` able to sweep all of them.
- [fact] Dirty tracking is per binding against `savedValue`, recomputed on every `set` and aggregated into the ViewModel's own `dirty` binding; `saveCurrent()` commits every field and clears it. `bindButtonDisabledToVmDirty` is the intended consumer.
- [fact] `bindChecked` is the one helper that binds in the other direction: it seeds the checkbox from the binding, then writes back through `onChange`.

## Relations

- see_also [[Interception]]
- see_also [[Development API Harness]]
