import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("org.jlleitschuh.gradle.ktlint")
    alias(libs.plugins.dokka)
    alias(libs.plugins.vannik.publish)
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
    api(libs.kotlinx.serialization)
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
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
val dokkaJavadoc = tasks.create("dokkaJavadocCustom", DokkaTask::class) {
    project.dependencies {
        plugins("org.jetbrains.dokka:kotlin-as-java-plugin:2.0.0")
    }
    // outputFormat = "javadoc"
    outputDirectory.set(File(project.buildDir, "javadoc"))
    inputs.dir("src/main/kotlin")
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

mavenPublishing {
    pom {
        name.set("sdbus-kotlin-codegen")
        description.set("A kotlin/native dbus client code generator")
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
            developerConnection.set(
                "scm:git:ssh://github.com/Monkopedia/sdbus-kotlin.git"
            )
            url.set("https://github.com/Monkopedia/sdbus-kotlin/")
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
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}
