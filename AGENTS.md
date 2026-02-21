# Repository Guidelines

## Project Structure & Module Organization
This repository is a Kotlin Multiplatform project focused on native D-Bus support.

- `src/commonMain/kotlin`: shared API and serialization logic.
- `src/nativeMain/kotlin` + `src/nativeInterop/cinterop`: Linux native implementation and C interop.
- `src/nativeTest/kotlin/{unit,integration,mocks}`: native tests and test fixtures.
- `codegen/`: JVM CLI/library for XML-to-Kotlin code generation.
- `plugin/`: Gradle plugin (`com.monkopedia.sdbus.plugin`) built on top of `:codegen`.
- `compile_test/`: compile-time integration module for dependency wiring.
- `samples/bluez-scan/`: example consumer project.

## Build, Test, and Development Commands
Use the Gradle wrapper from repo root:

- `./gradlew build`: full build across modules.
- `./gradlew test`: run JVM/native tests configured in the project.
- `./gradlew ktlintCheck`: run Kotlin style checks.
- `./gradlew licenseCheck`: validate license headers and required checks.
- `./gradlew dokkaHtml`: generate docs into `build/dokka`.
- `./gradlew codegen:fatJar`: build the standalone codegen JAR used in releases.

CI release/docs workflows run `gradle publish codegen:fatJar` and `gradle dokkaHtml` on Ubuntu.

## Coding Style & Naming Conventions
- Follow `.editorconfig`: 4-space indentation, LF endings, max line length 100.
- Kotlin style is enforced with `ktlint` (`android_studio` profile).
- Use `UpperCamelCase` for classes, `lowerCamelCase` for functions/properties, and clear package names under `com.monkopedia.sdbus`.
- Keep public API changes intentional; this repo uses Kotlin binary compatibility validation (`apiValidation`).

## Testing Guidelines
- Place tests alongside module scope (`src/nativeTest`, `codegen/src/test/kotlin`, `plugin` tests).
- Prefer descriptive test names that reflect behavior, e.g. `createsProxyFromXml`.
- Run `./gradlew test` before PRs; include integration coverage when touching transport/interop paths.
- If a native test flake appears, stop feature work immediately and investigate first (reproduce, isolate, and identify the introducing change) before continuing.
- TODO: add deterministic coverage for native `createCleaner` auto-release paths (without explicit `release()`), likely via a dedicated native test harness that can force/synchronize cleanup and assert expected unref/close side effects.

## Commit & Pull Request Guidelines
- Keep commit messages short, imperative, and scoped (history examples: `Update dependencies`, `Bump patch`, `Minor cleanup`).
- PRs should include:
  - purpose and module(s) changed,
  - linked issue/release context,
  - test evidence (`./gradlew test ./gradlew ktlintCheck` results),
  - API impact notes for public-facing changes.
