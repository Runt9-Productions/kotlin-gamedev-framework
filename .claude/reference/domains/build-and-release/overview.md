---
title: Build and Release
type: note
permalink: build-and-release/overview
tags: [ build, publishing, release ]
verified: 2026-11-27
coverage: partial
sources:
  - gradle.properties
  - .github/workflows/publish.yml
  - .github/workflows/pr.yml
  - .github/scripts/requirements.txt
  - .github/workflows/dependency-submission.yml
  - .github/dependabot.yml
  - gradle/wrapper/gradle-wrapper.properties
  - buildSrc/src/main/kotlin/kgdfw-common-plugin.gradle.kts
  - core/build.gradle.kts
---

# Build and Release

> **Scope.** This note covers the publishing configuration in `kgdfw-common-plugin.gradle.kts`, `publish.yml` and `gradle.properties`, the PR workflow's checks, and Dependabot. It does **not** cover the version catalog's contents, the `useLocalKgdfw` composite-build path a consumer uses to iterate locally, or the settings/module structure that decides which projects apply the plugin. The 2026-11-27 pass re-read the four workflow and config files that changed since 2026-10-24 (`pr.yml`, `publish.yml`, `dependency-submission.yml`, `dependabot.yml`), `gradle-wrapper.properties` and the new `requirements.txt`. Claims resting on `gradle.properties`, `kgdfw-common-plugin.gradle.kts` and `core/build.gradle.kts`, unchanged since 2026-10-24, were carried forward without a re-read, as were the external reads dated inline.

Read this before opening any PR that changes published code, before changing anything under `publishing { }` or in the workflows, or when a consumer cannot resolve a version that appears to exist. The things most likely to be got wrong: **merging into `master` is the release, so the PR itself must bump `version` in `gradle.properties`**, and **a version showing up in the packages API does not mean the publish finished**.

## Observations

### Coordinates and what gets published

- [fact] The group is `com.runt9.kgdfw`, set in `gradle.properties:5`.
- [fact] The publication sets `artifactId = project.name` and takes `from(components["java"])`, so each module publishes as `com.runt9.kgdfw:<module name>` (`kgdfw-common-plugin.gradle.kts:93-98`).
- [fact] The repository is `https://maven.pkg.github.com/Runt9-Productions/kotlin-gamedev-framework`, declared once in the shared convention plugin rather than per module (`kgdfw-common-plugin.gradle.kts:77-91`).
- [fact] The same plugin applies `java-test-fixtures`, `withJavadocJar()` and `withSourcesJar()` (`kgdfw-common-plugin.gradle.kts:9,17-18`), so the `java` component a publication is built from carries the test-fixtures variant alongside the main jar.
- [fact] Dependencies resolve from `mavenCentral()` alone (`kgdfw-common-plugin.gradle.kts:25-27`). JitPack and oss.sonatype were dropped in 2.0.12 along with `com.github.raeleus.stripe:freetype`, the one dependency that needed JitPack; nothing used its classes, since `ParameterAvailableFreeTypeSkin` reads FreeType fonts from skin JSON on plain libGDX.

### Merging releases; the file is the version

