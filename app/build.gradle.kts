import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.auvra.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.auvra.app"
        minSdk = 24
        targetSdk = 35

        val envVersionCode = System.getenv("APP_VERSION_CODE")
        versionCode = envVersionCode?.toIntOrNull() ?: 1

        val envVersionName = System.getenv("APP_VERSION_NAME")
        versionName = envVersionName ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val localProps = Properties()
            val localFile = rootProject.file("local.properties")
            if (localFile.exists()) {
                localFile.inputStream().use { localProps.load(it) }
            }
            fun prop(key: String): String? = localProps.getProperty(key) ?: System.getenv(key)

            val keystorePath = prop("KEYSTORE_PATH")
            val keystorePass = prop("KEYSTORE_PASSWORD")
            val alias = prop("KEY_ALIAS")
            val aliasPass = prop("KEY_PASSWORD")

            if (!keystorePath.isNullOrBlank() && !keystorePass.isNullOrBlank() &&
                !alias.isNullOrBlank() && !aliasPass.isNullOrBlank()) {
                val ksFile = rootProject.file(keystorePath)
                if (ksFile.exists()) {
                    storeFile = ksFile
                    storePassword = keystorePass
                    keyAlias = alias
                    keyPassword = aliasPass
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            val isSigningConfigured = signingConfigs.getByName("release").storeFile != null
            signingConfig = if (isSigningConfigured) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/*.version"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugaring)
    implementation(project(":innertube"))
    implementation(libs.timber)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.moshi)
    ksp(libs.moshi.codegen)
    implementation(libs.okhttp)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.coroutines.android)
    implementation(libs.jaudiotagger)
    implementation(libs.androidx.palette)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
