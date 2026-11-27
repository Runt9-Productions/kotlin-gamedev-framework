# Test review reference: `core`

Read by `test-review:test-reviewer` for specs under `core/src/test/`. The generic criteria are in the agent; this file
is what is specific to testing the framework's core. The mechanisms behind each entry are in the knowledge base note
named; read it before breaking that code.

## Why these are easy to write uselessly

Most of `core` is asynchronous, process-global or reflective, and each of those turns a wrong test green in its own
way. Work dispatched to a test scheduler does not run until the scheduler advances, so "nothing happened" is also what
a test that forgot to advance sees. State held in a Kotlin `object` outlives the spec that set it. And dispatch that
goes through reflection fails at run time, in a way that reads as a production defect.

## Failure modes

1. **An assertion that nothing happened, under a scheduler nobody advanced.** `TestAsyncFactory` hands out
   `StandardTestDispatcher`s, which queue work until the scheduler advances. A test that enqueues and asserts that a
   handler did not run proves nothing unless it also advances and shows the same setup does fire for the case that
   should. `EventBusTest`'s exact-class test is the shape: the base event first, asserted to arrive, then the
   subclass. Skipping the advance on purpose is a real assertion tool ("submit counts the item before the handler has
   run" in `AsyncWorkQueueTest`), but only when the test says so and asserts the work is still outstanding
   (`domains/event-and-state/overview.md`, "Threads").
2. **The wrong scheduler advanced.** A spec with `coroutineTestScope = true` advances Kotest's
   `testCoroutineScheduler`; a factory built over a fresh `TestCoroutineScheduler()` is drained by nothing that
   advances the other one. Check that the scheduler the test advances is the one its `TestAsyncFactory` was built
   over. Not yet observed here; it follows from the two shapes the suite uses side by side.
3. **Process-global state carried between specs.** `KgdfLog.sink` and `KgdfLog.minLevel`, `AssetRegistry`, the
   `Injector` and `ApiScreen.screens` are process-wide and have no teardown. A spec that assigns one without
   restoring it makes a later spec's result depend on run order: it passes alone under `--tests` and fails in the
   suite, or the reverse. Logging goes through `capturingLogs { }`, which restores in a `finally`
   (`domains/logging/overview.md`). The `Injector` cannot be reset at all, by the owner's ruling; a test needing a
   fresh container is out of reach, not a fixture to write.
4. **A test that writes to the real machine.** `GameConfig`'s `gameDataPath` defaults to the user's preferences
   directory, so a spec that builds a `GameConfig` without one reads and writes the reviewer's own settings and
   saves, and its result depends on what is already there. `GameStateServiceTest` passes a temporary directory and
   deletes it after each test.
5. **A reflective dispatch the fixture cannot reach.** `EventBus` invokes handlers through `KCallable.call` without
   `isAccessible`, so a recorder class declared `private` registers and then throws `IllegalCallableAccessException`
   on its first event. A red from that is a fixture failure and says nothing about the break being probed. The
   recorders in `EventBusTest` are public for this reason.
6. **A reload test that compares counters.** A `SeededRandomizer` counter can round-trip perfectly and still replay
   to the wrong position, which is exactly the defect fixed in 2.0.7. A reload test compares the draws after the
   reload, and carries a control with the saved counter one step off that must differ
   (`domains/seeded-randomizer/overview.md`). A counter delta is the draw kind's step cost, fixed only for unbounded
   draws, so an exact delta on a bounded `nextInt` or a shuffle can be right or wrong by luck of the seed.
7. **A buried assertion.** Kotest stops a test at its first failing assertion, so a break that trips an early guard
   leaves every later assertion unevaluated, and "did not fail" then looks the same as "was never reached". When a
   break reddens a multi-assertion test, read the failure text and confirm it came from the assertion the break was
   aimed at.
8. **`withData` arguments are built when the spec object is constructed**, before any `beforeTest` runs. A data
   table read from state a hook sets up is built from the state before it.
9. **A real wait.** `delay` inside a test-scheduled coroutine is virtual and costs nothing; `Thread.sleep`, a real
   `runBlocking` timeout or a context built outside `AsyncFactory` makes the test timing-dependent and, on a loaded
   CI runner, flaky.

## Running a forced failure

- Break production code under `core/src/main/` only. `core/src/testFixtures` is a helper and is published, so a
  break there tests the helper, not the code.
- Run one spec with `./gradlew :core:test --tests '<spec FQCN>' --console=plain --rerun`, and check the
  `TEST-SUMMARY` counts against the tests the spec declares. For one test, append `.<test name>` and confirm
  `passed` plus `failed` is 1, since a misspelled name passes with everything skipped.
- A `withData` row's test name is the row's `nameFn` output, nested under its context; select its spec rather than
  guessing the nested name.
- A Kotlin `BackendException ... during IR lowering` after a break edit is the incremental compiler, not the edit.
  Rebuild with `-Pkotlin.incremental=false` before reading anything into it.
- After restoring, check `git status` and `git diff` over `core/src/main/` before the final run.
