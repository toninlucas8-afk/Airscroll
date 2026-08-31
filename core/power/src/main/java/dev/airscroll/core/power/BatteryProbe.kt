package dev.airscroll.core.power

import android.content.Context
import android.os.BatteryManager
import android.util.Log

/**
 * Legge la corrente assorbita dal telefono, adesso.
 *
 * Serve a smettere di parlare di consumo per sentito dire. Fino alla 0.5.1
 * ogni affermazione sulla batteria in questo progetto era un ragionamento: la
 * fotocamera costa piu' dell'inferenza, l'attesa costa meno dell'attivo,
 * abbassare i fotogrammi aiuta. Probabilmente vero, mai misurato.
 *
 * Il sensore non c'e' su tutti i telefoni e non e' preciso allo stesso modo
 * ovunque: per questo il risultato utile non e' mai un valore assoluto, ma la
 * **differenza** fra due fasi misurate a pochi secondi di distanza, sullo
 * stesso telefono, con lo schermo acceso allo stesso modo.
 */
class BatteryProbe(context: Context) {

    private val manager = context.applicationContext
        .getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    /** true se questo telefono sa dire quanta corrente sta assorbendo. */
    val isSupported: Boolean by lazy {
        val value = readMicroAmps()
        value != null && value != 0L
    }

    /** Corrente istantanea in microampere, oppure null se il telefono non la espone. */
    fun readMicroAmps(): Long? {
        val battery = manager ?: return null
        return runCatching {
            battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrElse { error ->
            Log.w(TAG, "Corrente non leggibile", error)
            null
        }.takeIf { it != Long.MIN_VALUE }
    }

    fun sample(): PowerSample? = readMicroAmps()?.let(::PowerSample)

    /**
     * Capacita' stimata della batteria in mAh, per la stima delle ore.
     *
     * Si ricava dalla carica residua e dalla percentuale: il valore di targa
     * non e' esposto alle app, e questa approssimazione basta per un ordine di
     * grandezza.
     */
    fun estimatedCapacityMilliAmpHours(): Int {
        val battery = manager ?: return 0
        val residualMicroAmpHours = runCatching {
            battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        }.getOrDefault(0L)
        val percent = runCatching {
            battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrDefault(0)
        if (residualMicroAmpHours <= 0L || percent <= 0) return 0
        return ((residualMicroAmpHours / 1000.0) * (100.0 / percent)).toInt()
    }

    /**
     * true se il telefono e' sotto carica.
     *
     * Misurare mentre e' in carica non ha senso: la corrente entra invece di
     * uscire, e il numero non dice piu' niente sul consumo dell'app.
     */
    fun isCharging(): Boolean {
        val battery = manager ?: return false
        return runCatching {
            val status = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "AirScroll/Power"
    }
}
