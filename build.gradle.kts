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
}
apply(plugin = "kotlinx-atomicfu")

group = "com.monkopedia"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {
        binaries {
            sharedLib { }
        }
        compilations.getByName("main") {
            cinterops {
                val sdbus by creating
            }
            kotlinOptions {
                freeCompilerArgs += listOf("-opt-in=kotlinx.cinterop.ExperimentalForeignApi")
            }
        }
    }
    sourceSets {
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

afterEvaluate {
    tasks.all {
        (this as? KotlinNativeLink)?.kotlinOptions {
            freeCompilerArgs += "-Xoverride-konan-properties=linker.linux_x64=/usr/bin/ld.gold"
        }
        (this as? KotlinNativeCompile)?.kotlinOptions {
            freeCompilerArgs += "-Xoverride-konan-properties=linker.linux_x64=/usr/bin/ld.gold"
        }
    }
    val link = tasks.getByName("linkDebugTestNative")
    (link as KotlinNativeLink).apply {
        kotlinOptions {
            freeCompilerArgs += "-Xoverride-konan-properties=linker.linux_x64=/usr/bin/ld.gold"
            freeCompilerArgs += listOf("-linker-options", "-l systemd -l c")
//            freeCompilerArgs += listOf("-Xlinker", "-l", "/usr/lib/expat")
        }
    }
    println("${link::class}")
}