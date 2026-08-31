plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Gradle vuole `plugins {}` come prima istruzione dello script, quindi le
// costanti vanno dopo.
val fallbackVersion = "0.5.2"

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

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // I test strumentati girano contro la variante *release*, cioe' esattamente
    // l'APK che viene pubblicato. Provarli sulla debug non servirebbe a niente:
    // i guasti che ci hanno fermato quattro volte - modello assente, modello
    // compresso, MediaPipe rotto da R8 - esistono solo nella release.
    testBuildType = "release"

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
            // R8 resta SPENTO, e non e' una pigrizia: e' la correzione di un
            // guasto vero.
            //
            // Nella 0.4.2 il riconoscimento non partiva con questo errore:
            //
            //   NoClassDefFoundError: com.google.mediapipe.framework.Graph
            //     <- ExceptionInInitializerError
            //     <- IllegalStateException: no caller found on the stack for: E2.d
            //
            // `E2` e' il nome che R8 ha dato a `com.google.common.flogger`, la
            // libreria di log che MediaPipe usa internamente. Flogger ricava il
            // nome della classe chiamante **camminando sullo stack**, e R8 -
            // rinominando, unendo classi e incorporando metodi - fa sparire il
            // fotogramma che flogger sta cercando. L'inizializzatore statico di
            // `Graph` esplode, e con lui tutto MediaPipe.
            //
            // Le regole `-keep` non bastano contro questa classe di guasti: si
            // puo' impedire a R8 di rinominare una classe, non di cambiare la
            // forma dello stack attorno a lei. E per un'app distribuita fuori
            // dagli store non c'e' niente da guadagnare: il dex e' una briciola
            // rispetto ai 40 MB di librerie native e modello.
            //
            // Correttezza prima di due megabyte.
            isMinifyEnabled = false
            isShrinkResources = false
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
    implementation(project(":core:power"))
    implementation(project(":core:health"))
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

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
