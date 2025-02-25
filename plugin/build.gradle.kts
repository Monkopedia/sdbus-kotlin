import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`

    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.gradle.publish)
    `maven-publish`
    `signing`
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

    testImplementation("org.jetbrains.kotlin:kotlin-test")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

java {
    withJavadocJar()
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

gradlePlugin {
    website = "https://github.com/monkopedia/sdbus-kotlin"
    vcsUrl = "https://github.com/monkopedia/sdbus-kotlin"

    val sdbusPlugin by plugins.creating {
        id = "com.monkopedia.sdbus.plugin"
        implementationClass = "com.monkopedia.sdbus.plugin.SdbusPlugin"
        displayName = "Sdbus-Kotlin Gradle Plugin"
        description = project.description
        tags = listOf("kotlin", "kotlin/native", "sdbus", "interop")
    }
}

publishing {
    publications.all {
        if (this !is MavenPublication) return@all

        afterEvaluate {
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
}
