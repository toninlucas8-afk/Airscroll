package dev.airscroll.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.airscroll.app.R
import dev.airscroll.app.health.problemBody
import dev.airscroll.app.health.problemTitle
import dev.airscroll.app.health.remedy
import dev.airscroll.core.health.Problem
import dev.airscroll.core.health.Severity

/**
 * Il guasto, detto dentro l'app.
 *
 * La notifica raggiunge chi non ha AirScroll aperto; questo riquadro raggiunge
 * chi l'ha aperto proprio perche' "non funziona". Sono lo stesso testo di
 * proposito: due spiegazioni diverse dello stesso guasto sarebbero due
 * occasioni di contraddirsi.
 */
@Composable
fun ProblemCard(
    problem: Problem,
    onDismissWarning: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bloccante = problem.severity == Severity.BLOCKING

    // Rosso solo quando non funziona niente. Se ogni avviso fosse rosso, il
    // rosso smetterebbe di voler dire qualcosa.
    val contenitore = if (bloccante) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val inchiostro = if (bloccante) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bordo = if (bloccante) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = contenitore),
        border = BorderStroke(1.dp, bordo),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (bloccante) Icons.Filled.ErrorOutline else Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = inchiostro,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(problemTitle(problem)),
                    style = MaterialTheme.typography.titleMedium,
                    color = inchiostro,
                )
            }

            Text(
                text = stringResource(problemBody(problem)),
                style = MaterialTheme.typography.bodyMedium,
                color = inchiostro,
            )

            remedy(context, problem)?.let { fix ->
                Button(
                    onClick = { runCatching { context.startActivity(fix.intent) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(fix.label))
                }
            }

            // Solo per l'avviso sulla batteria: chi ha appena tolto la
            // restrizione deve poter dire "fatto". Se non fosse vero, il
            // sistema chiudera' di nuovo il servizio e l'avviso tornera' da
            // solo - il che e' esattamente il modo giusto di verificarlo.
            if (onDismissWarning != null) {
                TextButton(
                    onClick = onDismissWarning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.problem_action_done))
                }
            }
        }
    }
}
