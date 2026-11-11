plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
    alias(libs.plugins.ksp)
}

// Firebase Cloud Messaging solo con google-services.json; sin él la app compila y los avisos salen al sincronizar (HU-MOV-11).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Para el túnel local: ./gradlew assembleDebug -PapiBaseUrl=https://xxxx.trycloudflare.com/api/v1/
val apiBaseUrl = (project.findProperty("apiBaseUrl") as String?) ?: "http://10.0.2.2:8000/api/v1/"
// Las pantallas del reporte de siniestro bloquean capturas en release; -PsecureScreens=true lo activa en debug para probarlo (HU-MOV-08).
val secureScreensInDebug = (project.findProperty("secureScreens") as String?) == "true"

android {
    namespace = "com.solventa.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.solventa.app"
        minSdk = 33 // Android 13 (Tiramisu) como versión mínima requerida
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "SECURE_SCREENS", "$secureScreensInDebug")
        }
        release {
            buildConfigField("boolean", "SECURE_SCREENS", "true")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

// La release solo habla HTTPS: sin -PapiBaseUrl=https://… no compila (HU-MOV-07).
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        check(apiBaseUrl.startsWith("https://")) { "La release necesita -PapiBaseUrl=https://…; se recibió $apiBaseUrl" }
    }
}

// La base del reporte de siniestro exporta su esquema: sus datos solo existen en el teléfono (ADR 0021, punto 6; HU-MOV-08).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Las vistas previas (@Preview) solo existen para el editor de Android Studio; no son código de la app.
kover {
    reports {
        filters {
            excludes {
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
            }
        }
    }
}
