pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Essential for IntelliJ SDKs
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    }
}
plugins {
    // The version is defined ONLY here
    id("org.jetbrains.intellij.platform.settings") version "2.16.0"
}
rootProject.name = "com.bitwise.plugin"
