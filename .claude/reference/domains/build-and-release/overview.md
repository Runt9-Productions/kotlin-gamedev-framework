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
  - buildSrc/src/main/kotlin/kgdfw-common-plugin.gradle.kts
---

# Build and Release

> **Scope.** This note covers the publishing configuration in `kgdfw-common-plugin.gradle.kts`, `publish.yml` and `gradle.properties`. It does **not** cover the version catalog, the `useLocalKgdfw` composite-build path a consumer uses to iterate locally, or the settings/module structure that decides which projects apply the plugin.

Read this before cutting a release, before changing anything under `publishing { }`, or when a consumer cannot resolve a version that appears to exist. The two things most likely to be got wrong: **the tag is the version, not `gradle.properties`**, and **a version showing up in the packages API does not mean the publish finished**.

## Observations

### Coordinates and what gets published

- [fact] The group is `com.runt9.kgdfw`, set in `gradle.properties:5`.
- [fact] The publication sets `artifactId = project.name` and takes `from(components["java"])`, so each module publishes as `com.runt9.kgdfw:<module name>` (`kgdfw-common-plugin.gradle.kts:95-100`).
- [fact] The repository is `https://maven.pkg.github.com/Runt9-Productions/kotlin-gamedev-framework`, declared once in the shared convention plugin rather than per module (`kgdfw-common-plugin.gradle.kts:79-93`).
- [fact] The same plugin applies `java-test-fixtures`, `withJavadocJar()` and `withSourcesJar()` (`kgdfw-common-plugin.gradle.kts:9,17-18`), so the `java` component a publication is built from carries the test-fixtures variant alongside the main jar.
- [trap] `repositories { maven("https://jitpack.io") }` at `kgdfw-common-plugin.gradle.kts:28` is a **dependency-resolution** repository, not a publish target. Publishing moved off JitPack; seeing that line is not evidence it did not.

### The tag is the version

- [fact] `.github/workflows/publish.yml` triggers on a push of any tag matching `*.*.*` and runs `./gradlew -Pversion="${{ github.ref_name }}" publish`.
- [trap] `version` in `gradle.properties:6` is only the local default. `-Pversion` overrides it in the workflow, so **editing `gradle.properties` alone publishes nothing and changes no published coordinate** — the released version is whatever the tag says.
- [decision] The workflow grants `permissions: packages: write` and passes the run's own `GITHUB_TOKEN`. The stated intent is that the write-capable credential exists for one run and nothing write-capable sits in a developer's `gradle.properties` (`publish.yml:13-17,30-33`).
- [fact] A consequence of that decision: there is no supported way to publish from a developer machine. Releasing is a tag push.

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
