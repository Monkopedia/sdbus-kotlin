rootProject.name = "demo-service"

pluginManagement {
    includeBuild("../..")
    repositories {
        gradlePluginPortal()
        mavenLocal()
    }
}

// Composite build: pulls in the sdbus-kotlin library that lives two directories up so the
// sample always builds against the *current* source tree (the post-0.5.0 API), not the
// older artifact published to Maven Central. Gradle automatically substitutes the
// `com.monkopedia:sdbus-kotlin` dependency declared in build.gradle.kts with this local
// build, so the version coordinate there is only a placeholder.
includeBuild("../..")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}
