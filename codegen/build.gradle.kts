import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("org.jlleitschuh.gradle.ktlint")
    alias(libs.plugins.dokka)
    alias(libs.plugins.vannik.publish)
    signing
}

group = "com.monkopedia"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.xmlutil)
    api(libs.clikt)
    api(libs.kotlinpoet)
    testImplementation(project(":"))
    testImplementation(libs.kotlin.compiler.embeddable)
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

// Rewrites the checked-in golden files GeneratorTest compares against, for use after an
// intentional generator change. Kept separate from the test task on purpose: the test only reads
// goldens, this only writes them, so neither can pass itself off as the other.
tasks.register<JavaExec>("regenerateGoldens") {
    group = "build"
    description = "Regenerates the codegen golden fixtures from their test.xml inputs."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.monkopedia.sdbus.RegenerateGoldensKt")
    args(layout.projectDirectory.dir("src/test/resources").asFile.absolutePath)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-jvm-default=enable")
    }
}
java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
val dokkaAssets = rootProject.file("dokka/assets").listFiles()?.toList().orEmpty()
val dokkaLogoStyleSheet = rootProject.file("dokka/styles/logo-styles.css")

dokka {
    dokkaPublications.named("html") {
        outputDirectory.set(projectDir.resolve("build/dokka"))
        // #264: the same defect #254/#263 fixed for the root project was still live here. Dokka
        // only *prints* `Couldn't resolve link: [...]`, so this task exited BUILD SUCCESSFUL with
        // five dead KDoc links in AdaptorGenerator.kt. This module's docs are not on the site --
        // pages.yaml publishes only the root project -- but dokkaJavadocJar packages *this* task's
        // output and is in the publishToMavenLocal/Central graph, so the gate sits directly in
        // front of a published artifact. failOnWarning makes the expected count zero rather than a
        // baseline someone edits upward, and asserting it in the build (not in a workflow) means a
        // local run of this task fails exactly the way CI does.
        //
        // Deliberately broader than "unresolved links": it fails on ANY Dokka warning. Measured at
        // 76d22a3 that is a distinction without a difference -- every warning this task emitted was
        // an unresolved link (5 of 5) -- and the broad gate needs no log parsing, so unlike a grep
        // for `Couldn't resolve link` it cannot be defeated by Dokka rewording that message. If a
        // future Dokka emits some new warning category, fix it or suppress that category; turning
        // this flag back off restores the #254 defect wholesale.
        failOnWarning.set(true)
    }
    pluginsConfiguration.html {
        customAssets.from(dokkaAssets)
        customStyleSheets.from(dokkaLogoStyleSheet)
    }
}

mavenPublishing {
    coordinates("com.monkopedia", "sdbus-kotlin-codegen", project.version.toString())
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
}
