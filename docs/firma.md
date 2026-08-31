# La chiave di firma, una volta sola

## Perché serve

Android considera due APK firmati con chiavi diverse **due app diverse**, anche
se hanno lo stesso nome. Non le aggiorna una sull'altra: le rifiuta.

Finché nel repository non ci sono i segreti di firma, ogni build viene firmata
con la **chiave di debug**, che GitHub genera da capo su ogni macchina. Quindi
ogni versione ha una chiave diversa, e per installarla bisogna disinstallare la
precedente — perdendo **calibrazione, impostazioni e tutto il resto**.

Chi ha usato AirScroll dalla 0.4.3 in poi ha rifatto la calibrazione a ogni
aggiornamento senza che fosse necessario. Si sistema una volta e non si torna
più sull'argomento.

## Cosa fare

### 1. Crea la chiave

Su un computer con Java installato:

```bash
keytool -genkeypair -v \
  -keystore airscroll.jks \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias airscroll
```

Ti chiederà una password (due volte: per il file e per la chiave — puoi usare
la stessa) e qualche dato anagrafico, che per un'app distribuita fuori dagli
store non ha nessuna importanza: nome e paese bastano.

### 2. Conserva il file

> **`airscroll.jks` non si può ricreare.** Se lo perdi, non potrai mai più
> pubblicare un aggiornamento che si installi sopra le versioni esistenti:
> chiunque abbia l'app dovrà disinstallarla. Tienilo in almeno due posti, e
> segna la password insieme al file.

Il file **non va messo nel repository**: chi ce l'ha può firmare pacchetti che
si presentano come AirScroll.

### 3. Mettilo nei segreti di GitHub

Trasformalo in testo:

```bash
base64 -w0 airscroll.jks   # su macOS: base64 -i airscroll.jks
```

Poi vai su **GitHub → il repository → Settings → Secrets and variables →
Actions → New repository secret** e creane quattro:

| Nome del segreto | Cosa metterci |
|---|---|
| `AIRSCROLL_KEYSTORE_BASE64` | tutto l'output del comando qui sopra |
| `AIRSCROLL_KEYSTORE_PASSWORD` | la password del file |
| `AIRSCROLL_KEY_ALIAS` | `airscroll` |
| `AIRSCROLL_KEY_PASSWORD` | la password della chiave |

### 4. Pubblica una versione

Actions → Release → *Run workflow*. Se i segreti sono a posto, dalle note di
versione **sparisce l'avviso** sulla chiave non stabile: è il modo per
verificare che abbia funzionato, senza dover credere a nessuno.

## L'ultimo passaggio doloroso

La prima versione firmata con la chiave nuova va comunque installata dopo aver
disinstallato quella vecchia — le chiavi sono diverse, e non c'è modo di
aggirarlo. **È l'ultima volta:** da lì in poi gli aggiornamenti si installano
sopra e i tuoi dati restano dove sono.

Se prima di quel passaggio vuoi salvare la calibrazione, dalla 0.8.0 c'è
`Impostazioni → Il tuo profilo → Esporta`: esporta il file, disinstalla,
installa la versione firmata e reimporta.
