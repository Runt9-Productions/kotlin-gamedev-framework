# Lens: framework-lifecycle

Checks what happens when framework code the engine calls runs twice, runs before what it depends on, runs with a degenerate value a callback legitimately supplies, or is torn down without undoing its setup, including event handlers registered without a matching unregister.

## The question

What happens if this runs twice, runs before the thing it depends on, or runs with a degenerate value?

A game framework's entry points are called by the engine, on its schedule. Minimize, resize, focus loss, a screen
change and a re-show all re-enter the same functions with whatever state the window is in. Application code may
assume it runs once, in order, with sane arguments. Framework code may not.

## Before the diff

Read "Fragile spots CI does not catch" in `.claude/code-review-profile.md`, and "EventBus" and "GameService
lifecycle" in `.claude/reference/domains/event-and-state/overview.md`.

## In scope

1. **An operation with no idempotence guard.** `6672560` added `if (currentMusicPlayer != null) return` to
   `SoundService.playMusic`, because re-entering a screen started a second player over the first. The shape: a
   function that creates or starts something and assigns it to a field, with nothing checking whether the field is
   already populated. Of anything called from `show()`, `load()`, `create()` or a screen transition, ask what a
   second call does.
2. **A degenerate value a lifecycle callback legitimately supplies.** `da28a86` added
   `if (Gdx.graphics.width == 0) return` to `SkinLoader.regenerateFonts`, which divided by `Gdx.graphics.width`.
   Minimizing a window makes its width zero, so the scale became infinity and font regeneration produced garbage.
   Any arithmetic on a window, viewport or display dimension in a callback needs the zero case.
3. **Initialization ordered before attachment.** `7b3b9f6` moved `component.view.init()` inside the `actor(...)`
   block because it had run before the actor was added to the stage, so anything `init` did that needed a parent, a
   stage or a computed size worked on an unattached actor. The general shape: an `init` or setup call that must
   observe state another call establishes, placed before that call.
4. **Teardown that does not undo setup.** `start`/`stop`, `show`/`hide` and `load`/`dispose` are pairs. Anything
   acquired, registered, started or subscribed in one half needs releasing in the other, and a half-pair is a leak
   that shows only on the second visit. Event handlers are the commonest case: `EventBus` keeps them in a list with
   no deduplication, so N registrations against one unregister leave N-1 live copies and the event fires N-1 times.
   `GameService.start()` and `stop()` pair registration correctly and are the shape to match. Do not blur stop and
   dispose: `AsyncWorkQueue.stop()` cancels the loop but keeps the count so `start()` can resume, while `dispose()`
   zeroes it because nothing will drain it again.
5. **A default that starts state past its own first transition.** Seen in a consumer: a stage enum defaulted to the
   in-progress value rather than the starting one, so the start hook never fired for the first cycle and an effect
   keyed to it was correct and never ran. Any enum or flag whose initial value represents a state after the
   transition that is supposed to fire has this shape.

## Out of scope

Whether a value means what its consumer thinks belongs to `framework-contract`; whether the abstraction is well
shaped to `framework-api-shape`; which thread code runs on, and deadlock, to `correctness`.

## How far to read

For a suspected missing idempotence guard, find the callers with the language server's `findReferences` at the
function's declaration, which tells you whether anything can reach it twice. Not `incomingCalls`, which has been
measured on the Kotlin language server to drop callers in test code. A function with one caller on one path is not
a finding, and you cannot tell without looking. Framework entry points are also called by consumers outside this
repository, so a public `show`, `load` or `start` is reachable twice whatever the callers here show.

For an ordering question, read the enclosing function in full: ordering bugs are visible in one function body.

One or two reads. If it is still open after that, write an open question naming the re-entry or ordering you could
not rule out.

## Severity

High for a missing guard, a degenerate value or a half-pair reachable through an ordinary lifecycle event (a
re-show, a minimize, a screen change), since every consumer inherits it. Normal when only an unusual sequence reaches
it. Never Critical.
