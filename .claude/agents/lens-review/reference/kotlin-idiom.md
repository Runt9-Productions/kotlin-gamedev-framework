# Lens: kotlin-idiom

Pattern-matches a diff's Kotlin for three slips that compile cleanly and behave wrongly: a bare `return` inside a lambda, a setter that updates the live value but not its own field, and a routine outcome logged as an error.

## The question

Does this Kotlin do what it looks like it does?

Everything here compiles, passes review by eye, and does the wrong thing at runtime. None of it needs an
understanding of what the code is for; a check that needs you to reason about the domain is not this lens's.

## Before the diff

Nothing beyond the diff.

## In scope

1. **A bare `return` inside a lambda.** `6672560` fixed
   `skin.getAll<BitmapFont>()?.forEach { ... if (currentFontSize == newFontSize) return ... }`. Inside a lambda
   passed to an inline function, a bare `return` returns from the enclosing function, not from the iteration, so the
   first font that needed no regeneration aborted it for every remaining font, silently. Flag a bare `return` inside
   `forEach`, `map`, `filter`, `let`, `apply`, `run`, `also`, `takeIf` or any inline function taking a lambda, where
   the intent reads as "skip this one". The fix is `return@forEach` or the matching label. A bare `return` there is
   right only when aborting the whole enclosing function is meant, which is rare enough to ask about every time.
2. **A setter that updates the live value but not its own field.** `6672560` fixed `MusicPlayer.adjustVolume`, which
   did `currentSong?.volume = volume` and never `this.volume = volume`, so the current song changed volume and the
   next song reverted to the stale field. Flag a parameter named like a property of the enclosing class, where the
   body assigns the parameter onward but never to `this.<name>`. The symptom always appears at the next transition,
   never at the call.
3. **A routine outcome logged as an error.** `1e18859` fixed an `EventBus` loop that logged at error level on any
   failed `receiveCatching()`, but a cleanly closed channel is a failure with a null exception, so ordinary shutdown
   logged an error every time. Flag error-level logging on a result, branch or exception path that also covers a
   routine case: a closed channel, cancellation, an empty collection, a missing optional file, a first run with no
   saved state. The tell is an error message that would fire during normal startup or shutdown.

## Out of scope

Whether the logic is correct, which every other lens covers; style and naming the codebase does not enforce; anything
that needs a value traced across files. A `catch` that rethrows `CancellationException` before catching `Exception`
is correct and deliberate here, not a finding.

## How far to read

The diff, plus at most one read of the enclosing function to confirm the shape. All three are visible within one
function body. Do not open other files.

## Severity

High for a bare `return` in a lambda and for a stale-field setter, both silent wrong behavior. Normal for a routine
outcome logged as an error, unless the noise would hide a real error in the same log, which makes it High.

State the concrete failure, not the rule: "the first already-correct font aborts regeneration for every remaining
font" is a finding, "bare return in a lambda is bad practice" is not.