- [fact] `.github/workflows/publish.yml` runs on every push to `master` and on any hand-pushed `*.*.*` tag (`publish.yml:7-9`), one run at a time (`concurrency: publish`, `publish.yml:12`).
- [fact] On a `master` push the `version` job reads `version` from `gradle.properties`. If `refs/tags/<version>` already exists on the remote, the run publishes nothing (`publish.yml:43-45`). Otherwise the `test` job runs the full build and its tests (`publish.yml:54-71`), and only if that succeeds does the `publish` job run `./gradlew -Pversion=<version> publish` and then push the tag (`publish.yml:74-76`, `98`, `105-109`). A failing test therefore publishes nothing and tags nothing, and tagging after the publish means a failed publish leaves the version untagged, so the next merge retries it.
- [decision] The tests run in a job of their own, without the write token: checkout leaves the job's token in `.git/config`, so a test step inside the `publish` job would run every test and test dependency with `packages: write` and `contents: write` in reach (`publish.yml:52-53`). [owner, kgdfw PR 28 review (D15), 2026-10-24]
- [invariant] **So every PR that changes published code bumps `version` in `gradle.properties`.** A merge without the bump finds its version already tagged and releases nothing, with a green run #silent-failure. The `version` job in `.github/workflows/pr.yml` fails such a PR (`pr.yml:63-98`): on a PR into `master` only, it diffs `core`, `api`, `buildSrc`, `gradle`, `build.gradle.kts` and `settings.gradle.kts` against the base (unit-test sources excluded, test fixtures included since they ship), and requires a version that differs from the base's and is not already tagged.
- [decision] PRs opened by `dependabot[bot]` skip that `version` job (`pr.yml:64`). Dependabot never bumps `gradle.properties`, so a dependency update merges without releasing and ships with the next release that does bump. [owner, kgdfw PR 28 review (D14), 2026-10-24]
- [fact] The tag pushed by the run uses the run's own `GITHUB_TOKEN`, and GitHub starts no workflow run for a push made with that token, so the auto-tag does not re-trigger the tag path.
- [invariant] A hand-pushed tag still publishes, but only if it equals the file's `version`; otherwise the run exits 1 with "Tag is X but gradle.properties says Y" (`publish.yml:37-41`).
- [decision] The file is kept honest on purpose: it is what someone reads to learn what version kgdfw is at, and a consumer pinning a number that was never released fails downstream as an unresolvable dependency with no hint of the cause.
- [fact] A release therefore runs: the PR bumps `version`; it merges; the workflow publishes and tags; the consumer then bumps its pin. The pin bump is the consumer's own change, described in its own KB, not here.
- [decision] The `publish` job alone grants `packages: write` (publish) and `contents: write` (the tag) and passes the run's own `GITHUB_TOKEN`, so the write-capable credential exists for one job and nothing write-capable sits in a developer's `gradle.properties` (`publish.yml:79-83`, `98-102`). The workflow default is `contents: read` (`publish.yml:14-15`). [owner, kgdfw PR 28 review (D15), 2026-10-24]
- [fact] The `publish` job runs `setup-gradle` with `cache-disabled: true` (`publish.yml:93-96`), so no cache entry written by another job is restored into the job holding the write token. setup-gradle otherwise restores `master`'s entries into every job; the input is documented at v6.4.0 as neither reading nor writing the cache (read 2026-10-24). The pin is v6.4.1 since kgdfw PR 38, and the documentation was not re-read at that version.
- [fact] A consequence of that decision: there is no supported way to publish from a developer machine.

### Pull request checks

