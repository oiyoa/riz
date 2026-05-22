@file:Suppress("UnstableApiUsage")

import java.util.Properties

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Riz"
include(":app")

val localProps =
    Properties().apply {
        val localFile = file("local.properties")
        if (localFile.exists()) localFile.inputStream().use { load(it) }
    }
val sharedLibsPath =
    providers.gradleProperty("oiyoa.sharedLibs.path").orNull
        ?: localProps.getProperty("oiyoa.sharedLibs.path")
val useLocal = providers.gradleProperty("oiyoa.useLocalLibs").orNull != "false"

if (sharedLibsPath != null && useLocal) {
    val sharedLibs = file(sharedLibsPath)
    if (sharedLibs.exists()) {
        includeBuild(sharedLibs) {
            dependencySubstitution {
                substitute(module("com.oiyoa.android:manifest-format")).using(project(":manifest-format"))
                substitute(module("com.oiyoa.android:fronted-http")).using(project(":fronted-http"))
                substitute(module("com.oiyoa.android:updater")).using(project(":updater"))
            }
        }
    }
}
