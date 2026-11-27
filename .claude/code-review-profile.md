# Code review profile

Facts about this repository that `/lens-review` needs. The reviewer reads every section; each lens names the sections
it judges against.

## What ships, and why a defect is expensive

kotlin-gamedev-framework (kgdfw) is a public, GPL-3.0 Kotlin game framework over libGDX and KTX, published to GitHub
Packages as `com.runt9.kgdfw:core` and `com.runt9.kgdfw:api` (the `core` test fixtures ship in the same
publication). Other projects consume it by pinning a version, and **a merge into `master` is a release**: whatever
merges reaches every consumer on its next pin bump, and only another release fixes it. The framework has no
published documentation and almost no training data, so an agent working in a consumer trusts the KDoc and the
knowledge base, and most of the framework's hazards fail silently: no compile error, no exception, no log line.
Everything merged here is public for good, and git history keeps it after a later fix.

## Fetching the diff

| Target | Command |
|---|---|
| Working tree | `git diff HEAD` (staged and unstaged changes, and new files once staged) |
| Commit | `git show <sha>` |
| Range | `git diff <a>..<b>` |

The skill fetches a pull request itself with `gh`. `gh` answers `HTTP 401: Bad credentials` until `GH_TOKEN` is set
in the shell that runs it, and how that token is minted is machine-local, not in this repository. Without it, review
the pull request's commits as a range instead.

- Default branch: `master`.
- No path is excluded from review. `gradle/wrapper/` and `gradlew*` are relevant only to `guard-security` and
  `public-leak`; other lenses may skip them.

## Output

- Root: `.claude/output/code-review/`, git-ignored by the tracked `.gitignore`. Each run writes its own directory
  under it.

## Lenses

Default: `correctness`, `code-health`, `simplification`, `staleness-and-debris`, `public-leak`. These are the lenses
`default` applies. `auto` does not read this list: it applies every lens whose routing row matches, then any other the
reviewer judges the change needs. `public-leak` is a default and matches every change because every merged line
becomes public, and nothing here may name a consuming project.

Routing, binding under `auto` when the diff matches any one pattern in a row; a path pattern that matches a changed
file binds whatever the code in it does:

| Lens | Matches |
|---|---|
| `public-leak` | any change |
| `test-quality` | any file under `*/src/test/` or `*/src/testFixtures/` added or changed |
| `kotlin-idiom` | any `*.kt` file added or changed |
| `framework-api-shape` | a public or protected declaration added or changed under `*/src/main/` or `*/src/testFixtures/`; a new interface or abstract class |
| `framework-contract` | `core/src/main/kotlin/com/runt9/kgdf/settings/`; `core/src/main/kotlin/com/runt9/kgdf/util/Preferences.kt`; `core/src/main/kotlin/com/runt9/kgdf/service/SingleFileSaveStateService.kt`; `core/src/main/kotlin/com/runt9/kgdf/serialization/`; code that names a file location, reads an environment variable, branches on `UIUtils.isWindows`/`isMac`/`isLinux`, builds a `Json` or `Cbor` instance, or adds or renames a property of a `@Serializable` type; a rename that changes what a function's name promises |
| `framework-lifecycle` | code called from `show`, `hide`, `load`, `dispose`, `start`, `stop`, `create`, `resize` or `init`; code that registers or unregisters an event handler or interceptor; arithmetic on a window, viewport or display dimension |
| `guard-security` | anything under `.github/`; `gradle/wrapper/`; `gradle/libs.versions.toml`; `buildSrc/`; any `build.gradle.kts`; `settings.gradle.kts`; `gradle.properties`; `api/src/main/`, which serves HTTP |

## Severity calibration

The scale is Critical (Must Fix), High (Should Fix), Normal (Could Fix). Reference points for this repo:

- **Critical**: a published change that makes saved files or settings written by an earlier release unreadable; an
  interceptor, event handler or binding that silently stops firing for every consumer; a CI check that runs and
  checks nothing; a secret, a machine path or a private repository's content anywhere in the diff, its messages or its
  file names.
