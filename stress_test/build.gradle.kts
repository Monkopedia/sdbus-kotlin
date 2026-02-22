import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()

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

val crossNativeTestBinary = rootProject.project(":cross_test").layout.buildDirectory.file(
    "bin/linuxX64/debugTest/test.kexe"
)
val reverseInteropEnabled = providers
    .systemProperty("kdbus.crossRuntimeInterop.reverse.enabled")
    .orElse(providers.gradleProperty("kdbus.crossRuntimeInterop.reverse.enabled"))
val stressRepeat = providers
    .systemProperty("kdbus.stress.repeat")
    .orElse(providers.gradleProperty("kdbus.stress.repeat"))
    .orElse("25")
val stressTimeoutMs = providers
    .systemProperty("kdbus.stress.timeout.ms")
    .orElse(providers.gradleProperty("kdbus.stress.timeout.ms"))
    .orElse("30000")
val stressCaseFilter = providers
    .systemProperty("kdbus.stress.caseFilter")
    .orElse(providers.gradleProperty("kdbus.stress.caseFilter"))
val stressTestPattern = providers
    .systemProperty("kdbus.stress.testPattern")
    .orElse(providers.gradleProperty("kdbus.stress.testPattern"))

val jvmTestTask = tasks.named<Test>("jvmTest")

tasks.named<Test>("jvmTest") {
    enabled = false
}

tasks.register<Test>("jvmInteropStressTest") {
    group = "verification"
    description = "Runs repeated JVM/native direct-bus interop stress tests."
    dependsOn(":cross_test:linkDebugTestLinuxX64", "jvmTestClasses")
    shouldRunAfter(jvmTestTask)
    testClassesDirs = jvmTestTask.get().testClassesDirs
    classpath = jvmTestTask.get().classpath
    systemProperty("kdbus.crossRuntimeInterop.enabled", "true")
    systemProperty("kdbus.nativeTestBinary", crossNativeTestBinary.get().asFile.absolutePath)
    systemProperty("kdbus.stress.repeat", stressRepeat.get())
    systemProperty("kdbus.stress.timeout.ms", stressTimeoutMs.get())
    reverseInteropEnabled.orNull?.let { value ->
        systemProperty("kdbus.crossRuntimeInterop.reverse.enabled", value)
    }
    stressCaseFilter.orNull?.let { value ->
        systemProperty("kdbus.stress.caseFilter", value)
    }
    filter {
        includeTestsMatching("com.monkopedia.sdbus.stress.CrossRuntimeInteropStressTest")
        stressTestPattern.orNull?.let(::includeTestsMatching)
    }
}
