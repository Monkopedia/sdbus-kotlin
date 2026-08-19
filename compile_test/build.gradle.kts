import kotlinx.validation.ExperimentalBCVApi
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "com.monkopedia"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    linuxX64 {
        binaries {
            sharedLib { }
        }
        compilerOptions {
            freeCompilerArgs.set(listOf("-linker-options", "-L /usr/lib"))
        }
    }
    applyDefaultHierarchyTemplate()
    sourceSets {
        getByName("nativeMain") {
            // TestFiles symlinks the whole codegen fixture tree into one compilation. The
            // `hinted-*` goldens are the same interfaces named from annotations, so they would
            // redeclare their default counterparts; :codegen compiles that output on its own.
            kotlin.exclude("**/hinted-*.kt")
            dependencies {
                implementation(libs.kotlinx.coroutines)
                implementation(libs.kotlinx.serialization)
                implementation(libs.kotlinx.atomicfu)
                implementation(kotlin("stdlib"))
                implementation(project(":"))
            }
        }
        getByName("nativeTest") {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