- [fact] `.github/workflows/pr.yml` runs on every pull request, with no branch or path filter on the trigger (`pr.yml:5-6`). Its `gate` job needs `changes`, `test`, `version` and `guard`, runs under `if: always()`, and fails when any of them failed or was cancelled (`pr.yml:181-194`). `gate` is the one check branch protection is meant to require.
- [trap] Keep `if: always()` on `gate`, and keep path filters off the trigger. A job whose dependency failed is skipped, a skipped required check counts as passing, and a required check whose workflow never starts leaves the PR waiting on it forever #silent-failure. That is GitHub's documented behavior, not measured here. A job skipped by its own `if` (`test` on a docs-only PR, `version` on a PR not into `master` or opened by Dependabot) is a pass by design.
- [fact] The `changes` job skips `test` when every changed path ends in `.md` or sits under `.claude/` (`pr.yml:30-39`, `41-58`). `guard` and `version` do not depend on it.
- [fact] Both test jobs run plain `./gradlew build --console=plain` (`pr.yml:41-58`, `publish.yml:54-71`), with no step checking the test count. Gradle fails a test task that has test sources but discovers no tests (`failOnNoDiscoveredTests`, default true since Gradle 9.0), and the wrapper is 9.9.2 (`gradle-wrapper.properties:5`). Setting that property to false would bring back a green build that ran nothing #silent-failure.
- [trap] Keep CI logic in workflows under `.github/workflows/`, never in a composite action under `.github/actions/`. GitHub's "Choosing permissions for a GitHub App" ties the `workflows` permission, which the bot does not hold, to files in the `.github/workflows` directory, so a file under `.github/actions/` is one the bot can push, and a check living there could change without the owner. Read 2026-10-24; no bot push of a `.github/actions/` file has been tried.
- [fact] `guard` runs on every PR, docs-only ones included (`pr.yml:100-177`). Its first step fails when a file the PR adds or modifies under `.claude/`, or any `CLAUDE.md`, holds a zero-width, bidirectional, byte-order-mark or Unicode tag character anywhere in the file, not only on added lines, and searches a file holding a NUL byte as text rather than skipping it as binary (`pr.yml:114-136`). `.github/` is not checked.
- [fact] The rest of `guard` runs the knowledge base validator from the reference-kb plugin of `Runt9-Productions/runt9-claude-harness`, checked out into `.harness` at the commit `HARNESS_SHA` pins (`pr.yml:104-107`, `138-146`). Each of those steps carries `if: ${{ !cancelled() }}`, so a failed invisible-character step does not hide a validator failure.
- [fact] PyYAML 6.0.3 is installed into a venv with `--require-hashes --only-binary :all: --no-deps` from `.github/scripts/requirements.txt` (`pr.yml:147-155`), which carries two `--hash` lines its comment names as the cp312 and cp314 manylinux x86_64 wheels (`requirements.txt:1-7`). Which hash is which wheel was not checked against PyPI.
- [trap] **A runner image that changes its system Python turns `guard` red in the install, before the validator runs**, so the error is pip's hash check rather than anything about notes. Add the new wheel's hash to `requirements.txt` (`requirements.txt:3-4`).
- [fact] The format step runs the validator plain over `.claude/reference` (`pr.yml:156-162`). It exits 2 when the root is missing or holds no notes, so a knowledge base that moved or emptied fails it.
- [fact] The re-verify step runs it with `--stale-in HEAD^1..HEAD^2` and copies the output into the job summary (`pr.yml:164-177`). In GitHub's merge checkout `HEAD^1` is the base and `HEAD^2` the PR's head, so the range also holds the commits of any branch merged into the head that the base lacks, and a note those commits left stale fails the PR as if it had changed the sources itself. The rule is the reference-kb format's: a note fails when a commit in the range changes one of its `sources` and no commit from that one onwards edits the note leaving `verified` on or after the change's date, or carries a `Reverified: <note path>` trailer.
- [fact] On a PR opened by `dependabot[bot]` the re-verify step reports and cannot fail (`continue-on-error`, `pr.yml:170`). Dependabot edits files notes list as `sources` (the workflows, the wrapper) and cannot re-verify a note.
- [risk] `requirements.txt` sits outside `.github/workflows/`, so the bot can push it, as with `.github/actions/` above, and it decides what the validator imports as `yaml`. A changed pin can therefore weaken the knowledge base gate without the owner pushing anything. Inferred from the same permission documentation, not tried.
- [trap] Moving `HARNESS_SHA` can change what the validator accepts, so it goes in a PR of its own that fixes whatever notes the new validator rejects (`pr.yml:105-106`).

### Dependency updates

