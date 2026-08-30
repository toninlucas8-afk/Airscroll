plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Gradle vuole `plugins {}` come prima istruzione dello script, quindi le
// costanti vanno dopo.
val fallbackVersion = "0.4.2"

android {
    namespace = "dev.airscroll.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.airscroll.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // La versione arriva dal tag della release (`-PairscrollVersionName=0.3.1`).
        // Prima era inchiodata a 0.1.0, quindi ogni APK pubblicato dichiarava la
        // stessa versione e le info dell'app mentivano.
        val declaredVersion = providers.gradleProperty("airscrollVersionName").orNull
            ?.takeIf { it.isNotBlank() }
            ?: fallbackVersion
        val parts = declaredVersion.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        versionCode = (parts.getOrElse(0) { 0 } * 10_000) +
            (parts.getOrElse(1) { 0 } * 100) +
            parts.getOrElse(2) { 0 }
        versionName = declaredVersion

        vectorDrawables.useSupportLibrary = true
    }

    // Firma opzionale: se le variabili d'ambiente non ci sono, la release viene
    // firmata con la chiave di debug. Serve solo a poter installare l'APK preso
    // dalle GitHub Actions senza dover configurare un keystore.
    val keystorePath = providers.environmentVariable("AIRSCROLL_KEYSTORE").orNull
        ?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("AIRSCROLL_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("AIRSCROLL_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("AIRSCROLL_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }

    androidResources {
        // Il modello MediaPipe deve restare NON compresso dentro l'APK.
        //
        // `setModelAssetPath` lo apre con `AssetManager.openFd()`, che funziona
        // solo su asset memorizzati senza compressione: su uno compresso lancia
        // un'eccezione e il riconoscitore non parte affatto.
        //
        // La stessa riga esiste in :core:vision, ma non serviva a niente:
        // l'impacchettamento dell'APK avviene qui, e l'impostazione di un
        // modulo libreria non si propaga al modulo applicazione.
        noCompress += "task"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:settings"))
    implementation(project(":core:camera"))
    implementation(project(":core:vision"))
    implementation(project(":core:gesture"))
    implementation(project(":core:control"))
    implementation(project(":core:overlay"))
    implementation(project(":core:designsystem"))

    // --- Profili applicazione ---------------------------------------------
    // Per supportare una nuova app: crea un modulo in `apps/`, aggiungilo a
    // settings.gradle.kts, elencalo qui e registralo in AppProfileBootstrap.
    implementation(project(":apps:api"))
    implementation(project(":apps:browser"))
    implementation(project(":apps:social"))
    implementation(project(":apps:reader"))
    // ----------------------------------------------------------------------

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
