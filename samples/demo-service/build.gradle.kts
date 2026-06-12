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
    // Server/adaptor side: generate the *Adaptor base classes from the introspection XML.
    // Proxies are generated too so the sample's `client` mode can call itself for a demo.
    generateAdapters = true
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
                // Version is a placeholder: the composite build in settings.gradle.kts
                // substitutes this with the local sdbus-kotlin source tree.
                implementation("com.monkopedia:sdbus-kotlin:0.4.5")
            }
        }
    }
}

tasks.register<JavaExec>("runJvm") {
    group = "application"
    description = "Run the demo service on the JVM. Pass args after '--args', e.g. --args=client."
    dependsOn("jvmMainClasses")
    mainClass.set("MainKt")
    val jvmMainCompilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
    classpath(
        jvmMainCompilation.output.allOutputs,
        configurations.getByName("jvmRuntimeClasspath")
    )
}
