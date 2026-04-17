plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.bunnycare"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.bunnycare"
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

    buildFeatures {
        viewBinding = true
    }

    aaptOptions {
        noCompress.addAll(listOf("tflite"))
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    implementation("org.tensorflow:tensorflow-lite-task-vision:0.3.0")
    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.0")

    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.legacy.support.v4)

    implementation("org.mapsforge:mapsforge-core:0.23.0")
    implementation("org.mapsforge:mapsforge-map:0.23.0")
    implementation("org.mapsforge:mapsforge-map-reader:0.23.0")
    implementation("org.mapsforge:mapsforge-themes:0.23.0")
    implementation("org.mapsforge:mapsforge-map-android:0.23.0")
    implementation("org.mapsforge:mapsforge-poi:0.23.0")
    implementation("org.mapsforge:mapsforge-poi-android:0.23.0")

    implementation("com.caverock:androidsvg:1.4")
    implementation("com.google.android.gms:play-services-location:17.0.0")
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.cloudinary:cloudinary-android:3.0.2")

    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.firebase:firebase-storage:20.3.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}