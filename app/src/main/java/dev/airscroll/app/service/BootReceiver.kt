package dev.airscroll.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.airscroll.app.bootstrap.ServiceLocator
import dev.airscroll.app.health.ProblemNotifier
import dev.airscroll.core.health.Problem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Si sveglia dopo il riavvio del telefono, e non accende niente.
 *
 * Questo file esiste per **non** fare la cosa che di solito fa un ricevitore
 * d'avvio. AirScroll non riparte da solo, ed e' una scelta: un'app che si
 * riavvia da sola e apre la fotocamera e' esattamente cio' che nessuno vuole,
 * ed e' anche il motivo per cui Android tratta questa combinazione di permessi
 * con sospetto.
 *
 * Ma restare in silenzio non era una scelta, era una dimenticanza: chi aveva
 * AirScroll acceso lo ritrovava spento, senza che niente glielo dicesse, e nel
 * frattempo la diagnosi dava la colpa alle ottimizzazioni della batteria - che
 * non c'entrano niente.
 *
 * Quindi qui si fanno due sole cose, entrambe passive: si annota che c'e' stato
 * un riavvio (cosi' la diagnosi racconta la causa giusta) e si mostra una
 * notifica con un pulsante per riaccendere. La fotocamera resta chiusa finche'
 * non e' l'utente a decidere.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in AZIONI) return

        val appContext = context.applicationContext
        val repository = ServiceLocator.settings(appContext)
        // Un BroadcastReceiver ha pochi secondi di vita e nessuno scope:
        // `goAsync` tiene in piedi il processo finche' la scrittura non e'
        // finita, altrimenti il segno del riavvio andrebbe perso proprio nel
        // caso in cui serve.
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = repository.settings.first()
                // Se AirScroll era gia' spento non c'e' niente da raccontare.
                if (!settings.serviceEnabled) return@launch
                repository.setRebooted(true)
                ProblemNotifier(appContext).update(Problem.AFTER_REBOOT)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val AZIONI = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            // Alcuni telefoni con doppio profilo mandano solo questa.
            "android.intent.action.QUICKBOOT_POWERON",
        )
    }
}
