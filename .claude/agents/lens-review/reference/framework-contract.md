# Lens: framework-contract

Checks whether what a value guarantees matches what the code receiving it assumes: a path's absolute or relative meaning lost at a boundary, a platform assumption one OS cannot falsify, a configured instance shadowed by its static default, a persisted type that stops reading old files, and a name promising a guarantee the code does not give.

## The question

Does what this value actually guarantees match what its consumer assumes about it?

When producer and consumer are written by the same person, the contract between them never gets written down, and
the disagreement surfaces months later on a machine or a file that is not the author's. Every item below is a real
fix in this repository's history, and they share one shape: **the caller assumed something the callee never
promised.**

## Before the diff

Read "Contracts" and "Fragile spots CI does not catch" in `.claude/code-review-profile.md`.

## In scope

1. **A path's absolute or relative meaning, lost at a boundary.** Fixed twice, eight months apart. `03ce69d`:
   `teeStderrToFile` created the file's parent directory and then wrote to the bare file name, relative to the
   process working directory. `50f604c`: `getDefaultPreferencesDirectory()` returned `.config` and
   `Library/Preferences` while callers handed the result to libGDX as `Files.FileType.Absolute`, so saves landed
   beside wherever the game was launched from, and launching from elsewhere read a different save. For any `Path` or
   `String` naming a location that crosses a function boundary, ask what the far side does with it.
   `FileType.Absolute` and `FileType.External` mean opposite things about the same string.
2. **A platform assumption the author's own OS cannot falsify.** The `50f604c` fix says so in its own comment: the
   relative path looked correct on Windows because `APPDATA` is already absolute. kgdfw ships to Windows, macOS and
   Linux. Anything branching on `UIUtils.isWindows`, `isMac` or `isLinux`, reading an environment variable, or
   joining path segments by hand is a place where testing on one platform proves nothing. The same fix had to tell
   an empty `XDG_CONFIG_HOME` from an unset one: a variable that exists and is blank is not absent, and `?:` alone
   does not catch it.
3. **A configured instance shadowed by its static default.** `c4dc7b8`: `SettingsStore` decoded with
   `Json.decodeFromStream`, the strict companion object, while encoding through its configured `json` property.
   Encode and decode disagreed about `ignoreUnknownKeys`, which shows only once a file holds a key the strict side
   rejects. Look for a capitalized companion (`Json.`, `Cbor.`) where a configured instance of the same type is in
   scope.
4. **A persisted type that stops reading files written before the change.** `7b3b9f6` added `refreshRate` to
   `PlayerSettings.Resolution`, a `@Serializable` type read from an existing settings file, with no default, so every
   install that already had a file threw `MissingFieldException`. `PlayerSettings` declares its keys as abstract
   properties, so a new or renamed key obliges every consumer's implementation to give it a default; the `minLogLevel`
   invariant in `.claude/reference/domains/logging/overview.md` is the worked case. Of any new field on a `@Serializable` type ask what happens to
   a file written before it existed, and of a renamed one, what happens to the old key.
5. **A name that promises a guarantee the code does not give.** `1e18859` split `update {}`, which had been
   fire-and-forget on the Service-Thread, into a synchronous `update` and an explicit `updateAsync`, because callers
   read the name as "the mutation has happened when this returns". An earlier `enqueueEventSync` meant only that the
   caller need not suspend, and was misread as synchronous dispatch three separate times. A name that implies
   completion, ordering or blocking must provide it.

## Out of scope

Whether an operation is safe to run twice or in the wrong order belongs to `framework-lifecycle`; whether the
abstraction is shaped right to `framework-api-shape`; mechanical Kotlin slips to `kotlin-idiom`; whether code runs on
the right thread to `correctness`. Where a name implies synchrony (item 5), report the naming half here and the
thread half under `correctness`.

## How far to read

For a path or a serialized value, one read of the receiving call site usually settles it: open what receives the
value and see what it assumes. Find it with the language server's `findReferences` rather than a text search, since
these values pass through wrappers and a character match misses the hop that matters. For a new field on a persisted
type, check the declaration for a default and the format for `ignoreUnknownKeys` or a migration: two reads, and
decisive.

Do not chase a value more than a couple of hops. If two reads have not settled it, write an open question naming the
boundary you could not resolve.

## Severity

Critical when a published change makes existing saved files or settings unreadable for every consumer that has one
(item 4). High for the other items when the mismatch is reachable on a supported platform or by an ordinary caller.
Normal when it needs an unusual setup to show.