- [fact] `.github/workflows/dependency-submission.yml` submits the resolved Gradle dependency graph on every push to `master` (`dependency-submission.yml:7`), which is what lets Dependabot alerts see transitive dependencies.
- [invariant] It is two jobs, and must stay two. `generate` runs Gradle with a read-only token that checkout does not persist into `.git/config`, and uploads the graph as an artifact (`dependency-submission.yml:16-32`). `submit` holds `contents: write` and runs only the action's `download-and-submit`, with no checkout and no Gradle (`dependency-submission.yml:37-47`). Folded into one job, every plugin and dependency Gradle resolves runs with the write token in reach.
- [fact] `.github/dependabot.yml` puts the `gradle` and `github-actions` ecosystems in one weekly multi-ecosystem group, `weekly` (`dependabot.yml:4-7`), each joining it with `patterns: ["*"]` and a seven-day cooldown (`dependabot.yml:15-18`, `21-24`). It declares no registry, since everything resolves from Maven Central and the plugin portal.
- [fact] That group yields one PR a week across both ecosystems and both gradle directories: kgdfw PR 38 (`7032605`, "Bump the weekly group across 2 directories with 18 updates") changed both version catalogs and the action pins in one commit. kgdfw PR 37 set this up, replacing the gradle-only group of PR 28 (D14), which GitHub's changelog of 2026-02-24 said would open one PR per directory.
- [trap] **The gradle entry lists `/buildSrc` as well as `/`; keep both** (`dependabot.yml:10-14`). Dependabot reads `gradle/libs.versions.toml` only at a configured directory's own root, so from `/` alone `buildSrc/gradle/libs.versions.toml`, which holds the Kotlin, serialization, Kover and versions plugins, is never updated and nothing reports it #silent-failure. Read on 2026-10-24 in dependabot-core's `gradle/lib/dependabot/gradle/file_fetcher.rb` (`version_catalog_file` returns nothing unless the scanned directory is the root, and `buildSrc` is scanned as a nested build). With both listed, PR 38 updated `buildSrc/gradle/libs.versions.toml`; the failure with `/buildSrc` omitted has not been seen on a real run.
- [risk] libGDX, KTX and korlibs are never offered. `core/build.gradle.kts` builds their coordinates as strings (`apiGdx`, `apiKtx`, `apiKorlibs`) from catalog `[versions]` entries that no `[libraries]` row references, which Dependabot cannot parse. Inferred from how Dependabot parses catalogs, not observed. They move by hand, and KTX's release tracks a libGDX version, so a bump to one is a bump to both.
- [history] Until 2026-10-16 releasing was a hand-pushed tag, and twice an agent's PR left `gradle.properties` unbumped (2026-09-26 and kgdfw PR 23), because the bump read as part of "cutting a release" rather than of the feature PR. Auto-tag on merge plus the PR check replaced it.

### Credentials

- [fact] The repository's credentials read Gradle properties `gpr.user` / `gpr.key`, falling back to the `GITHUB_ACTOR` / `GITHUB_TOKEN` environment variables (`kgdfw-common-plugin.gradle.kts:85-88`).
- [trap] `gpr.key` must be a **classic** token. The comment at `kgdfw-common-plugin.gradle.kts:81-83` records that the Maven registry rejects fine-grained tokens with a 401, which reads as a wrong password rather than as an unsupported token type #silent-failure.
- [risk] Consuming a published version is reported to require a classic token with `read:packages` even though the package is public, with anonymous reads failing as the same misleading 401. Nothing in this repository establishes that — it is registry behavior observed downstream, recorded here because the failure mode is indistinguishable from a bad credential.
- [fact] In Actions the built-in `GITHUB_TOKEN` works for reading instead, but only once the consuming repository has been granted read access to the package (same comment, `kgdfw-common-plugin.gradle.kts:83`).

### Waiting on a publish

- [trap] **The packages API showing a version is not proof the publish finished.** The version row appears on the first file uploaded, so a build started at that moment can resolve a partial artifact: Gradle falls back to POM-derived variants and the symptom presents as missing test fixtures rather than as a race #silent-failure #measured (observed during the 2026-09-03 migration; not re-run in this pass).
- [fact] Wait on the workflow run rather than on the version appearing.
- [risk] Gradle caches the resolution miss, so a retry after the publish completes can keep failing until `--refresh-dependencies` is passed. Recorded from the same migration, not demonstrated here.

### Why it is not JitPack

- [history] Publishing moved to GitHub Packages on 2026-09-03; `74bdbd0` ("Test github package") is the commit that proved the path. JitPack's re-hosting corrupts Gradle Module Metadata — it strips the classifier from each variant's file reference while keeping that artifact's real size and sha256, so the test-fixtures variant pointed at the main jar while carrying the fixtures jar's checksum, and test fixtures could not be consumed at all.
- [history] That corruption was reported to jitpack.io twice (issues 4519 in 2021 and 8007 in 2026) and both were auto-closed with no maintainer reply, so it should not be expected to be fixed. The corrupted-metadata detail above was recorded at migration time and is not re-derivable from this repository, since the JitPack publishing configuration is gone.

## Relations

- see_also [[Running Without a Display]]
