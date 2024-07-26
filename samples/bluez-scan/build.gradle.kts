plugins {
    kotlin("multiplatform") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
}

repositories {
    mavenCentral()
    mavenLocal()
}

kotlin {
    linuxX64("native") {
        binaries {
            executable { }
        }
        compilerOptions {
            freeCompilerArgs.set(listOf("-linker-options", "-L /usr/lib -l systemd"))
        }
    }
    sourceSets {
        val nativeMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
                implementation(kotlin("stdlib"))
		        implementation("com.monkopedia:sdbus-kotlin:0.1.0")
            }
        }
        val nativeTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0-RC")
            }
        }
    }
}
