plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.qlchitieu"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.qlchitieu"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")


    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))

    // Firebase Authentication - đăng ký, đăng nhập
    implementation("com.google.firebase:firebase-auth")

    // Cloud Firestore - lưu dữ liệu
    implementation("com.google.firebase:firebase-firestore")

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}