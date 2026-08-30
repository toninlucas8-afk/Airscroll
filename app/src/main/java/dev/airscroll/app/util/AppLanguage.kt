package dev.airscroll.app.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Le lingue dell'app.
 *
 * Passa da `AppCompatDelegate` invece che dalle impostazioni interne perche'
 * cosi' la scelta e' quella *di sistema*: su Android 13+ compare anche in
 * Impostazioni -> App -> AirScroll -> Lingua, e su versioni precedenti
 * appcompat la ricorda da solo. Un'app che si tiene la lingua in un file suo
 * finisce sempre per litigare con quella del telefono.
 *
 * L'etichetta di ogni lingua e' scritta *nella lingua stessa*: chi cerca il
 * tedesco cerca "Deutsch", non "Tedesco".
 */
enum class AppLanguage(val tag: String, val label: String) {
    SYSTEM("", "Sistema"),
    ITALIAN("it", "Italiano"),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    GERMAN("de", "Deutsch"),
    CHINESE("zh-CN", "中文"),
    ;

    companion object {
        /** Tag attualmente in uso, stringa vuota se si segue il sistema. */
        fun current(): String =
            AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',')

        fun apply(language: AppLanguage) {
            val locales = if (language.tag.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language.tag)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
