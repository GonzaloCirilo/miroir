# Miroir

This is a Kotlin Multiplatform project targeting Windows, macOS, and Linux with Kotlin Native support.

## Current Status

Currently working on USB connection support for Windows, with macOS and Linux support planned.

## Project Structure

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./composeApp/src/commonMain/kotlin) is for code that's common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use platform-specific APIs, place them in the corresponding folder:
      - [nativeMain](./composeApp/src/nativeMain/kotlin) for Kotlin Native targets
      - Platform-specific folders for Windows, macOS, and Linux implementations

## Features

- Kotlin Native support for cross-platform desktop development
- USB device detection and connection (Windows in progress, macOS and Linux planned)

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…