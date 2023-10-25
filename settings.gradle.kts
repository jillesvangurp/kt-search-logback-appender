
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("de.fayard.refreshVersions") version "0.60.3"
}

refreshVersions {
}

rootProject.name = "kt-search-logback-appender"
