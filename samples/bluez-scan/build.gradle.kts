plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.serialization)
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
                implementation(libs.kotlinx.coroutines)
                implementation(libs.kotlinx.serialization)
                implementation(libs.kotlinx.datetime)
                implementation(kotlin("stdlib"))
                implementation("com.monkopedia:sdbus-kotlin:0.4.3")
            }
        }
        val nativeTest by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
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

