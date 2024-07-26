plugins {
    application
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
}

group = "com.monkopedia.sdbus"

repositories {
    mavenCentral()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    implementation(libs.xmlutil)
    implementation(libs.clikt)
    implementation(libs.kotlinpoet)
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
