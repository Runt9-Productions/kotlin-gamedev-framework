---
title: Build and Release
type: note
permalink: build-and-release/overview
tags: [ build, publishing, release ]
verified: 2026-09-04
branch: master
coverage: partial
sources:
  - gradle.properties
  - .github/workflows/publish.yml
  - .github/workflows/version-check.yml
  - buildSrc/src/main/kotlin/kgdfw-common-plugin.gradle.kts
---

# Build and Release

> **Scope.** This note covers the publishing configuration in `kgdfw-common-plugin.gradle.kts`, `publish.yml` and `gradle.properties`. It does **not** cover the version catalog, the `useLocalKgdfw` composite-build path a consumer uses to iterate locally, or the settings/module structure that decides which projects apply the plugin.

Read this before opening any PR that changes published code, before changing anything under `publishing { }` or in the workflows, or when a consumer cannot resolve a version that appears to exist. The things most likely to be got wrong: **merging into `master` is the release, so the PR itself must bump `version` in `gradle.properties`**, and **a version showing up in the packages API does not mean the publish finished**.

**Partial re-verify 2026-10-16 (branch `claude/auto-tag-release`):** `publish.yml` was rewritten to publish and tag on merge, and `version-check.yml` added. Only the "Merging releases" section and its citations were rewritten from them; everything else is carried forward from the 2026-09-04 pass, which `verified:` still refers to.

## Observations

### Coordinates and what gets published

- [fact] The group is `com.runt9.kgdfw`, set in `gradle.properties:5`.
- [fact] The publication sets `artifactId = project.name` and takes `from(components["java"])`, so each module publishes as `com.runt9.kgdfw:<module name>` (`kgdfw-common-plugin.gradle.kts:95-100`).
- [fact] The repository is `https://maven.pkg.github.com/Runt9-Productions/kotlin-gamedev-framework`, declared once in the shared convention plugin rather than per module (`kgdfw-common-plugin.gradle.kts:79-93`).
- [fact] The same plugin applies `java-test-fixtures`, `withJavadocJar()` and `withSourcesJar()` (`kgdfw-common-plugin.gradle.kts:9,17-18`), so the `java` component a publication is built from carries the test-fixtures variant alongside the main jar.
- [trap] `repositories { maven("https://jitpack.io") }` at `kgdfw-common-plugin.gradle.kts:28` is a **dependency-resolution** repository, not a publish target. Publishing moved off JitPack; seeing that line is not evidence it did not.

### Merging releases; the file is the version
### Merging releases; the file is the version

- [fact] `.github/workflows/publish.yml` runs on every push to `master` and on any hand-pushed `*.*.*` tag (`publish.yml:7-9`), one run at a time (`concurrency: publish`, `publish.yml:12`).
- [fact] On a `master` push it reads `version` from `gradle.properties`. If `refs/tags/<version>` already exists on the remote, the run publishes nothing (`publish.yml:43-45`); otherwise it runs `./gradlew -Pversion=<version> publish` and only then pushes the tag (`publish.yml:61`, `69-73`). Tagging after the publish means a failed publish leaves the version untagged, so the next merge retries it.
- [invariant] **So every PR that changes published code bumps `version` in `gradle.properties`.** A merge without the bump finds its version already tagged and releases nothing, with a green run #silent-failure. `.github/workflows/version-check.yml` fails such a PR: it diffs `core`, `api`, `buildSrc`, `gradle`, `build.gradle.kts` and `settings.gradle.kts` against the base (unit-test sources excluded, test fixtures included since they ship), and requires a version that differs from the base's and is not already tagged.
- [fact] The tag pushed by the run uses the run's own `GITHUB_TOKEN`, and GitHub starts no workflow run for a push made with that token, so the auto-tag does not re-trigger the tag path.
- [invariant] A hand-pushed tag still publishes, but only if it equals the file's `version`; otherwise the run exits 1 with "Tag is X but gradle.properties says Y" (`publish.yml:37-41`).
- [decision] The file is kept honest on purpose: it is what someone reads to learn what version kgdfw is at, and a consumer pinning a number that was never released fails downstream as an unresolvable dependency with no hint of the cause.
- [fact] A release therefore runs: the PR bumps `version`; it merges; the workflow publishes and tags; the consumer then bumps its pin. The pin bump is the consumer's own change, described in its own KB, not here.
- [decision] The workflow grants `packages: write` (publish) and `contents: write` (the tag) and passes the run's own `GITHUB_TOKEN`, so the write-capable credential exists for one run and nothing write-capable sits in a developer's `gradle.properties` (`publish.yml:18-22`, `62-66`).
- [fact] A consequence of that decision: there is no supported way to publish from a developer machine.
- [history] Until 2026-10-16 releasing was a hand-pushed tag, and twice an agent's PR left `gradle.properties` unbumped (2026-09-26 and kgdfw PR 23), because the bump read as part of "cutting a release" rather than of the feature PR. Auto-tag on merge plus the PR check replaced it.

### Credentials

- [fact] The repository's credentials read Gradle properties `gpr.user` / `gpr.key`, falling back to the `GITHUB_ACTOR` / `GITHUB_TOKEN` environment variables (`kgdfw-common-plugin.gradle.kts:87-90`).
- [trap] `gpr.key` must be a **classic** token. The comment at `kgdfw-common-plugin.gradle.kts:83-85` records that the Maven registry rejects fine-grained tokens with a 401, which reads as a wrong password rather than as an unsupported token type #silent-failure.
- [risk] Consuming a published version is reported to require a classic token with `read:packages` even though the package is public, with anonymous reads failing as the same misleading 401. Nothing in this repository establishes that — it is registry behavior observed downstream, recorded here because the failure mode is indistinguishable from a bad credential.
- [fact] In Actions the built-in `GITHUB_TOKEN` works for reading instead, but only once the consuming repository has been granted read access to the package (same comment, `kgdfw-common-plugin.gradle.kts:85`).

### Waiting on a publish

- [trap] **The packages API showing a version is not proof the publish finished.** The version row appears on the first file uploaded, so a build started at that moment can resolve a partial artifact: Gradle falls back to POM-derived variants and the symptom presents as missing test fixtures rather than as a race #silent-failure #measured (observed during the 2026-09-03 migration; not re-run in this pass).
- [fact] Wait on the workflow run rather than on the version appearing.
- [risk] Gradle caches the resolution miss, so a retry after the publish completes can keep failing until `--refresh-dependencies` is passed. Recorded from the same migration, not demonstrated here.

### Why it is not JitPack

- [history] Publishing moved to GitHub Packages on 2026-09-03; `74bdbd0` ("Test github package") is the commit that proved the path. JitPack's re-hosting corrupts Gradle Module Metadata — it strips the classifier from each variant's file reference while keeping that artifact's real size and sha256, so the test-fixtures variant pointed at the main jar while carrying the fixtures jar's checksum, and test fixtures could not be consumed at all.
- [history] That corruption was reported to jitpack.io twice (issues 4519 in 2021 and 8007 in 2026) and both were auto-closed with no maintainer reply, so it should not be expected to be fixed. The corrupted-metadata detail above was recorded at migration time and is not re-derivable from this repository, since the JitPack publishing configuration is gone.

## Relations

- see_also [[Running Without a Display]]
