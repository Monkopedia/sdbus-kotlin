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
    implementation("io.github.pdvrieze.xmlutil:serialization:0.90.1")
    implementation("com.github.ajalt.clikt:clikt:4.4.0")
    implementation("com.squareup:kotlinpoet-jvm:1.17.0")
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.2")
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
    jvmToolchain(21)
}
