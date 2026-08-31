package dev.airscroll.app.health

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dev.airscroll.app.MainActivity
import dev.airscroll.app.R
import dev.airscroll.core.health.Problem
import dev.airscroll.core.health.Severity

/**
 * La notifica che dice cosa si e' rotto.
 *
 * Sta su un canale **suo**, separato da quello del servizio, per un motivo
 * pratico: la notifica di stato e' silenziosa e permanente, e chi vuole puo'
 * silenziarla del tutto senza sapere che cosi' si silenzia anche l'unico modo
 * che AirScroll ha di dire "mi sono fermato". Due canali, due scelte diverse.
 *
 * Si aggiorna solo quando il problema **cambia**: ripubblicare lo stesso avviso
 * ogni due secondi lo trasformerebbe in rumore da ignorare.
 */
class ProblemNotifier(context: Context) {

    private val appContext = context.applicationContext
    private var shown: Problem? = null

    init {
        createChannel()
    }

    /**
     * Mostra il problema, o toglie l'avviso se non c'e' piu' niente da dire.
     *
     * Chiamabile a ogni giro senza pensarci: se niente e' cambiato non fa
     * niente.
     */
    fun update(problem: Problem?) {
        if (problem == shown) return
        shown = problem
        if (problem == null) {
            cancel()
        } else {
            post(problem)
        }
    }

    /** Toglie l'avviso e dimentica: si usa quando il servizio si spegne. */
    fun clear() {
        shown = null
        cancel()
    }

    private fun post(problem: Problem) {
        val manager = appContext.getSystemService<NotificationManager>() ?: return
        runCatching { manager.notify(NOTIFICATION_ID, build(problem)) }
    }

    private fun cancel() {
        appContext.getSystemService<NotificationManager>()?.cancel(NOTIFICATION_ID)
    }

    private fun build(problem: Problem): Notification {
        val title = appContext.getString(problemTitle(problem))
        val body = appContext.getString(problemBody(problem))

        val openApp = PendingIntent.getActivity(
            appContext,
            REQUEST_OPEN,
            Intent(appContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle(title)
            .setContentText(body)
            // Il testo lungo va per esteso: e' li' che c'e' la spiegazione, e
            // una notifica troncata a meta' frase non spiega niente.
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(priorityOf(problem))

        // Il pulsante che porta dove si risolve. Quando non esiste una
        // schermata che risolve, non si mette un pulsante finto.
        remedy(appContext, problem)?.let { fix ->
            val pending = PendingIntent.getActivity(
                appContext,
                REQUEST_REMEDY,
                fix.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, appContext.getString(fix.label), pending)
        }

        return builder.build()
    }

    /**
     * Un guasto che blocca tutto suona; gli altri no.
     *
     * E' la differenza fra un avviso che vale la pena leggere subito e uno che
     * puo' aspettare la prossima occhiata al telefono.
     */
    private fun priorityOf(problem: Problem): Int = when (problem.severity) {
        Severity.BLOCKING -> NotificationCompat.PRIORITY_HIGH
        Severity.DEGRADED, Severity.WARNING -> NotificationCompat.PRIORITY_DEFAULT
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.problem_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = appContext.getString(R.string.problem_channel_description)
            setShowBadge(true)
        }
        appContext.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "airscroll_problemi"
        const val NOTIFICATION_ID = 1002
        const val REQUEST_OPEN = 10
        const val REQUEST_REMEDY = 11
    }
}
