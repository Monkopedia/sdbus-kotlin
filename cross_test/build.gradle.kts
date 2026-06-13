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
        val commonMain by getting {
            dependencies {
                implementation(project(":"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val jvmTest by getting {
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
