@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import kotlinx.validation.ExperimentalBCVApi
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.bcv)
    alias(libs.plugins.ktlint)

    `maven-publish`
}

group = "com.monkopedia"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

// == BCV setup ==
apiValidation {
    ignoredProjects.addAll(listOf("compile_test"))
    @OptIn(ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}

kotlin {
    val systemdVersion = "256.2-1"
    linuxX64 {
        binaries {
            sharedLib { }
        }
        compilations.getByName("main") {
            cinterops {
                create("sdbus-x86_64-$systemdVersion")
            }
        }
        compilerOptions {
            freeCompilerArgs.set(
                listOf(
                    "-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
                    "-Xexpect-actual-classes"
                )
            )
        }
    }
    // Not working quite yet.
//    linuxArm64 {
//        binaries {
//            sharedLib { }
//        }
//        compilations.getByName("main") {
//            cinterops {
//                create("sdbus-aarch64-$systemdVersion")
//            }
//        }
//        compilerOptions {
//            freeCompilerArgs.set(
//                listOf(
//                    "-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
//                    "-Xexpect-actual-classes"
//                )
//            )
//        }
//    }
    applyDefaultHierarchyTemplate()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines)
                implementation(libs.kotlinx.serialization)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.atomicfu)
            }
        }
        val nativeMain by getting {
            dependencies {
                implementation(libs.clikt)
                implementation(libs.kotlinx.coroutines)
                implementation(libs.kotlinx.serialization)
                implementation(libs.kotlinx.datetime)
                implementation(kotlin("stdlib"))
            }
        }
        val nativeTest by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

ktlint {
    this.android.set(true)
}

val overrides = mapOf(
    "linker.linux_x64" to "/usr/bin/ld.gold",
    "linker.linux_x64-linux_arm64" to "/usr/bin/aarch64-linux-gnu-ld.gold"
).entries.map { "-Xoverride-konan-properties=${it.key}=${it.value}" }

afterEvaluate {
    tasks.all {
        (this as? KotlinNativeLink)?.kotlinOptions {
            freeCompilerArgs += overrides
        }
        (this as? KotlinNativeCompile)?.kotlinOptions {
            freeCompilerArgs += overrides
        }
    }
    val link = tasks.getByName("linkDebugTestLinuxX64")
    (link as KotlinNativeLink).apply {
        kotlinOptions {
            freeCompilerArgs += overrides
            freeCompilerArgs += listOf("-linker-options", "-l systemd -l c")
        }
    }
    println("${link::class}")
//    val linkArm = tasks.getByName("linkDebugTestLinuxArm64")
//    (linkArm as KotlinNativeLink).apply {
//        kotlinOptions {
//            freeCompilerArgs += overrides
//            freeCompilerArgs += listOf("-linker-options", "-l systemd -l c")
//        }
//    }
//    println("${linkArm::class}")
}
