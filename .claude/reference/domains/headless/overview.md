---
title: Running Without a Display
type: note
permalink: headless/overview
tags: [ settings, testing, lwjgl ]
verified: 2026-09-04
branch: master
coverage: partial
sources:
  - core/src/main/kotlin/com/runt9/kgdf/settings/PlayerSettings.kt
  - core/src/main/kotlin/com/runt9/kgdf/application/ApplicationConfiguration.kt
---

# Running Without a Display

> **Scope.** This note covers `PlayerSettings.defaultPlayerSettings()` and the two `getDisplayMode()` call sites in `core`. It does **not** cover asset loading, audio, fonts, or any other LibGDX subsystem that may equally demand a display — none of those were read, and their absence here says nothing about whether they are safe.

Read this before assuming a consumer's test harness, CI job, or headless tool can run without a windowing system. **"Creates no LibGDX `Application`" and "runs headless" are not the same claim**, and the gap between them is the whole subject of this note: a static LibGDX call can initialize GLFW with no `Application` anywhere in the process.

## Observations

### The static call

- [trap] `Lwjgl3ApplicationConfiguration.getDisplayMode()` is a **static** method that initializes GLFW. Calling it needs a windowing system even though no `Application` has been created and nothing has been rendered (`PlayerSettings.kt:36`) #silent-failure.
- [fact] `defaultPlayerSettings()` is where that call sits in the normal path, reading the primary display mode to seed `Resolution` (`PlayerSettings.kt:34-40`).
- [fact] There is **no call site for `defaultPlayerSettings()` inside this repository**. It exists for a consumer building its own `PlayerSettings` implementation, so the hazard is entirely one a consumer inherits.

### The fallback

- [fact] The call is wrapped in `try`/`catch (e: Exception)`. On failure it falls back to `HEADLESS_RESOLUTION` and logs at WARN with the exception attached (`PlayerSettings.kt:35-40`).
- [fact] `HEADLESS_RESOLUTION` is `Resolution(1280, 720, 60)`, documented in place as a floor rather than a guess because every mode the settings UI offers is at least that large (`PlayerSettings.kt:30-31`).
- [history] The fallback landed in `9c20f32` ("Headless settings"); `gradle.properties` declares `2.0.3` as its local default at that point. Before it, the GLFW initialization failure propagated out of `defaultPlayerSettings()` uncaught, so any consumer that built default settings without a display failed at that line. The exact exception type reported at the time was `GdxRuntimeException: Unable to initialize GLFW`; that string was not re-derived in this pass.
- [trap] The fallback catches `Exception`, so a GLFW failure surfacing as an `Error` rather than an `Exception` would still propagate. Not observed, but the catch is narrower than "anything that can go wrong here".

### The fallback does not make the framework headless-safe

- [trap] `ApplicationConfiguration.handleResolution` calls `getDisplayMode()` at `ApplicationConfiguration.kt:22` with **no fallback and no try/catch**. It runs only on the `fullscreen == true` branch while constructing the launcher configuration, so it is not on a headless path today — but a fix that exists in one call site is not a property of the framework.
- [invariant] Any new static LibGDX call reached before an `Application` exists carries the same requirement. The guard is per call site; there is nothing central enforcing it.

### Nothing tests this

- [fact] `core/src/test/kotlin/com/runt9/kgdf` holds test packages for `log`, `input`, `event` and `async` only. There is no test covering `settings`, and therefore none covering the fallback.
- [risk] The fallback can therefore regress silently: removing the `try`/`catch` breaks no test, and the failure appears only in a consumer's display-less environment. Recorded as a gap, not a fix.
- [trap] **Unsetting `DISPLAY` alone does not reproduce the failure on WSL**, which makes it easy to believe an unverified fix has been verified. Reproducing it needs `DISPLAY`, `WAYLAND_DISPLAY`, `XDG_RUNTIME_DIR`, `XDG_SESSION_TYPE`, `XDG_SESSION_CLASS` and `WSL2_GUI_APPS_ENABLED` stripped together; `XDG_RUNTIME_DIR` was the one still letting GLFW find a compositor #measured (measured during the fallback work; not re-run in this pass).

## Relations

- see_also [[Logging]]
- see_also [[Build and Release]]
