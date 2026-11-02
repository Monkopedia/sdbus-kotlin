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
        // The `hinted-*` goldens nativeMain has to leave out, given a compilation of their own.
        compilations.create("hinted") {
            defaultSourceSet {
                kotlin.srcDir("../codegen/src/test/resources")
                kotlin.include("**/hinted-*.kt")
                dependencies {
                    implementation(libs.kotlinx.coroutines)
                    implementation(project(":"))
                }
            }
        }
    }
    applyDefaultHierarchyTemplate()
    sourceSets {
        getByName("nativeMain") {
            // TestFiles symlinks the whole codegen fixture tree into one compilation. The
            // `hinted-*` goldens are the same interfaces named from annotations, so they would
            // redeclare their default counterparts; the `hinted` compilation above compiles them.
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

// CI reaches this module through allTests, which only compiles nativeMain on its own.
tasks.named("allTests") {
    dependsOn("compileHintedKotlinLinuxX64")
}
