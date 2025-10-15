plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.solvoice"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.solvoice"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    
    // ViewModel and LiveData for MVVM architecture
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    implementation("androidx.compose.runtime:runtime-livedata:1.5.4")
    
    // Navigation for Compose
    implementation("androidx.navigation:navigation-compose:2.7.4")
    
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Network operations for Solana RPC calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    
    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Crypto libraries for Solana key management
    implementation("org.bitcoinj:bitcoinj-core:0.16.2")
    implementation("com.google.guava:guava:32.1.3-android")
    
    // Base58 encoding (needed for Solana addresses) - included in bitcoinj
    // Note: bitcoinj-core already includes Base58 encoding support
    
    // Audio recording and playback
    implementation("androidx.media:media:1.7.0")
    
    // Permissions handling
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    
    // Icons for better UI
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    
    // Date and time handling
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
    
    // Preferences/Settings storage
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Testing dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}