- **High**: a contract changed on one side only (see "Contracts"); a consuming project or its ticket named; a
  knowledge base note or `CLAUDE.md` line the change makes false; a new public API shaped for one consumer; a test
  that cannot fail.
- **Normal**: leftover debug output; a missing KDoc where a consumer could misuse the API without it; history or
  incident narrative in a comment; a stale `verified:` date.

A Normal is optional: the author may decline one with a one-line reason. A finding whose only fix is a guard for a
case no honest caller reaches is not reported. A finding that removes code is worth as much as one that adds it.

## Fragile spots CI does not catch

Each fails with no compile error, exception or log line. The knowledge base note named holds the detail.

- **Exact-class matching.** `canIntercept` is `context::class == T::class`, reified from the lambda's declared
  parameter, and `EventBus` dispatches on `eventHandlers[event::class]`. A supertype in either place registers and
  never fires. The two-argument `InterceptableContext.intercept(hook, context)` skips `canIntercept` entirely
  (`domains/interception/overview.md`).
- **No deduplication.** Event handlers sit in a `MutableList` and interceptors in a set of identity-equal anonymous
  objects, so a second registration fires twice and one unregister removes one copy.
- **`enqueueEvent` returns before dispatch**, and a slow handler blocks every event behind it. `awaitIdle()` from
  inside a handler waits on itself (`domains/event-and-state/overview.md`).
- **`GameStateService`.** `save` is a no-op when the state equals the cache and `forceUpdate` is false, and the
  update event sits inside the same guard, so a dropped write also fires no event. `load` returns a clone; `peek`
  hands over the live cache; `update {}` is synchronous and `updateAsync {}` is not.
- **Bindings.** `Binding.set` returns early on an equal value, and reading a binding inside an `Updatable`
  subscribes to it (`domains/view-binding/overview.md`).
- **The async seam.** `AsyncFactory` is the only caller of `newSingleThreadAsyncContext`; a context built any other
  way runs on a real thread no test scheduler drains. A `WorkSource` must count work on submit, not on start.
- **`SeededRandomizer`.** `rngCounter` must equal generator steps; the wrapper overrides only `nextBits`, and an
  intact counter round-trip proves nothing about the stream (`domains/seeded-randomizer/overview.md`).
- **Logging.** Renaming `Logger.kt` changes the `LoggerKt` class name in `SELF_STACKS_TO_SKIP`, and every log line
  then names the wrong caller. A message built before the level check turns every disabled call into a stack walk
  (`domains/logging/overview.md`).
- **The `api` harness.** Every ViewModel read or drive must cross onto the rendering thread through `renderHop`;
  skipping it can kill the process later, against other state. `respondApi` must stay `inline` with a reified type.
  Routes are a snapshot taken at `serve`, so a controller registered later serves nothing
  (`domains/api-harness/overview.md`).
- **Display.** A static libGDX call such as `Lwjgl3ApplicationConfiguration.getDisplayMode()` needs a windowing
  system with no `Application` running. CI has no display (`domains/headless/overview.md`).
- **Release.** A merge without a `version` rise releases nothing, with a green run; the `version` job in
  `.github/workflows/pr.yml` catches it only for the paths it lists. `gate` must keep `if: always()` and the trigger
  must keep no path filter, or a required check never reports. CI logic belongs in `.github/workflows/`, never in
  `.github/actions/`, which the bot can push. Dependabot's gradle entry must list `/buildSrc` as well as `/`
  (`domains/build-and-release/overview.md`).
- **Consumers are invisible from here.** Nothing in this repository calls much of its public API, so a search that
  finds no caller says nothing about whether a consumer has one, and a behavior change no test here covers can still
  break every consumer.

## Contracts

