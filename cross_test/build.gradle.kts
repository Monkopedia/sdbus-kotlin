import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    linuxX64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(project(":"))
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        getByName("jvmTest") {
            dependencies {
                // dbus-java here is an INDEPENDENT third-party D-Bus peer used by
                // CrossRuntimeInteropSmokeTest to prove sdbus-kotlin interoperates with another
                // JVM D-Bus stack over a real bus. It is NOT sdbus-kotlin's backend (that was
                // retired in epic #93 phase 6) and is not a published dependency; it is declared
                // by literal coordinate so it stays out of the shared version catalog.
                implementation("com.github.hypfvieh:dbus-java-core:5.2.0")
                runtimeOnly("com.github.hypfvieh:dbus-java-transport-junixsocket:5.2.0")
                // junixsocket on the test runtime classpath: the JVM fd cross-tests reflect onto
                // its native primitives (see DbusmockFdSupport.jvm.kt).
                implementation(libs.junixsocket.core)
            }
        }
    }
}

val crossNativeTestBinary = layout.buildDirectory.file("bin/linuxX64/debugTest/test.kexe")
val reverseInteropEnabled = providers
    .systemProperty("kdbus.crossRuntimeInterop.reverse.enabled")
    .orElse(providers.gradleProperty("kdbus.crossRuntimeInterop.reverse.enabled"))

// CrossRuntimeInteropSmokeTest only does anything under `jvmInteropTest` below, which is the task
// that selects it and hands it a linked native test binary. Keep it out of the plain `jvmTest`
// task (which `allTests` runs on every CI job) so it is reported exactly once, by the task that
// actually configures it — mirroring the gcSoak gate in the root build.
tasks.named<Test>("jvmTest") {
    filter.excludeTestsMatching("com.monkopedia.sdbus.integration.CrossRuntimeInteropSmokeTest")
}

// Kotlin/Native's test runner has no runtime-skip primitive, so `DbusmockHarness.skipTest`'s native
// actual can only print its reason: a Dbusmock* case that asserted nothing because python-dbusmock
// is missing is reported as a PASS, and `:cross_test:linuxX64Test` claimed 44 of them (#186). The
// JVM half of that was fixed in c5f874c (#182) with `org.junit.Assume`; native has no equivalent, so
// the only honest lever left is to not run the suites at all — absent beats a phantom pass.
//
// The decision follows c5f874c rather than adding a second convention:
//  * `DBUSMOCK_REQUIRED` set — the `full-tests-x64` job, which apt-installs python3-dbusmock on
//    purpose — the suites always run, and the harness itself fails loudly when it cannot start a
//    peer. So CI can never lose this coverage to the exclusion below.
//  * otherwise the harness is probed — a session bus plus an importable `dbusmock` module under
//    `DBUSMOCK_PYTHON` / `python3` — and the suites are excluded only when it cannot run, so a
//    contributor who *has* dbusmock still runs them with no configuration.
//
// Two limits, stated because both fail *open* (back to the phantom passes, not to something worse):
//  * The probe is weaker than the launch. `launchDbusmock` forks `python3 -m dbusmock --session
//    [-t <template>] …` and gives it a liveness window; this only checks the module imports. On a
//    box where `dbusmock` imports but a peer cannot actually start — an older dbusmock missing a
//    template like `bluez5`, a bus-name claim failure — the suites still run and native still
//    reports passes that asserted nothing.
//  * If the class names below drift, the exclusions silently stop matching. Gradle's filter API
//    offers no "this exclusion matched nothing" signal to guard that with.
val dbusmockRequired = providers.environmentVariable("DBUSMOCK_REQUIRED").isPresent
val dbusmockPython = providers.environmentVariable("DBUSMOCK_PYTHON").getOrElse("python3")
val sessionBusPresent = providers.environmentVariable("DBUS_SESSION_BUS_ADDRESS").isPresent

fun dbusmockCanRun(): Boolean {
    if (dbusmockRequired) return true
    if (!sessionBusPresent) return false
    return runCatching {
        ProcessBuilder(dbusmockPython, "-c", "import dbusmock")
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
            .waitFor() == 0
    }.getOrDefault(false)
}

tasks.named<AbstractTestTask>("linuxX64Test") {
    // NativeInteropPeerTest's cases are the *peer half* of a two-process case: each one returns
    // immediately unless KDBUS_NATIVE_INTEROP_ROLE names its role, which only the JVM side sets when
    // it spawns this binary (see CrossRuntimeInteropSmokeTest). `linuxX64Test` never sets it, so
    // every one of them runs to completion having asserted nothing — and, per #186, native reports
    // that as a pass. They are covered where they are actually driven: `jvmInteropTest`, which now
    // fails unless the spawned peer really ran its case and reported it passing (#183).
    filter.excludeTestsMatching("com.monkopedia.sdbus.integration.NativeInteropPeerTest")
    if (!dbusmockCanRun()) {
        logger.lifecycle(
            "cross_test: excluding the Dbusmock* suites from linuxX64Test — '$dbusmockPython -m " +
                "dbusmock' cannot run here. Kotlin/Native cannot report these as skipped (#186), " +
                "so running them would report passes that asserted nothing. Set " +
                "DBUSMOCK_REQUIRED=1 to run them anyway and fail loudly instead."
        )
        filter.excludeTestsMatching("com.monkopedia.sdbus.integration.Dbusmock*")
    }
}

tasks.register<Test>("jvmInteropTest") {
    group = "verification"
    description = "Runs JVM<->native direct-bus interop smoke tests in cross_test."
    dependsOn("linkDebugTestLinuxX64", "jvmTestClasses")
    val jvmTest = tasks.named<Test>("jvmTest")
    shouldRunAfter(jvmTest)
    testClassesDirs = jvmTest.get().testClassesDirs
    classpath = jvmTest.get().classpath
    systemProperty("kdbus.crossRuntimeInterop.enabled", "true")
    reverseInteropEnabled.orNull?.let { value ->
        systemProperty("kdbus.crossRuntimeInterop.reverse.enabled", value)
    }
    systemProperty("kdbus.nativeTestBinary", crossNativeTestBinary.get().asFile.absolutePath)
    filter {
        includeTestsMatching("com.monkopedia.sdbus.integration.CrossRuntimeInteropSmokeTest")
    }
}

val systemdVersion = "257.2-2"
val localSystemdLibDir = rootProject
    .file("libs/x86_64/$systemdVersion/lib")
    .takeIf { it.exists() }
    ?.absolutePath

tasks.withType<KotlinNativeLink>().configureEach {
    if (name.contains("LinuxX64", ignoreCase = true) && localSystemdLibDir != null) {
        toolOptions {
            freeCompilerArgs.addAll(
                listOf(
                    "-linker-option",
                    "-L$localSystemdLibDir",
                    "-linker-option",
                    "-Wl,-rpath,$localSystemdLibDir"
                )
            )
        }
    }
}
