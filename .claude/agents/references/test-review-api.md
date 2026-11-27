# Test review reference: `api`

Read by `test-review:test-reviewer` for specs under `api/src/test/`. The generic criteria are in the agent; this file
is what is specific to testing the development HTTP harness. Read `.claude/reference/domains/api-harness/overview.md`
before breaking anything in `api/src/main/`.

## Why these are easy to write uselessly

The harness exists to cross onto a running game's rendering thread, and a unit test has no game, no GL context and
no render loop. So most of what can go wrong in `api` (a missed render hop, a route registered after `serve`, a
serializer erased by a non-reified frame) cannot be reached from a spec here at all. What can be reached is the pure
logic around it, chiefly screen resolution and the wire format, and that logic is decided by inputs the obvious test
does not supply.

## Failure modes

1. **A screen-matching test without the subclass case.** `ApiScreen.of` matches with `isInstance` and
   `forController` with `==`. Every input except a subclass of a declared controller gives both the same answer, so
   a suite without the subclass cases passes when either is rewritten to the other's predicate. `ApiScreenTest`'s
   KDoc states this; keep its subclass and sibling cases.
2. **A registry leaked from an earlier test.** `ApiScreen.screens` is process-global with no teardown, so a test that
   registers screens without clearing first lets an earlier test's entry decide its result. `ApiScreenTest` clears in
   `beforeTest`; a new spec touching screens does the same.
3. **A stub controller that answers too much.** A stub whose `vm` or `view` returns something usable can let a test
   pass through code that should never have touched them. The stubs in `ApiScreenTest` throw from both, so a
   resolution path that starts reading the ViewModel fails rather than passing.
4. **A wire-format test that only round-trips known values.** `ApiScreenSerializer` writes a screen's `route`, and
   `deserialize` resolves an unrecognized route to `UNKNOWN` instead of failing. A round trip over registered screens
   alone passes a deserializer that ignores its input for the one value tried; include an unregistered route and a
   second registered one.
5. **A test that claims the render hop.** Nothing here can show that an endpoint crosses onto the rendering thread,
   since there is no rendering thread. A test named for the hop that stubs `onRenderingThread` away asserts only the
   stub. Report the behavior as untested rather than accepting such a test as coverage.

## Running a forced failure

- Run with `./gradlew :api:test --tests '<spec FQCN>' --console=plain --rerun`, and check the `TEST-SUMMARY` counts;
  for one test append `.<test name>` and confirm `passed` plus `failed` is 1.
- `api` compiles against `core`, so a break in `core/src/main/` can redden `api` specs too. Run `:core:test` in a
  separate invocation when a break touches `core`.
- After restoring, check `git status` and `git diff` over `api/src/main/` before the final run.
