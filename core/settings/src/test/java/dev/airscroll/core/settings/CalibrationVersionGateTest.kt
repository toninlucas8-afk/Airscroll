package dev.airscroll.core.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationVersionGateTest {

    @Test
    fun `una versione nuova azzera la calibrazione`() {
        assertTrue(
            CalibrationVersionGate.shouldReset(
                calibratedVersion = "0.8.0",
                currentVersion = "0.9.0",
                calibrationCompleted = true,
                enabled = true,
            )
        )
    }

    @Test
    fun `la stessa versione non tocca niente`() {
        // Conta tantissimo: l'app parte ogni volta che si apre, e se il
        // cancello scattasse sulla stessa versione la calibrazione durerebbe
        // fino alla prossima apertura.
        assertFalse(
            CalibrationVersionGate.shouldReset(
                calibratedVersion = "0.9.0",
                currentVersion = "0.9.0",
                calibrationCompleted = true,
                enabled = true,
            )
        )
    }

    @Test
    fun `anche tornare indietro conta come cambio`() {
        // Installare la 0.8.0 sopra la 0.9.0 e' un cambio di versione come un
        // altro, e i numeri della 0.9.0 dentro il motore della 0.8.0 sono
        // esattamente il problema che questo serve a evitare.
        assertTrue(
            CalibrationVersionGate.shouldReset(
                calibratedVersion = "0.9.0",
                currentVersion = "0.8.0",
                calibrationCompleted = true,
                enabled = true,
            )
        )
    }

    @Test
    fun `spento non azzera mai`() {
        assertFalse(
            CalibrationVersionGate.shouldReset(
                calibratedVersion = "0.8.0",
                currentVersion = "0.9.0",
                calibrationCompleted = true,
                enabled = false,
            )
        )
    }

    @Test
    fun `senza sapere che versione gira non si cancella`() {
        assertFalse(
            CalibrationVersionGate.shouldReset(
                calibratedVersion = "0.8.0",
                currentVersion = "",
                calibrationCompleted = true,
                enabled = true,
            )
        )
    }

    @Test
    fun `alla prima installazione non c'e' niente da azzerare`() {
        // Senza questo, chi installa AirScroll per la prima volta si vedrebbe
        // annunciare che un aggiornamento gli ha azzerato una calibrazione che
        // non ha mai fatto.
        assertFalse(
            CalibrationVersionGate.shouldReset(
                calibratedVersion = "",
                currentVersion = "0.9.0",
                calibrationCompleted = false,
                enabled = true,
            )
        )
    }

    @Test
    fun `una calibrazione senza versione viene da prima di questo controllo`() {
        // Chi aveva gia' calibrato con la 0.8.0 non ha nessuna versione
        // scritta: sono esattamente i numeri vecchi dentro il motore nuovo che
        // questo serve a togliere di mezzo.
        assertTrue(
            CalibrationVersionGate.shouldReset(
                calibratedVersion = "",
                currentVersion = "0.9.0",
                calibrationCompleted = true,
                enabled = true,
            )
        )
    }

    @Test
    fun `dopo l'azzeramento non riscatta al riavvio successivo`() {
        // L'app parte a ogni apertura. Se questo giro non chiudesse, la
        // calibrazione appena rifatta durerebbe fino alla prossima apertura, e
        // la cosa si noterebbe solo su un telefono vero.
        assertFalse(
            CalibrationVersionGate.shouldReset(
                calibratedVersion = "",
                currentVersion = "0.9.0",
                calibrationCompleted = false,
                enabled = true,
            )
        )
    }

    @Test
    fun `un profilo importato non viene azzerato al primo riavvio`() {
        // L'import marca il profilo con la versione che sta girando adesso -
        // vedi SettingsRepository.applyProfile. Chi importa ha deciso in quel
        // momento: non e' un aggiornamento che gli succede addosso.
        assertFalse(
            CalibrationVersionGate.shouldReset(
                calibratedVersion = "0.9.0",
                currentVersion = "0.9.0",
                calibrationCompleted = true,
                enabled = true,
            )
        )
    }
}
