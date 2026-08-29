package dev.airscroll.apps.reader

import dev.airscroll.apps.api.AppCategory
import dev.airscroll.apps.api.AppProfile
import dev.airscroll.apps.api.AppProfileProvider
import dev.airscroll.apps.api.ScrollTuning

/**
 * Lettura: PDF, e-book, ricette, documenti.
 *
 * E' il caso d'uso in cui le mani sono davvero occupate (cucina, officina,
 * palestra). Qui serve precisione, non velocita': curva piu' morbida, velocita'
 * massima ridotta, zona neutra effettivamente ampia (gestita dal motore).
 */
object ReaderProfiles : AppProfileProvider {

    override val providerId: String = "reader"

    private val readingTuning = ScrollTuning(
        speedMultiplier = 0.7f,
        curveGamma = 2.1f,
        gripFractionX = 0.5f,
        gripFractionY = 0.5f,
        volumeEnabled = false,
    )

    override val profiles: List<AppProfile> = listOf(
        AppProfile(
            id = "reader.drive.pdf",
            displayName = "Google Drive / PDF Viewer",
            packageNames = setOf("com.google.android.apps.docs", "com.google.android.apps.pdfviewer"),
            category = AppCategory.READER,
            tuning = readingTuning,
        ),
        AppProfile(
            id = "reader.adobe",
            displayName = "Adobe Acrobat",
            packageNames = setOf("com.adobe.reader"),
            category = AppCategory.READER,
            tuning = readingTuning,
        ),
        AppProfile(
            id = "reader.xodo",
            displayName = "Xodo PDF",
            packageNames = setOf("com.xodo.pdf.reader"),
            category = AppCategory.READER,
            tuning = readingTuning,
        ),
        AppProfile(
            id = "reader.kindle",
            displayName = "Kindle",
            packageNames = setOf("com.amazon.kindle"),
            category = AppCategory.READER,
            tuning = readingTuning,
        ),
        AppProfile(
            id = "reader.moon",
            displayName = "Moon+ Reader",
            packageNames = setOf("com.flyersoft.moonreader", "com.flyersoft.moonreaderp"),
            category = AppCategory.READER,
            tuning = readingTuning,
        ),
        AppProfile(
            id = "reader.pocket",
            displayName = "Pocket",
            packageNames = setOf("com.ideashower.readitlater.pro"),
            category = AppCategory.READER,
            tuning = readingTuning,
        ),
        AppProfile(
            id = "reader.keep",
            displayName = "Google Keep",
            packageNames = setOf("com.google.android.keep"),
            category = AppCategory.READER,
            tuning = readingTuning,
        ),
    )
}
