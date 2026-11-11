plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

fun mobwriterValue(property: String, env: String): String {
    val fromProperty = providers.gradleProperty(property).orNull?.trim().orEmpty()
    if (fromProperty.isNotEmpty()) return fromProperty
    return System.getenv(env)?.trim().orEmpty()
}

fun updateSlug(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return ""
    val ok = trimmed.matches(Regex("^[A-Za-z0-9_.-]{1,100}$")) &&
        !trimmed.contains("..") &&
        !trimmed.startsWith(".") &&
        !trimmed.startsWith("-") &&
        !trimmed.endsWith(".") &&
        !trimmed.endsWith("-")
    require(ok) { "Update repository owner and name must be GitHub slugs" }
    return trimmed
}

val updateOwner = updateSlug(mobwriterValue("mobwriter.updateOwner", "MOBWRITER_UPDATE_OWNER"))
val updateRepo = updateSlug(mobwriterValue("mobwriter.updateRepo", "MOBWRITER_UPDATE_REPO"))
val publishedVersionCode = mobwriterValue("mobwriter.versionCode", "MOBWRITER_VERSION_CODE").toIntOrNull()?.takeIf { it > 0 }
val publishedVersionName = mobwriterValue("mobwriter.versionName", "MOBWRITER_VERSION_NAME")
    .takeIf { it.matches(Regex("^[A-Za-z0-9._+-]{1,40}$")) }
// Release signing passwords come from the environment so they stay off the Gradle command line.
// The release workflow requires ANDROID_KEYSTORE_* secrets and does not mint a debug key. See release-apk.yml.
val releaseStoreFile = mobwriterValue("mobwriter.storeFile", "MOBWRITER_STORE_FILE")
val releaseStorePassword = mobwriterValue("mobwriter.storePassword", "MOBWRITER_STORE_PASSWORD")
val releaseKeyAlias = mobwriterValue("mobwriter.keyAlias", "MOBWRITER_KEY_ALIAS")
val releaseKeyPassword = mobwriterValue("mobwriter.keyPassword", "MOBWRITER_KEY_PASSWORD")
val hasReleaseKey = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it.isNotEmpty() }

android {
    namespace = "dev.mobwriter.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.mobwriter.app"
        minSdk = 26
        targetSdk = 35
        versionCode = publishedVersionCode ?: 1
        versionName = publishedVersionName ?: "1.0.0"
        buildConfigField("String", "UPDATE_OWNER", "\"$updateOwner\"")
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core"))
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("ru.noties:jlatexmath-android:0.2.0")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
