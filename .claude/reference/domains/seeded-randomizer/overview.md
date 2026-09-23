---
title: Seeded Randomizer
type: note
permalink: seeded-randomizer/overview
tags: [ rng, persistence, serialization, determinism ]
verified: 2026-09-16
branch: claude/seeded-randomizer-step-count
coverage: partial
sources:
  - core/src/main/kotlin/com/runt9/kgdf/util/SeededRandomizer.kt
  - core/src/main/kotlin/com/runt9/kgdf/util/GameStateRandomizers.kt
  - core/src/test/kotlin/com/runt9/kgdf/util/SeededRandomizerReloadTest.kt
---

# Seeded Randomizer

> **Scope.** `SeededRandomizer`, `GameStateRandomizers` and `SeededRandomizerReloadTest` were read in full. Not covered: `com.runt9.kgdf.ext.random` (the `ClosedFloatingPointRange<Float>.random` behind `randomRange`, imported but not opened), `SingleFileSaveStateService` beyond the CBOR configuration the test copies, and the Kotlin stdlib's `Random` and `XorWowRandom`. **Every per-draw step cost below comes from the class's own comment, the test's row names and the commit message, not from reading the stdlib.** No test was run for this note, so "the reload rows pass" rests on the commit that added them.

A reproducible random stream that survives serialization by storing a counter instead of the generator. Read this before persisting one, before asserting on `rngCounter` in a test, and before touching the wrapper class inside it: the counter is only useful if it counts exactly what the replay replays, and every way of getting that wrong resumes a reloaded stream somewhere else with no error.

## Observations

### What is persisted and how it comes back

- [fact] `SeededRandomizer` is `@Serializable` with exactly two constructor properties, `seed` and `rngCounter`. The generator is a `@Transient` property, rebuilt at construction as `StepCountingRandom(Random(seed).apply { repeat(rngCounter) { nextInt() } })` (`SeededRandomizer.kt:14`).
- [invariant] **Reload fidelity requires `rngCounter` to equal the number of generator steps taken**, because the rebuild advances one `nextInt()` per count. Any other meaning for the counter resumes a reloaded stream at a different position, and nothing reports it #silent-failure
- [fact] The replay runs on the bare generator, before it is wrapped, so reconstruction does not count its own steps.

### How the counter counts

- [fact] `StepCountingRandom` is a private `inner class` extending `Random` that overrides only `nextBits`: it increments the outer `rngCounter`, then delegates to the wrapped generator (`SeededRandomizer.kt:40-45`).
- [fact] `randomizeBasic` no longer touches the counter at all; it is `action(rng)` (`:16`). `randomize`, `percentChance`, `coinFlip`, `randomRange` and `randomFromCollection` all route through it, so every draw kind is counted by the wrapper and none by the call.
- [trap] **Override nothing else on the wrapper.** Its comment is the reason: every other `Random` method reaches the generator through `nextBits`, and one `nextBits` on a seeded generator is one step. Overriding `nextInt()` directly, say for speed, would draw from the generator without passing through the count #silent-failure (stdlib behavior as stated in that comment; not re-read)
- [trap] **A counter delta is a step cost, not a number of calls.** Per the test's row names and the commit message: `percentChance`, `randomRange` and `coinFlip` cost one step, two when `lucky`; `nextDouble` and `nextLong` cost two; `nextInt(bound)` can cost more when rejection sampling redraws; a shuffle of n is n − 1 bounded draws, and a shuffle of 0 or 1 costs nothing. A consumer test asserting "exactly one draw" is asserting that draw kind's step cost, which is only fixed for the unbounded kinds.
- [trap] **`rngCounter` is a public `var`, and writing it moves only what a reload will replay.** The live generator is already built and ignores it. `SeededRandomizerReloadTest` relies on exactly that to build its one-step-off control: bump the counter, reload, restore.
- [fact] Counting does not change what is drawn. "a randomizer draws the same values as a bare generator on the same seed" compares 25 calls of mixed draws (`nextInt`, bounded `nextInt` including a rejection-prone bound, `nextFloat`, `nextBoolean`, `nextDouble`, `nextLong`, a shuffle of 24) against `Random(seed)`.

### What the tests pin

- [fact] `SeededRandomizerReloadTest` runs twelve draw kinds, 25 calls each, reloads through CBOR configured as `SingleFileSaveStateService` configures it (`encodeDefaults = true`, `ignoreUnknownKeys = true`), and compares the next ten `nextInt()` draws of the reloaded and live randomizers.
- [fact] Each kind has a control with the saved counter one step off, asserting the probe **differs**. Without it, a reload row could pass by comparing two streams that happen to agree.
- [trap] **An intact counter round-trip is not evidence the stream resumes.** A counter can serialize and decode perfectly and still replay to the wrong position; that is precisely what the call-counting version did. Compare draws after the reload, never counters.

### History and old saves

- [history] Through 2.0.6, `randomizeBasic` did `rngCounter++` once per call. Any call costing other than one step left the counter out of step with the generator: multi-element shuffles above all, and also empty or singleton shuffles (zero steps against a count of one), rejection redraws, `nextDouble` and `nextLong`. The commit that fixed it (`32716ea`, released as 2.0.7 on 2026-09-16) reports 25 shuffles of 24 measured through the save-file CBOR as 575 steps in against a counter of 25 #silent-failure
- [trap] **A save written before 2.0.7 carries a call count, and its first reload resumes exactly where the old code would have**, which is the wrong place. The rebuild replays the stored count unchanged, and counting is correct only from that point on. The true position is not recoverable, because a call count does not encode how many steps each call took #irreversible
- [decision] The save format is unchanged: the same two properties, so no migration and no version gate.

### Reaching it from game state

- [fact] `GameStateRandomizers.kt` declares a `GameState` extension for every drawing method, each delegating to `rng`. Inside any `GameState` receiver a bare `randomizeBasic { }` or `percentChance(...)` is therefore the seeded one, even though nothing at the call site says so.

## Relations

- see_also [[Events, Async and State]]
