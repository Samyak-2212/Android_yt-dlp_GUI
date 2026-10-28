plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.chaquopy)
}

android {
  namespace = "com.ytdlp.android"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.ytdlp.android"
    minSdk = 24
    targetSdk = 35
    versionCode = 1
    versionName = "1.0.0"

    ndk {
      abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    jniLibs {
      useLegacyPackaging = false
    }
  }
}

kotlin {
  jvmToolchain(17)
}

chaquopy {
  defaultConfig {
    version = "3.10"
    buildPython("C:/Users/naska/AppData/Roaming/uv/python/cpython-3.10-windows-x86_64-none/python.exe")
    pip {
      install("yt-dlp")
    }
  }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)

  // DataStore Preferences
  implementation(libs.androidx.datastore.preferences)

  // OkHttp & Serialization
  implementation(libs.okhttp)
  implementation(libs.kotlinx.serialization.json)

  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
