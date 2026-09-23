---
title: Logging
type: note
permalink: logging/overview
tags: [ logging, testing, settings ]
verified: 2026-08-14
branch: testability-and-testing-refactor
coverage: partial
sources:
  - core/src/main/kotlin/com/runt9/kgdf/log/Logger.kt
  - core/src/main/kotlin/com/runt9/kgdf/log/LogLevel.kt
  - core/src/main/kotlin/com/runt9/kgdf/log/LogSink.kt
  - core/src/main/kotlin/com/runt9/kgdf/log/KgdfLog.kt
  - core/src/main/kotlin/com/runt9/kgdf/log/KotlinLoggingLogger.kt
  - core/src/main/kotlin/com/runt9/kgdf/application/ApplicationInitializer.kt
  - core/src/main/kotlin/com/runt9/kgdf/settings/PlayerSettings.kt
  - core/src/testFixtures/kotlin/com/runt9/kgdf/testsupport/CapturingSink.kt
---

# Logging

> **Scope.** This note covers the `com.runt9.kgdf.log` package, how a level reaches it from player settings, and how tests capture output. It does **not** cover SLF4J/logback backend configuration, log file rotation, or `teeStderrToFile`'s interaction with a running application.

Read this before adding a log call in a hot path, before changing anything in `com.runt9.kgdf.log`, or before writing a test that asserts on log output.

Logging does **not** go through LibGDX. `Logger` is kgdfw's own, writing to a swappable [LogSink]; `Gdx.app` is untouched by it.

## Observations

### Levels and configuration

- [fact] `LogLevel` is `ERROR, WARN, INFO, DEBUG, TRACE`, declared most severe first, and `KgdfLog.isEnabled` emits when `level.ordinal <= minLevel.ordinal`.
- [fact] There is no `OFF`. Errors always log, deliberately.
- [trap] `LogLevel.gdxLevel` maps onto LibGDX's four levels and is lossy in both directions: LibGDX has no WARN, so WARN maps to `LOG_ERROR`, and TRACE maps to `LOG_DEBUG` because LibGDX has nothing more verbose. It exists only to keep LibGDX's own internal logging in step and is not used by `Logger`.
- [fact] `PlayerSettings.minLogLevel` is the settings key. `ApplicationInitializer.initialize()` sets both `KgdfLog.minLevel` and `Gdx.app.logLevel` from it, because LibGDX's internal logging still routes through `Gdx.app`.
- [invariant] An implementation of `PlayerSettings` must give `minLogLevel` a default. Without one, kotlinx.serialization throws `MissingFieldException` for any settings file written before the key existed, and `SettingsStore.load()` has no try/catch and runs during startup #silent-failure.
- [history] The key was renamed from an `Int` `logLevel` rather than having its type changed in place. `ignoreUnknownKeys` drops the old key silently, where a type change on an existing key fails to deserialize every settings file in existence.

### Caller detection

- [fact] `Logger.buildMessage` renders `[ timestamp | thread | LoggerName.callingMethod ]: message`, and `kgdfLogger()` names the returned logger after the class that called it.
- [fact] Both resolve the caller with `StackWalker` (`RETAIN_CLASS_REFERENCE`), taking the first frame whose declaring class is not in `SELF_STACKS_TO_SKIP`.
- [trap] `SELF_STACKS_TO_SKIP` holds class *name strings*, one of which is the file class `LoggerKt`. **Renaming `Logger.kt` changes that class name**, and the result is not an error: every log line keeps rendering and silently names the wrong method #silent-failure.
- [invariant] `callerFrame`, `walker` and the skip set are file-private rather than members of `Logger` because the top-level `kgdfLogger()` calls `callerFrame()`, and a class-private member is not visible to a top-level function in the same file. Moving them into a companion would compile but add `Logger$Companion` as a third frame to skip.
- [fact] `LoggerTest` asserts the caller's method name and class name exactly, so it fails rather than silently mis-naming if the frame depth or skip set breaks.

### Performance

- [invariant] `Logger.log` checks the level **before** invoking the caller's message lambda and before `buildMessage`. Both the string build and the stack walk are skipped entirely when the level is disabled, which is what makes a `debug` call inside a per-frame loop free #order-dependent.
- [risk] Any refactor that formats the message before the level check turns every disabled log call into a stack walk. That cost is invisible in a test suite and appears as frame-time noise in a running game.

### Sinks and testing

- [fact] `KgdfLog.sink` defaults to `KotlinLoggingSink`, which forwards to kotlin-logging and from there to whatever SLF4J backend is on the classpath.
- [fact] `KotlinLoggingLogger` is a separate LibGDX `ApplicationLogger`, installed on `Gdx.app` so LibGDX's own internal logging reaches the same backend. It is not part of `Logger`'s path.
- [trap] `KgdfLog.sink` and `KgdfLog.minLevel` are process-wide mutable state. A test that assigns either without restoring it steals every later test's output for the rest of the JVM #silent-failure.
- [fact] The test-fixtures variant ships `CapturingSink` and `capturingLogs { }`; the latter swaps sink and level, runs a block, and restores both in a `finally`. Prefer it to assigning `KgdfLog.sink` directly.
- [fact] `CapturingSink` synchronizes its list because kgdfw logs from several named threads, so a test asserting on Event-Thread output is the normal case.

### Consequences for consumers

- [fact] Logging needs no live `Gdx.app`. Since it was previously the only thing in kgdfw that did, a consuming project's test harness can generally drop its headless-backend dependency and its `HeadlessApplication` bootstrap entirely — verify against that project's own tests rather than assuming, since a consumer may need one for something else.
- [fact] `ktx-log` is not a dependency. `Logger` was previously a subclass of `ktx.log.Logger`, whose `invoke` reads `Gdx.app.getLogLevel()` and calls `Gdx.app.log(...)`, and which is `final` so the coupling could not be overridden out.

## Relations

- see_also [[Events, Async and State]]
- see_also [[Running Without a Display]]
