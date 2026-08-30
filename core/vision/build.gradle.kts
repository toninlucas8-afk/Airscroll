import java.io.File
import java.net.URI

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.airscroll.core.vision"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    // Il modello MediaPipe NON e' committato nel repository: viene scaricato in fase
    // di build dentro questa cartella, che qui viene registrata come sorgente assets.
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/mediapipeAssets"))

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        // I .task sono gia' compressi: comprimerli di nuovo rallenta il caricamento a runtime.
        resources.excludes += "META-INF/{AL2.0,LGPL2.1}"
    }

    androidResources {
        noCompress += "task"
    }
}

/**
 * Scarica il modello `gesture_recognizer.task` di MediaPipe.
 *
 * E' un file binario di ~8 MB: tenerlo fuori da git mantiene il repository leggero
 * e permette di aggiornare il modello cambiando solo l'URL qui sotto.
 */
abstract class DownloadModelTask : DefaultTask() {

    @get:Input
    abstract val sourceUrl: Property<String>

    @get:Input
    abstract val minimumBytes: Property<Long>

    @get:OutputFile
    abstract val destination: RegularFileProperty

    @TaskAction
    fun download() {
        val target = destination.get().asFile
        target.parentFile.mkdirs()
        val temp = File(target.parentFile, target.name + ".part")
        logger.lifecycle("AirScroll: scarico il modello MediaPipe da ${sourceUrl.get()}")
        URI(sourceUrl.get()).toURL().openStream().use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        val size = temp.length()
        if (size < minimumBytes.get()) {
            temp.delete()
            throw GradleException(
                "Download del modello incompleto: $size byte ricevuti, attesi almeno ${minimumBytes.get()}."
            )
        }
        if (target.exists()) target.delete()
        temp.renameTo(target)
        logger.lifecycle("AirScroll: modello pronto (${size / 1024} KB) in $target")
    }
}

val downloadGestureModel = tasks.register<DownloadModelTask>("downloadGestureModel") {
    description = "Scarica il modello MediaPipe Gesture Recognizer usato da AirScroll."
    group = "airscroll"
    sourceUrl.set(
        "https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/1/gesture_recognizer.task"
    )
    minimumBytes.set(1_000_000L)
    destination.set(layout.buildDirectory.file("generated/mediapipeAssets/gesture_recognizer.task"))
}

// Il modello va scaricato prima di *qualunque* cosa in questo modulo.
//
// La versione precedente agganciava solo i task chiamati `merge*Assets`, che
// pero' esistono nel modulo applicazione: in una libreria Android il task che
// raccoglie gli asset si chiama `packageReleaseAssets`. Risultato: il download
// non partiva mai, la cartella restava vuota e l'APK usciva senza modello,
// senza che niente fallisse. `preBuild` non lascia scampatoie.
tasks.named("preBuild") {
    dependsOn(downloadGestureModel)
}

tasks.configureEach {
    if (name.endsWith("Assets") && (name.startsWith("merge") || name.startsWith("package"))) {
        dependsOn(downloadGestureModel)
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:settings"))
    implementation(libs.androidx.core.ktx)
    api(libs.mediapipe.tasks.vision)
}
