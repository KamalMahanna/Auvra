plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.metrolist.innertube"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    if (providers.gradleProperty("useMavenLocalInnerTubeX").isPresent) {
        api("com.github.MetrolistGroup:innertubex:${libs.versions.innertubex.get()}")
    } else {
        api(libs.innertubex)
    }
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)
    implementation(libs.timber)
    testImplementation(libs.junit)

    coreLibraryDesugaring(libs.desugaring)
}
