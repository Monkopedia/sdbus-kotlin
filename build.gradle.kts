@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import kotlinx.validation.ExperimentalBCVApi
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.24.0")
    }
}

plugins {

    kotlin("multiplatform") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.15.1"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"

    `maven-publish`
}
apply(plugin = "kotlinx-atomicfu")

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
    linuxArm64 {
        binaries {
            sharedLib { }
        }
        compilations.getByName("main") {
            cinterops {
                create("sdbus-aarch64-$systemdVersion")
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
    applyDefaultHierarchyTemplate()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }
        val nativeMain by getting {
            dependencies {
                implementation("com.github.ajalt.clikt:clikt:4.4.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
                implementation(kotlin("stdlib"))
            }
        }
        val nativeTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0-RC")
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
    val linkArm = tasks.getByName("linkDebugTestLinuxArm64")
    (linkArm as KotlinNativeLink).apply {
        kotlinOptions {
            freeCompilerArgs += overrides
            freeCompilerArgs += listOf("-linker-options", "-l systemd -l c")
        }
    }
    println("${linkArm::class}")
}
