# Android AppFunctions Code Review Style Guide

This repository contains Android Kotlin and Jetpack Compose projects (`ChatApp/` and `agent/`).

## General
- Follow the [Android Kotlin Style Guide](https://developer.android.com/kotlin/style-guide); formatting is enforced by Spotless (`./gradlew spotlessCheck`).
- Keep changes focused and minimal; avoid unrelated refactoring.
- Ensure new public APIs and non-trivial logic include unit or instrumentation tests.

## Android & Compose
- Follow Unidirectional Data Flow (UDF): state flows down, events flow up.
- Avoid blocking calls on the main thread; use structured concurrency and appropriate `CoroutineDispatcher`s.
- Prefer `StateFlow` / immutable UI state models over mutable shared state.
- Check for resource leaks, lifecycle issues, and missing permission/nullability checks.

## GitHub Actions
- Pin third-party actions to full commit SHAs with a version comment (e.g. `@<sha> # vX`).
- Declare minimal `permissions:` on workflow jobs.
