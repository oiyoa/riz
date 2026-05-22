@file:Suppress("UnstableApiUsage")

import com.android.build.api.variant.FilterConfiguration
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

val appName = "Riz"
val baseVersionCode = 1
val baseVersionName = "1.0.0"

val abiVersionCodes =
    mapOf(
        "armeabi-v7a" to 1,
        "x86" to 2,
        "arm64-v8a" to 3,
        "x86_64" to 4,
    )
val abiVersionCodeMultiplier = 1_000_000

val secretsProps =
    Properties().apply {
        val secretsFile = project.rootProject.file("secrets.properties")
        if (secretsFile.exists()) secretsFile.inputStream().use { load(it) }
    }
val localProps =
    Properties().apply {
        val localFile = project.rootProject.file("local.properties")
        if (localFile.exists()) localFile.inputStream().use { load(it) }
    }

fun secret(
    envName: String,
    propName: String,
): String =
    System.getenv(envName)
        ?: secretsProps.getProperty(propName)
        ?: localProps.getProperty(propName)
        ?: ""

val updaterManifestUrl = secret("RIZ_UPDATER_MANIFEST_URL", "updater.manifestUrl")
val updaterPubKeyHex = secret("RIZ_UPDATER_PUBKEY", "updater.publicKey")
val updaterPubKeyId = secret("RIZ_UPDATER_KEY_ID", "updater.keyId").ifBlank { "1" }

fun javaEscape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

val releaseKeystorePath = secret("RIZ_KEYSTORE_PATH", "riz.keystore.path").ifBlank { "keys/release.jks" }
val releaseKeystorePassword = secret("RIZ_KEYSTORE_PASSWORD", "riz.keystore.password")
val releaseKeyAlias = secret("RIZ_KEY_ALIAS", "riz.key.alias").ifBlank { "riz-release" }
val releaseKeyPassword = secret("RIZ_KEY_PASSWORD", "riz.key.password").ifBlank { releaseKeystorePassword }
val releaseKeystoreFile = rootProject.file(releaseKeystorePath)
val releaseSigningAvailable = releaseKeystoreFile.exists() && releaseKeystorePassword.isNotBlank() && releaseKeyPassword.isNotBlank()

if (!releaseSigningAvailable) {
    println(
        "BUILD: release signing NOT configured (missing ${releaseKeystoreFile.path} or blank passwords) — " +
            "debug builds OK; assembleRelease will fail. See secrets.properties.template.",
    )
}

android {
    namespace = "com.riz.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.riz.app"
        minSdk = 26
        targetSdk = 37
        versionCode = baseVersionCode
        versionName = baseVersionName

        buildConfigField("String", "UPDATER_MANIFEST_URL", "\"${javaEscape(updaterManifestUrl)}\"")
        buildConfigField("String", "UPDATER_PUBKEY_HEX", "\"${javaEscape(updaterPubKeyHex)}\"")
        buildConfigField("int", "UPDATER_KEY_ID", updaterPubKeyId.toIntOrNull()?.toString() ?: "1")
    }

    androidResources {
        localeFilters += listOf("en", "fa")
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = true
        // Network-dependent "newer version available" notices are not actionable signal.
        disable += "AndroidGradlePluginVersion"
        disable += "GradleDependency"
        disable += "NewerVersionAvailable"
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

spotless {
    kotlin {
        target("**/*.kt")
        ktlint("1.1.0")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.1.0")
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$projectDir/config/detekt/detekt.yml"))
    baseline = file("$projectDir/config/detekt/baseline.xml")
}

// Hard-fail assembleRelease when signing isn't wired
gradle.taskGraph.whenReady {
    if (!releaseSigningAvailable && allTasks.any { it.name == "assembleRelease" || it.name == "bundleRelease" }) {
        throw GradleException(
            "Release signing is not configured. Place the keystore at $releaseKeystorePath and set " +
                "riz.keystore.password / riz.key.alias / riz.key.password in secrets.properties. " +
                "See secrets.properties.template.",
        )
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi =
                output.filters
                    .find { it.filterType == FilterConfiguration.FilterType.ABI }
                    ?.identifier
            // Universal APK keeps base versionCode so installers prefer the
            // per-ABI APK when one matches the device.
            output.versionCode.set((abiVersionCodes[abi] ?: 0) * abiVersionCodeMultiplier + baseVersionCode)

            val abiPart = abi ?: "universal"
            output.outputFileName.set("$appName-$baseVersionName-$abiPart-${variant.buildType}.apk")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.zxcvbn)
    implementation(libs.oiyoa.updater)

    debugImplementation(libs.androidx.ui.tooling)
}
