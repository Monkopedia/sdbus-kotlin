plugins {
    kotlin("multiplatform") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    id("com.monkopedia.sdbus.plugin") version "0.2.2"
}

repositories {
    mavenCentral()
    mavenLocal()
}

sdbus {
    sources.srcDirs("src/dbusMain")
    outputs.add("nativeMain")
    generateProxies = true
}

kotlin {
    linuxX64("native") {
        binaries {
            executable { }
        }
        compilerOptions {
            freeCompilerArgs.set(listOf("-linker-options", "-L /usr/lib"))
        }
    }
    sourceSets {
        val nativeMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
                implementation(kotlin("stdlib"))
                implementation("com.monkopedia:sdbus-kotlin:0.2.2")
            }
        }
        val nativeTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0-RC")
            }
        }
    }
}
