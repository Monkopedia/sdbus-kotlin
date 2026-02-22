plugins {
    kotlin("multiplatform") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("com.monkopedia.sdbus.plugin")
}

repositories {
    mavenCentral()
    mavenLocal()
}

sdbus {
    sources.srcDirs("src/dbusMain")
    outputs.add("commonMain")
    generateProxies = true
}

kotlin {
    val systemdLibDir = rootDir.resolve("../../libs/x86_64/257.2-2/lib").canonicalFile
    jvm("jvm")
    linuxX64("native") {
        binaries {
            executable {
                linkerOpts(
                    "-L${systemdLibDir.absolutePath}",
                    "-lsystemd",
                    "-Wl,-rpath,${systemdLibDir.absolutePath}"
                )
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.10.0-RC")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation(kotlin("stdlib"))
                implementation("com.monkopedia:sdbus-kotlin:0.4.0")
            }
        }
        val nativeTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
    }
}

tasks.register<JavaExec>("runJvm") {
    group = "application"
    description = "Run the Bluez scan sample on JVM."
    dependsOn("jvmMainClasses")
    mainClass.set("MainKt")
    val jvmMainCompilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
    classpath(
        jvmMainCompilation.output.allOutputs,
        configurations.getByName("jvmRuntimeClasspath")
    )
}

tasks.matching { it.name.startsWith("compileKotlin") }.configureEach {
    dependsOn("generateSdbusWrappers")
}
