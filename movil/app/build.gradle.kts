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
// Firma de release desde el entorno; la llave solo vive en los secretos de GitHub (HU-MOV-09, SR-06). Sin las
// cuatro variables la release sale sin firmar y la depuración no cambia.
val releaseKeystore = listOf("ANDROID_KEYSTORE_FILE", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")
    .associateWith { System.getenv(it).orEmpty() }
    .takeIf { env -> env.values.all { it.isNotBlank() } }

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

    releaseKeystore?.let { env ->
        signingConfigs {
            create("release") {
                storeFile = file(env.getValue("ANDROID_KEYSTORE_FILE"))
                storePassword = env.getValue("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = env.getValue("ANDROID_KEY_ALIAS")
                keyPassword = env.getValue("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "SECURE_SCREENS", "$secureScreensInDebug")
            buildConfigField("boolean", "REGISTRATION_IN_APP", "true")
        }
        release {
            buildConfigField("boolean", "SECURE_SCREENS", "true")
            // En AWS el backend no registra desde la app (REGISTRATION_ENABLED apagado).
            buildConfigField("boolean", "REGISTRATION_IN_APP", "false")
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.mlkit.document.scanner)
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
