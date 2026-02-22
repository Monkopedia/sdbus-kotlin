@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import java.util.*
import kotlinx.validation.ExperimentalBCVApi
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

buildscript {
    dependencies {
        classpath(libs.bundles.dokka)
    }
}
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.bcv)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.hierynomus.license)
    alias(libs.plugins.dokka)
    alias(libs.plugins.vannik.publish)
    signing
}

group = "com.monkopedia"

repositories {
    mavenCentral()
}

val systemdVersion = "257.2-2"

// == BCV setup ==
apiValidation {
    ignoredProjects.addAll(listOf("compile_test", "cross_test", "stress_test"))
    @OptIn(ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}

kotlin {
    jvm()
    linuxX64 {
        binaries {
            sharedLib { }
        }
        compilations.getByName("main") {
            cinterops {
                create("sdbus-$systemdVersion")
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
    linuxArm64 {
        binaries {
            sharedLib { }
        }
        compilations.getByName("main") {
            cinterops {
                create("sdbus-$systemdVersion")
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
                implementation(libs.kotlinx.coroutines)
                implementation(libs.kotlinx.serialization)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.atomicfu)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines)
                implementation(libs.kotlinx.serialization)
                implementation(libs.kotlinx.datetime)
                implementation(libs.dbus.java.core)
                runtimeOnly(libs.dbus.java.transport.junixsocket)
                implementation(kotlin("stdlib"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.mockk)
            }
        }
        val nativeMain by getting {
            dependencies {
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

fun firstExistingPath(vararg candidates: String): String? =
    candidates.firstOrNull { rootProject.file(it).exists() }

val overrides = buildMap<String, String> {
    firstExistingPath(
        "/usr/bin/ld.gold",
        "/usr/bin/ld"
    )?.let { put("linker.linux_x64", it) }

    firstExistingPath(
        "/usr/bin/aarch64-linux-gnu-ld.gold",
        "/usr/bin/aarch64-linux-gnu-ld",
        "/usr/bin/aarch64-linux-gnu-ld.bfd"
    )?.let { put("linker.linux_x64-linux_arm64", it) }
}.entries.map { "-Xoverride-konan-properties=${it.key}=${it.value}" }

fun systemdLibDirForTask(taskName: String): String? = when {
    taskName.contains("LinuxX64", ignoreCase = true) ->
        rootProject.file("libs/x86_64/$systemdVersion/lib").absolutePath

    taskName.contains("LinuxArm64", ignoreCase = true) ->
        rootProject.file("libs/aarch64/$systemdVersion/lib").absolutePath

    else -> null
}

fun arm64RuntimeLibDirs(): List<String> = listOf(
    "/lib/aarch64-linux-gnu",
    "/usr/lib/aarch64-linux-gnu",
    "/usr/aarch64-linux-gnu/lib"
).map { rootProject.file(it) }
    .filter { it.exists() }
    .map { it.absolutePath }

afterEvaluate {
    tasks.all {
        val localSystemdLibDir = systemdLibDirForTask(name)
        (this as? KotlinNativeLink)?.toolOptions {
            freeCompilerArgs.addAll(overrides)
            if (localSystemdLibDir != null) {
                freeCompilerArgs.addAll(
                    listOf(
                        "-linker-option",
                        "-L$localSystemdLibDir",
                        "-linker-option",
                        "-Wl,-rpath,$localSystemdLibDir"
                    )
                )
            }
            if (name.contains("LinuxArm64", ignoreCase = true)) {
                freeCompilerArgs.addAll(
                    listOf(
                        "-linker-option",
                        "-Wl,--allow-shlib-undefined"
                    )
                )
                arm64RuntimeLibDirs().forEach { armLibDir ->
                    freeCompilerArgs.addAll(
                        listOf(
                            "-linker-option",
                            "-L$armLibDir",
                            "-linker-option",
                            "-Wl,-rpath-link,$armLibDir"
                        )
                    )
                }
            }
        }
        (this as? KotlinNativeCompile)?.compilerOptions {
            freeCompilerArgs.addAll(overrides)
        }
    }
}

tasks.register("crossPlatformTests") {
    group = "verification"
    description = "Runs cross-platform shared API tests in the dedicated cross_test module."
    dependsOn(":cross_test:jvmTest", ":cross_test:linuxX64Test")
}

tasks.register("crossRuntimeInteropTests") {
    group = "verification"
    description = "Runs JVM/native direct-bus interop tests in the dedicated cross_test module."
    dependsOn(":cross_test:jvmInteropTest")
}

tasks.register("crossRuntimeStressTests") {
    group = "verification"
    description = "Runs opt-in JVM/native stress tests in the dedicated stress_test module."
    dependsOn(":stress_test:jvmInteropStressTest")
}

val dokkaAssets = rootProject.file("dokka/assets").listFiles()?.toList().orEmpty()
val dokkaLogoStyleSheet = rootProject.file("dokka/styles/logo-styles.css")

dokka {
    dokkaPublications.named("html") {
        outputDirectory.set(projectDir.resolve("build/dokka"))
    }
    pluginsConfiguration.html {
        customAssets.from(dokkaAssets)
        customStyleSheets.from(dokkaLogoStyleSheet)
    }
}
mavenPublishing {
    pom {
        name.set(project.name)
        description.set("A kotlin/native dbus client")
        url.set("https://www.github.com/Monkopedia/sdbus-kotlin")
        licenses {
            license {
                name.set("GNU LESSER GENERAL PUBLIC LICENSE Version 3, 29 June 2007")
                url.set("https://www.gnu.org/licenses/lgpl-3.0.txt")
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
            url.set("https://github.com/Monkopedia/sdbus-kotlin/")
        }
    }
    publishToMavenCentral()

    signAllPublications()
}

tasks.register(
    "licenseCheckForKotlin",
    com.hierynomus.gradle.license.tasks.LicenseCheck::class
) {
    source = fileTree(project.projectDir) {
        include("**/*.kt")
        exclude("**/test/resources/**/*.kt")
        exclude("**/compile_test/**/*.kt")
    }
}
tasks["license"].dependsOn("licenseCheckForKotlin")
tasks.register(
    "licenseFormatForKotlin",
    com.hierynomus.gradle.license.tasks.LicenseFormat::class
) {
    source = fileTree(project.projectDir) {
        include("**/*.kt")
        exclude("**/test/resources/**/*.kt")
        exclude("**/compile_test/**/*.kt")
    }
}
tasks["licenseFormat"].dependsOn("licenseFormatForKotlin")

license {
    header = rootProject.file("license-header.txt")
    skipExistingHeaders = true
    strictCheck = false
    ext["year"] = Calendar.getInstance().get(Calendar.YEAR)
    ext["name"] = "Jason Monk"
    ext["email"] = "monkopedia@gmail.com"
}

afterEvaluate {
    tasks.findByName("licenseCheckForKotlin")?.let {
        tasks.all {
            if ((this.name.startsWith("ktlint") && this.name.endsWith("Check")) ||
                (this.name.startsWith("transform") && this.name.endsWith("Metadata")) ||
                (this.name.startsWith("compile") && this.name.contains("Kotlin")) ||
                this.name.startsWith("link") ||
                this.name == "copyLib" ||
                this.name.endsWith("Test") ||
                this.name.endsWith("Tests") ||
                this.name == "processTestResources" ||
                this.name == "test"
            ) {
                it.dependsOn(this)
            }
        }
    }
}
allprojects {
    if (name == "compile_test") return@allprojects
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        android.set(true)
        filter {
            exclude("**/test/resources/**/*.kt", "**/compile_test/**/*.kt")
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}
