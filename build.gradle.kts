@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import java.util.*
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
    alias(libs.plugins.hierynomus.license)
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
    val systemdVersion = "257.2-2"
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

val overrides = mapOf<String, String>(
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
}
val dokkaJavadoc = tasks.create("dokkaJavadocCustom", DokkaTask::class) {
    project.dependencies {
        plugins("org.jetbrains.dokka:kotlin-as-java-plugin:2.0.0")
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

allprojects {
    if (name == "compile_test") return@allprojects
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "com.github.hierynomus.license")
    tasks.register(
        "licenseCheckForKotlin",
        com.hierynomus.gradle.license.tasks.LicenseCheck::class
    ) {
        source = fileTree(project.projectDir) { include("**/*.kt") }
    }
    tasks["license"].dependsOn("licenseCheckForKotlin")
    tasks.register(
        "licenseFormatForKotlin",
        com.hierynomus.gradle.license.tasks.LicenseFormat::class
    ) {
        source = fileTree(project.projectDir) { include("**/*.kt") }
    }
    tasks["licenseFormat"].dependsOn("licenseFormatForKotlin")

    this.license {
        header = rootProject.file("license-header.txt")
        includes(listOf("**/*.kt"))
        excludes(listOf("**/test/resources/**/*.kt", "**/compile_test/**/*.kt"))
        strictCheck = true
        ext["year"] = Calendar.getInstance().get(Calendar.YEAR)
        ext["name"] = "Jason Monk"
        ext["email"] = "monkopedia@gmail.com"
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        android.set(true)
    }
    afterEvaluate {
        tasks.findByName("licenseCheckForKotlin")?.let {
            tasks.all {
                if ((this.name.startsWith("ktlint") && this.name.endsWith("Check")) ||
                    (this.name.startsWith("transform") && this.name.endsWith("Metadata")) ||
                    (this.name.startsWith("compile") && this.name.contains("Kotlin")) ||
                    this.name.startsWith("link") || this.name == "copyLib" ||
                    this.name.endsWith("Test") || this.name.endsWith("Tests")
                ) {
                    it.dependsOn(this)
                }
            }
        }
    }
}