| Contract | Sides |
|---|---|
| Work is counted on submit, so a combined idle read is a fixpoint | `core/src/main/kotlin/com/runt9/kgdf/async/WorkSource.kt` and `WorkTracker.kt`, `AsyncWorkQueue.kt`, `CombinedWorkSource.kt`, `core/src/main/kotlin/com/runt9/kgdf/event/EventBus.kt`, `api/src/main/kotlin/com/runt9/kgdf/api/controller/ApiController.kt` (`settle`) |
| Every async context comes from `AsyncFactory`, so a test can substitute it | `core/src/main/kotlin/com/runt9/kgdf/async/AsyncFactory.kt` and `core/src/testFixtures/kotlin/com/runt9/kgdf/testsupport/TestAsyncFactory.kt`, and every class that creates a context (`EventBus`, `ServiceAsync`, `AssetConfig`, `AsyncWorkQueue`) |
| `save` fires the consumer's update event only when it writes | `core/src/main/kotlin/com/runt9/kgdf/service/GameStateService.kt` and every consumer's `GameState.equals`/`clone` and its update-event handlers |
| The settings keys `PlayerSettings` declares, and that old files still decode | `core/src/main/kotlin/com/runt9/kgdf/settings/PlayerSettings.kt` and `SettingsStore.kt`, `PlayerSettingsConfig.kt`, and every consumer's implementation |
| The randomizer's two persisted properties and what the counter means | `core/src/main/kotlin/com/runt9/kgdf/util/SeededRandomizer.kt` and `SingleFileSaveStateService.kt`'s CBOR configuration, `core/src/test/kotlin/com/runt9/kgdf/util/SeededRandomizerReloadTest.kt` |
| A screen's wire name is its `route`; `of` matches by instance and `forController` by exact class | `api/src/main/kotlin/com/runt9/kgdf/api/observe/ApiScreen.kt` and `ScreenApiController.kt`, `HarnessServer.kt`, `api/src/test/kotlin/com/runt9/kgdf/api/observe/ApiScreenTest.kt` |
| The published coordinates and the release trigger | `buildSrc/src/main/kotlin/kgdfw-common-plugin.gradle.kts` (`publishing`) and `.github/workflows/publish.yml`, `gradle.properties` |
| The `version` job's list of published paths | `.github/workflows/pr.yml` and the modules in `settings.gradle.kts` plus `buildSrc` |
| The `TEST-SUMMARY <task> <result> total= passed= failed= skipped=` line | the listener in `buildSrc/src/main/kotlin/kgdfw-common-plugin.gradle.kts` and `.claude/test-review-profile.md`, `.claude/development-profile.md` (section 6) |

## Living docs

| Doc | Must change when |
|---|---|
| `CLAUDE.md` | a mechanism in "Core mechanics", a knowledge base entry point, or the release and consumption steps in "Working here" change |
| `.claude/reference/README.md` | a knowledge base note is added, renamed or removed, or a local convention changes |
| each note under `.claude/reference/domains/` | the behavior it states changes in a file its `sources:` lists; the validator's `--stale-in` names them |
| `.claude/development-profile.md` | branching, building, testing, CI, review or release works differently |
| this profile | a contract appears, a silent failure is found, or something is ruled not to be a finding |
| `.claude/test-review-profile.md` and `.claude/agents/references/test-review-*.md` | a test module, the run command or the result line changes |
| KDoc on a public declaration | its behavior changes |

## Mirrors

| Copy | Source | Kept in step by |
|---|---|---|
| the one-line hazards in `CLAUDE.md`, "Core mechanics" | the knowledge base notes that state them | hand |
| the entry-point list in `CLAUDE.md`, "The knowledge base" | the entry-point table in `.claude/reference/README.md` | hand |
| a `# vX` comment beside a pinned action | the SHA it annotates | Dependabot |
| `TestAsyncFactory`'s KDoc saying `AsyncFactory` is the sole caller of `newSingleThreadAsyncContext` | the callers in `core/src/main/` | hand |

## Companions

- A change to published code (`core`, `api`, `buildSrc`, `gradle`, `build.gradle.kts`, `settings.gradle.kts`, not
  counting `core/src/test` or `api/src/test`) brings a `version` rise in `gradle.properties`. CI fails a pull request
  without it; a working-tree or range review should still say so.
- A new published module brings its path into the `version` job's list in `.github/workflows/pr.yml`.
- A new property on a `@Serializable` persisted type brings a default.
- A behavior change in a file a knowledge base note lists under `sources:` brings that note's update and a new
  `verified:` date.
- A new knowledge base note brings a row in `.claude/reference/README.md` and a line in `CLAUDE.md`'s entry points.

