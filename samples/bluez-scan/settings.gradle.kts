rootProject.name = "bluez-scan"

pluginManagement {
    includeBuild("../..")
    repositories {
        gradlePluginPortal()
        mavenLocal()
    }
}

includeBuild("../..")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}
