@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import kotlinx.validation.ExperimentalBCVApi
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

buildscript {
    dependencies {
        classpath(libs.bundles.dokka)
    }
}
plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.bcv)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dokka)

    `maven-publish`
    signing
}

group = "com.monkopedia"

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
val dokkaJavadoc = tasks.create("dokkaJavadocCustom", DokkaTask::class) {
    project.dependencies {
        plugins("org.jetbrains.dokka:kotlin-as-java-plugin:1.9.20")
    }
    // outputFormat = "javadoc"
    outputDirectory.set(File(project.buildDir, "javadoc"))
    inputs.dir("src/commonMain/kotlin")
}

val javadocJar = tasks.create("javadocJar", Jar::class) {
    dependsOn(dokkaJavadoc)
    archiveClassifier.set("javadoc")
    from(File(project.buildDir, "javadoc"))
}

tasks.dokkaHtml.configure {
    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
        customAssets = file("dokka/assets").listFiles()?.toList().orEmpty()
        customStyleSheets = file("dokka/styles").listFiles()?.toList().orEmpty()
    }

    outputDirectory.set(buildDir.resolve("dokka"))
}
publishing {
    publications.all {
        if (this !is MavenPublication) return@all
        artifact(javadocJar)
        pom {
            name.set(project.name)
            description.set("A kotlin/native dbus client")
            url.set("http://www.github.com/Monkopedia/sdbus-kotlin")
            licenses {
                license {
//                    name.set("The Apache License, Version 2.0")
//                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("monkopedia")
                    name.set("Jason Monk")
                    email.set("monkopedia@gmail.com")
                }
            }
            scm {
                connection.set("scm:git:git://github.com/Monkopedia/sdbus-kotlin.git")
                developerConnection.set("scm:git:ssh://github.com/Monkopedia/sdbus-kotlin.git")
                url.set("http://github.com/Monkopedia/sdbus-kotlin/")
            }
        }
    }
    repositories {
        maven(url = "https://oss.sonatype.org/service/local/staging/deploy/maven2/") {
            name = "OSSRH"
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}

afterEvaluate {
    tasks.withType(Sign::class) {
        val signingTask = this
        tasks.withType(AbstractPublishToMaven::class) {
            dependsOn(signingTask)
        }
    }
}
