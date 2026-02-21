rootProject.name = "bluez-scan"

pluginManagement {
    includeBuild("../..")
    repositories {
        gradlePluginPortal()
        mavenLocal()
    }
}

includeBuild("../..")
