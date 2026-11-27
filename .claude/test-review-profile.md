# Test review profile

Facts about this repository's tests that `/test-review` and its `test-review:test-reviewer` agent need.

## Default branch

`master`. With no paths given, the skill reviews test files changed against the merge base with it.

## Test kinds

Both kinds are Kotest specs run through Gradle. Run each module's task in its own invocation with `--rerun`: in a
multi-task invocation `--rerun` forces only the last task, and the others print a cached `TEST-SUMMARY` line that
reads exactly like a fresh one.

| Kind | Files | Helpers | Run | Reading the result |
|---|---|---|---|---|
| core | `core/src/test/**/*.kt` | `core/src/testFixtures/**/*.kt` | `./gradlew :core:test --tests '<spec FQCN>' --console=plain --rerun`, or one test with `--tests '<spec FQCN>.<test name>'`; the whole module without `--tests` | one `TEST-SUMMARY :core:test <SUCCESS or FAILURE> total= passed= failed= skipped=` line, then `BUILD SUCCESSFUL` or `BUILD FAILED`; failures in `core/build/test-results/test/TEST-<spec FQCN>.xml` |
| api | `api/src/test/**/*.kt` | `core/src/testFixtures/**/*.kt` | `./gradlew :api:test --tests '<spec FQCN>' --console=plain --rerun`, or one test with `--tests '<spec FQCN>.<test name>'`; the whole module without `--tests` | the same line for `:api:test`; failures in `api/build/test-results/test/` |

Reading a run, measured on this repository:

- **A test-name filter that matches nothing still passes.** With the spec matched and the test name wrong, the line
  reads `SUCCESS total=2 passed=0 failed=0 skipped=2` and the build succeeds. A one-test run is valid only when
  `passed` plus `failed` is 1.
- A spec filter that matches no class fails the build with `No tests found for given includes`, after a
  `TEST-SUMMARY ... total=0` line.
- The whole suite on this branch: `:core:test` total=101, `:api:test` total=5.
- Parse the result XML with an XML parser. A passing case is a self-closing tag, so a regex over
  `<testcase ...>(.*?)</testcase>` runs into the next case and puts a failure on the wrong test.

Production code is every file that is neither a test nor a helper listed above.

## Checks on every scoped file

- **The spec ran.** Kotest finds a spec by its class at run time, so confirm the run produced
  `TEST-<spec FQCN>.xml` and that its test count matches the tests the file declares.
- **CI runs it.** `.github/workflows/pr.yml` runs `./gradlew build`, which runs every spec under `src/test`, except
  on a pull request whose every changed path ends in `.md` or sits under `.claude/`.
- **It needs no display.** CI runs on `ubuntu-latest` with none. A test that reaches a static libGDX call needing a
  windowing system (`.claude/reference/domains/headless/overview.md`) passes on a desktop and fails there.
- **It names no consumer.** The repository is public: a test, fixture or comment never names a project that
  consumes kgdfw, its classes or its tickets, and its data is plainly synthetic.
- **A helper is published code.** `core/src/testFixtures` ships to consumers in the published `core` artifact, so a
  fix there is a change to published code and needs a `version` rise in `gradle.properties`. Report a needed helper
  fix rather than making it.

## Reference files

| Kind | Reference |
|---|---|
| core | `.claude/agents/references/test-review-core.md` |
| api | `.claude/agents/references/test-review-api.md` |

## Suite policy

Criterion 4 judges each test against this:

- **Boundaries.** A test drives a class through its public API the way a consumer would and asserts on what comes
  back out or what reaches a handler. Async work goes through `TestAsyncFactory` and a `TestCoroutineScheduler`,
  never a real thread or a sleep.
- **One test per decision.** Each decision the code makes gets one test; a second test of the same decision is
  redundant.
- **Characterization tests of a documented trap are kept.** A test pinning a hazard the knowledge base records (the
  double registration in `EventBusTest`, the exact-class split in `ApiScreenTest`) is never deleted as redundant,
  and one that breaks because the trap was fixed is updated in the same change.
- **Controls are part of the test.** A test asserting that nothing happened carries a positive control showing the
  same setup does fire; a reload test carries a control one step off that must differ. Neither is redundant.
- **Fixtures.** Test-only types live in the spec file, named for the test (`CounterState`, `Recorder`), and a test
  writes files only under a temporary directory it deletes.
