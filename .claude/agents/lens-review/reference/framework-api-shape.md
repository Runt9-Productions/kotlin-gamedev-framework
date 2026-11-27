# Lens: framework-api-shape

Judges whether new or changed public API is shaped as a framework primitive or as an extract of whatever first needed it: consumer concepts leaked into the framework, a capability fused to the thing that uses it, a seam a test cannot substitute, an interface whose contract its signature does not state, and generality nothing uses.

## The question

Is this a framework primitive, or a consumer's code that happens to live in the framework?

kgdfw is written by the same person who writes the projects that consume it, so nothing forces an API here to be
general. The pressure runs the other way: a capability gets written where it is first needed, fused to the thing
that needed it, and is pulled apart later. That has happened repeatedly and deliberately: `42d8540` split the
randomizer into a serializable class that could be picked back up, `387dfd7` and `e3100e8` made the injector
testable, `14fcf1f` turned `ServiceAsync` from a global object into an injected class, and `fac89b1` broke the
queueing mechanism out into its own async primitive. Each was right and each was late. Catch the next one while it is
still cheap.

## Before the diff

Read "What ships, and why a defect is expensive" and "Rulings that are not findings" in
`.claude/code-review-profile.md`, and `.claude/reference/domains/event-and-state/overview.md` ("Threads" and "Work
tracking and idle checks") for the `AsyncFactory` seam and the `WorkSource` contract.

## In scope

1. **A consumer concept leaked into the framework.** kgdfw must not know what any one game's domain is: its cards,
   rounds, units or shops. A type, parameter, default or name that only makes sense for one consumer has escaped
   upward. The subtler form is a shape rather than a name: a hook that assumes exactly one of something, or an
   ordering only one consumer needs.
2. **A capability fused to the thing that happens to use it.** The recurring shape: a counter inside a service, a
   dispatcher built inline, a randomizer owned by a state object. Ask of any new mechanism whether a second consumer
   could use it without copying it. The answer is not automatically "extract it": extracting something with one user
   is item 5's defect. Report a mechanism that is already used two ways while living inside one of them.
3. **Something a test cannot substitute.** `ServiceAsync` was a global object, so no test could reach it and work
   dispatched there landed at an unpredictable moment (`14fcf1f`). The seam today is `AsyncFactory`: every
   dispatcher comes from it, because `TestAsyncFactory` overriding `newAsyncContext` is the only thing that makes
   async work drainable in a test. **A `newSingleThreadAsyncContext` call outside `AsyncFactory` re-creates that
   defect**, and the failure is invisible: the code works, and only tests silently stop being deterministic. Anything
   constructing its own dispatcher, scope, thread or clock is this finding.
4. **An interface whose contract an implementor who did not write it cannot meet.** A new interface should be
   implementable from its own declaration and docs. `WorkSource` is the live example: it is correct only when work is
   counted at submit time rather than at start, which the signature cannot show and only its KDoc states. An
   interface with an unstated timing, ordering or threading requirement will be implemented wrongly, and silently.
5. **Speculative generality.** The inverse failure, equally real: a parameter with one caller passing one value, a
   type parameter always bound to one type, a hook nothing registers, an option nothing sets. A knob added "for
   later" is a finding, not foresight. Weigh it against the rulings: some API with no caller here exists on purpose.

## Out of scope

Whether a value means what its consumer assumes belongs to `framework-contract`; re-entrancy, ordering and teardown to
`framework-lifecycle`; whether code runs on the right thread to `correctness` (item 3 is yours because it is a
substitutability question, the thread it then runs on is not); readability in general to `code-health`; code that
could simply be removed to `simplification`.

## How far to read

For a new or changed public declaration, find its callers with the language server's `findReferences` at the
declaration, which tells you whether a parameter has one caller and one value (item 5) and whether a mechanism
already has two kinds of user (item 2). Not `incomingCalls`, which has been measured on the Kotlin language server to
drop callers in test code. For a new interface, read every implementor (`goToImplementation`) and ask whether each
meets what the interface states or only what its author happened to know.

**Consumers are outside this repository.** A public declaration with no caller here is the normal case, not
evidence that nothing calls it, so a finding under item 5 must rest on what this repository shows (a parameter only
kgdfw itself passes, a hook only kgdfw registers). When the verdict turns on how a consumer uses the API, write an
open question naming the decision and what a look at a consumer would settle.

Two or three queries. Beyond that, write an open question naming the API decision you could not settle.

## Severity

High for item 3, and for item 1 or 4 on published API, since a consumer builds against it and changing it later is
a breaking release. Normal for item 2 and item 5, and for any of these on `internal` or test-fixture code. Never
Critical.