## Test-quality traps

The references are `.claude/agents/references/test-review-core.md` and `.claude/agents/references/test-review-api.md`.

- A test that asserts nothing happened, under a `StandardTestDispatcher` it never advanced. The work never ran, so
  the assertion holds whether the code is right or wrong.
- A spec that assigns `KgdfLog.sink` or `KgdfLog.minLevel`, or fills `ApiScreen.screens`, without restoring it, so a
  later spec's result depends on run order.
- A `GameConfig` built without `gameDataPath`, which writes into the real user's preferences directory.
- A reload test comparing counters rather than the draws that follow.

## Debris

- `println` or a log call left in for debugging.
- A focused or disabled test: an `f:` or `!` name prefix, `xtest`/`xcontext`, or `.config(enabled = false)`.
- `Thread.sleep` or a real-time wait in a test.
- Commented-out code.
- A TODO or FIXME added in passing, unrelated to the change.
- An absolute local path or a sibling-checkout path in code, a comment or a fixture.

## Public on purpose

These names are public by design and are not leaks: the `Runt9-Productions` organization and the
`kotlin-gamedev-framework` repository, the `com.runt9.kgdfw` group and the `com.runt9.kgdf` package, the
`Runt9 Productions` company default in `GameConfig`, and the `runt9-claude-bot[bot]` name and its noreply address.

Proposed by the lead session, not yet ruled by the owner: the commit identities already in this repository's history
are public too.

## Rulings that are not findings

Each was decided by the owner, on the date shown where one was recorded. Report one only when the diff brings
something the ruling did not account for.

- The `Injector` stays a process-global `object`. Refactoring it, or assuming the container can be reset between
  tests, has been proposed and rejected repeatedly: it would ease testing and make automatic wiring worse.
  (2026-08-15)
- Finding no callers for `ServiceAsync` or `updateAsync` is not grounds for deleting them: they exist for real-time
  consumers (`domains/event-and-state/overview.md`).
- `WorkSource.pending` is `Flow<Int>`, not `StateFlow<Int>` (`domains/event-and-state/overview.md`).
- The tests run in a job without the write token, and the `publish` job alone holds `packages: write` and
  `contents: write`. (2026-10-24)
- Dependabot pull requests skip the `version` job, and all gradle updates share one group. (2026-10-24)

## Other callers

None. No hook or CI job here runs a lens.

## Delivery

- **The report.** Write the complete report to `<review-dir>/review.md` and nowhere else. Subagent writes to files
  named `REPORT*`, `SUMMARY*`, `FINDINGS*` or `ANALYSIS*` are refused. A run that writes no report says why in its
  final message.
- **The run never posts.** It writes the files below; the session that started it posts them afterwards as the bot
  (`.claude/development-profile.md`, section 9).
- **Pull request targets** also write `<review-dir>/review-payload.json`, which is posted as written with
  `event: "COMMENT"` added:
  - `commit_id`: the 40-hex head SHA the diff was fetched at. Without it GitHub reviews the newest head.
  - `body`: the review summary. It keeps all three severity headings, and a finding placed inline appears there as a
    one-line pointer to its comment.
  - `comments[]`: each with `path`, `line`, `side: "RIGHT"` and `body`, carrying one whole finding, and no other
    field. `line` is the new-side line number.
- **Public text.** Every posted body is public: it names no consuming project, no machine path and no private
  repository.
- **Body cap.** Keep `body` under 60,000 characters, below GitHub's 65,536-character limit for a review. When the full
  body would pass that, shorten every finding in it to one line with its location, and say that the full text is in
  the inline comments and in `review.md`.
- **Fallback.** GitHub rejects the whole review when one comment does not land on the diff; `review.md` is then
  posted alone as the body, so it holds every finding in full, inline or not.

## Standing instructions

`code-health` and `simplification` judge the change against `CLAUDE.md`, the local conventions in
`.claude/reference/README.md`, and the style-guide skills `code-style`, `kotlin-style` and `comment-style`, loaded
with the Skill tool. Where a skill cannot be loaded, the report's coverage says so.
