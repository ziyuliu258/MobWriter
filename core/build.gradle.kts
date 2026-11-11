import java.util.Properties

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // Private real-world fixtures are opt-in and never compiled into the app.
    if (providers.gradleProperty("includeLocalPosts").orNull == "true") {
        val privateFile = rootProject.file("config/private.local.properties")
        require(privateFile.isFile) { "Create config/private.local.properties first" }
        val privateConfig = Properties().apply {
            privateFile.reader(Charsets.UTF_8).use { load(it) }
        }
        val postsDirectory = privateConfig.getProperty("test.postsDirectory")
        require(!postsDirectory.isNullOrBlank()) { "Set test.postsDirectory in the local configuration" }
        systemProperty("mobwriter.test.postsDirectory", postsDirectory)
    }
}
