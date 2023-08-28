
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("de.fayard.refreshVersions") version "0.60.1"
}

refreshVersions {
}

rootProject.name = "kt-search-logback-appender"
