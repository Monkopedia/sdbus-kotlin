plugins {
    application
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
    `maven-publish`
    signing
}

group = "com.monkopedia.sdbus"

repositories {
    mavenCentral()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    api(libs.xmlutil)
    api(libs.clikt)
    api(libs.kotlinpoet)
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.2")
}

tasks.register("fatJar", type = Jar::class) {
    archiveBaseName = "${project.name}-all"
    manifest {
        attributes["Implementation-Title"] = "sdbus-kotlin codegen"
        attributes["Implementation-Version"] = version
        attributes["Main-Class"] = "com.monkopedia.sdbus.Xml2KotlinKt"
    }
    from(configurations["runtimeClasspath"].map { if (it.isDirectory) it else zipTree(it) })
    with(tasks["jar"] as CopySpec)
    duplicatesStrategy = DuplicatesStrategy.WARN
}

application {
    mainClass.set("com.monkopedia.sdbus.Xml2KotlinKt")
}

ktlint {
    this.android.set(true)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create("mavenJava", MavenPublication::class.java) {
            afterEvaluate {
                from(components["java"])
                pom {
                    name.set("sdbus-kotlin-codegen")
                    description.set(project.description)
                    url.set("http://www.github.com/Monkopedia/sdbus-kotlin")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
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
                        developerConnection.set(
                            "scm:git:ssh://github.com/Monkopedia/sdbus-kotlin.git"
                        )
                        url.set("http://github.com/Monkopedia/sdbus-kotlin/")
                    }
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
