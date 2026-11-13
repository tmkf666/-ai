import java.util.Properties

plugins {
    id("com.android.application")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("release.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun signingValue(key: String, envKey: String): String? {
    return System.getenv(envKey) ?: keystoreProperties.getProperty(key)
}

val storeFilePath = signingValue("storeFile", "SIGNING_STORE_FILE")
val storePasswordValue = signingValue("storePassword", "SIGNING_STORE_PASSWORD")
val keyAliasValue = signingValue("keyAlias", "SIGNING_KEY_ALIAS")
val keyPasswordValue = signingValue("keyPassword", "SIGNING_KEY_PASSWORD")

val hasReleaseSigning =
    !storeFilePath.isNullOrBlank() &&
    !storePasswordValue.isNullOrBlank() &&
    !keyAliasValue.isNullOrBlank() &&
    !keyPasswordValue.isNullOrBlank() &&
    !storePasswordValue.contains("YOUR_") &&
    !keyPasswordValue.contains("YOUR_") &&
    file(storeFilePath).exists()

android {
    namespace = "com.mocitianxia.ai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mocitianxia.ai"
        minSdk = 21
        targetSdk = 36
        versionCode = 2
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(storeFilePath!!)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }

            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            lint {
                checkReleaseBuilds = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = false
    }

    packaging {
        resources {
            excludes += setOf("META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:deprecation")
}

dependencies {
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("com.google.android.material:material:1.12.0")
}