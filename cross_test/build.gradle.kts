import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("multiplatform")
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
                implementation(libs.dbus.java.core)
                runtimeOnly(libs.dbus.java.transport.junixsocket)
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
