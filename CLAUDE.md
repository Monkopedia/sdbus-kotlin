# CLAUDE.md

Guidance for agents working on sdbus-kotlin.

## What this is

A Kotlin Multiplatform D-Bus client (a port of sdbus-c++), targeting **jvm + linuxX64 + linuxArm64**. The native targets wrap `sd-bus` via cinterop; the JVM target is backed by dbus-java. It also ships a code generator (`:codegen`, XML → Kotlin BlueZ proxies/adaptors) and a Gradle plugin (`:plugin`, id `com.monkopedia.sdbus.plugin`).

Modules: root (the library), `:codegen`, `:plugin`, `:cross_test`, `:stress_test`, `samples/bluez-scan`.

## Building & verifying

- **JDK 17+ required** (Gradle 9). The system default is often Java 8 and will fail — use `JAVA_HOME=/usr/lib/jvm/java-17-openjdk` (or java-21). The JVM bytecode target is pinned to 17 so published artifacts are deterministic regardless of build JDK.
- Compile: `./gradlew compileKotlinJvm compileKotlinLinuxX64 compileKotlinLinuxArm64`
- Static gates (run in CI's `static-checks` job): `./gradlew ktlintCheck apiCheck`. `apiCheck` is binary-compatibility-validator; if you intentionally change the public API, regenerate with `./gradlew apiDump` and commit the `api/*.api` diff.
- Unit/integration tests (`jvmTest`, `linuxX64Test`) need a **D-Bus session bus** — run under `dbus-run-session -- ./gradlew …`.
- The `samples/bluez-scan` and blue-falcon-sdbus integration suites need a **real BlueZ adapter + BLE peripheral** (hardware); they can't run in CI.

## Releasing

1. Bump the version in `gradle.properties`, the README install snippets, and `samples/bluez-scan/build.gradle.kts`.
2. Create a GitHub release `vX.Y.Z` → the `publish.yaml` workflow publishes to Maven Central via vanniktech with `automaticRelease = true` (no manual Sonatype Portal step). All 5 coordinates (root, `-jvm`, `-linuxx64`, `-linuxarm64`, `-codegen`) deploy as one atomic deployment.
3. SNAPSHOT versions skip signing (for local `publishToMavenLocal` cross-repo testing); real releases are signed.

## Maintenance — keep these current when things change

- **README badges:** when the Kotlin version or build setup changes, update the badge block at the top of `README.md`.
  - The **Maven Central version** badge updates automatically (shields.io reads Central).
  - The **Kotlin version** badge is hardcoded — bump it manually when the Kotlin version changes.
  - The **Build** badge tracks `.github/workflows/arm-build-test.yaml`; keep the workflow path correct if CI is renamed.
  - Maven Central has no public *download* count, so there is no downloads badge (only the namespace owner can see download stats in the Central Portal).
- Generated BlueZ proxy fixtures and the BCV `api/*.api` dumps are checked in — regenerate (`apiDump`) and commit them when the public surface changes.
