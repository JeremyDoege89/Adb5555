import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.dreamz.adb5555"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.dreamz.adb5555"
        // Wireless debugging (pairing + TLS) exists from Android 11.
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        // Phones only: Conscrypt's native library is ~2 MB per ABI.
        ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a") }
    }

    // Local, git-ignored keystore.properties: storeFile / storePassword / keyAlias / keyPassword.
    val keystoreProps = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }
    }
    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    packaging {
        resources.excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    implementation(libs.libadb)
    implementation(libs.conscrypt)
    implementation(libs.bcpkix)
    testImplementation(libs.junit)
}
