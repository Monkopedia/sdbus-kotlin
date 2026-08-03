import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-gradle-plugin`

    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.gradle.publish)
    alias(libs.plugins.vannik.publish)
    signing
}

repositories {
    mavenCentral()
    mavenLocal()
}

group = "com.monkopedia.sdbus"
description = "Tool to generate sd-bus wrapper code for use with sdbus-kotlin"

dependencies {
    implementation(libs.kotlin.gradle)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.clikt)
    api(project(":codegen"))

    testImplementation(gradleTestKit())
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

java {
    withJavadocJar()
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

gradlePlugin {
    website = "https://github.com/monkopedia/sdbus-kotlin"
    vcsUrl = "https://github.com/monkopedia/sdbus-kotlin"

    plugins.create("sdbusPlugin") {
        id = "com.monkopedia.sdbus.plugin"
        implementationClass = "com.monkopedia.sdbus.plugin.SdbusPlugin"
        displayName = "Sdbus-Kotlin Gradle Plugin"
        description = project.description
        tags = listOf("kotlin", "kotlin/native", "sdbus", "interop")
    }
}

mavenPublishing {
    pom {
        name.set("sdbus-kotlin-gradle-plugin")
        description.set(project.description)
        url.set("https://www.github.com/Monkopedia/sdbus-kotlin")
        licenses {
            license {
                name.set("GNU LESSER GENERAL PUBLIC LICENSE, version 3, 29 June 2007")
                url.set("https://www.gnu.org/licenses/")
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
    publishToMavenCentral(automaticRelease = true)

    // SNAPSHOT builds (e.g. publishToMavenLocal for downstream testing) are not signed:
    // Maven Central doesn't require signatures for snapshots, and it avoids needing a GPG
    // key for local cross-project validation.
    if (!version.toString().endsWith("SNAPSHOT")) {
        signAllPublications()
    }
}

if (!version.toString().endsWith("SNAPSHOT")) {
    signing {
        useGpgCmd()
        sign(publishing.publications)
    }
} else {
    // com.gradle.plugin-publish registers its own Sign tasks for the plugin publications,
    // and those fail rather than skip once no signatory is configured.
    tasks.withType<Sign>().configureEach { enabled = false }
}
