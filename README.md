# AirScroll

Motore universale di controllo gestuale per Android. Muovi la mano davanti al
telefono e la pagina scorre, come se ci fosse un dito invisibile appoggiato allo
schermo. Tutto viene elaborato sul dispositivo: nessuna connessione, nessuna
immagine salvata, nessun account.

Serve quando toccare lo schermo è scomodo: cucina, officina, palestra, un PDF
lungo, o semplicemente quando muovere le dita è difficile.

Open source, licenza Apache 2.0. Non è pensato per il Play Store: si compila e
si installa l'APK.

---

## Come funziona

| Stato | Indicatore | Fotocamera | Cosa succede |
|---|---|---|---|
| Spento / inattivo | rosso | **chiusa** | Nessuna app compatibile in primo piano. |
| In attesa | giallo | aperta, ~8 fps | Apri un'app compatibile: per 5–6 secondi AirScroll cerca **solo** il pollice in su. |
| Attivo | verde | aperta, ~22 fps | La mano guida lo scorrimento. |

- **Pollice in su** tenuto ~0,4 s → si attiva.
- **Mano su / giù** → la pagina segue il movimento. Non ci sono scatti: è un
  trascinamento continuo. Più ti allontani dal centro, più accelera.
- **Mano a destra / sinistra** → volume, anch'esso progressivo. Un asse alla
  volta, con isteresi: scorrendo non cambi il volume per sbaglio.
- **Pugno chiuso** per ~2 s → stop. Anche uscire dall'app ferma tutto e chiude
  la fotocamera.
- I micro-tremolii vengono assorbiti da una **zona neutra** ricavata dalla
  calibrazione, non da un numero deciso a tavolino.

### La calibrazione

Alla prima installazione, quattro passaggi da una quindicina di secondi in
tutto, nello spirito di Face ID: invece di chiederti di indovinare dei numeri,
misura i tuoi movimenti veri.

1. **Distanza abituale** → guadagno del profilo automatico.
2. **Mano ferma** → ampiezza del tuo tremolio, cioè la zona neutra.
3. **Su e giù** → quanto è ampio un movimento "pieno" per te.
4. **Destra e sinistra** → la stessa cosa, per il volume.

Ci sono anche tre profili distanza fissi (vicino, medio, lontano) oltre alla
modalità automatica, che stima la distanza dalla dimensione apparente della
mano e adegua il guadagno.

---

## Compilare

Non servono chiavi, account o servizi esterni.

```bash
./gradlew :app:assembleRelease
# APK in app/build/outputs/apk/release/
```

Requisiti: JDK 17 e l'Android SDK (API 35). Android Studio li installa entrambi;
da riga di comando basta che `ANDROID_HOME` punti a un SDK con `platforms;android-35`
e `build-tools`.

Il modello MediaPipe (`gesture_recognizer.task`, ~8 MB) **non è nel repository**:
lo scarica il task Gradle `downloadGestureModel`, che viene eseguito
automaticamente prima di ogni build. Serve rete solo la prima volta.

Senza un keystore configurato la release viene firmata con la chiave di debug,
così l'APK è comunque installabile. Per firmarlo davvero:

```bash
export AIRSCROLL_KEYSTORE=/percorso/airscroll.jks
export AIRSCROLL_KEYSTORE_PASSWORD=...
export AIRSCROLL_KEY_ALIAS=...
export AIRSCROLL_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

### Senza installare niente

Il workflow `.github/workflows/android.yml` compila l'APK a ogni push e lo
allega come artifact della run. Da GitHub: **Actions → Android → l'ultima
run → Artifacts → `airscroll-release-apk`**.

### Installare

1. Copia l'APK sul telefono e installalo (va concessa l'installazione da fonti
   sconosciute).
2. Apri AirScroll e segui l'introduzione: fotocamera, servizio di accessibilità,
   sovrapposizione, notifiche.
3. Fai la calibrazione.
4. Accendi l'interruttore **dalla schermata principale** (vedi sotto: deve
   partire mentre l'app è aperta).
5. Su Xiaomi, Huawei, Samsung e simili: togli AirScroll dalle ottimizzazioni
   batteria, altrimenti il servizio viene chiuso dopo pochi minuti.

---

## Architettura

Modulare per davvero: il motore non sa quali app esistono, e i moduli app non
sanno come funziona il motore.

```
├── app/                    UI Compose, servizi Android, cablaggio dei moduli
├── core/
│   ├── common/             modelli, filtro One Euro, bus in-process
│   ├── settings/           DataStore: preferenze e profilo di calibrazione
│   ├── camera/             CameraX in sola analisi, risoluzione e fps adattivi
│   ├── vision/             MediaPipe Gesture Recognizer (+ download del modello)
│   ├── gesture/            macchina a stati e mappatura movimento → velocità
│   ├── control/            gesti di scorrimento continui, volume
│   ├── overlay/            il pallino di stato
│   └── designsystem/       tema Compose
└── apps/
    ├── api/                AppProfile, ScrollTuning, registro
    ├── browser/            Chrome, Firefox, Samsung Internet, Brave, Edge, Opera
    ├── social/             Instagram, TikTok, YouTube, Reddit, X, Facebook, WhatsApp
    └── reader/             Drive/PDF, Acrobat, Xodo, Kindle, Moon+, Pocket, Keep
```

Due servizi, un solo processo:

- **`VisionForegroundService`** possiede fotocamera, modello e motore. Pubblica
  stato e comandi su `AirScrollBus`.
- **`AirScrollAccessibilityService`** dice quale app è in primo piano ed esegue i
  gesti. Non legge i contenuti dello schermo
  (`canRetrieveWindowContent="false"` nel config XML).

### Aggiungere il supporto a una nuova app

Quattro righe, nessuna modifica al motore:

1. crea `apps/<nome>/` con un oggetto che implementa `AppProfileProvider`;
2. `include(":apps:<nome>")` in `settings.gradle.kts`;
3. la dipendenza in `app/build.gradle.kts`;
4. una riga in `AppProfileBootstrap.install()`.

Ogni profilo può regolare velocità, curva di risposta, verso, punto di appoggio
del dito e se il volume ha senso in quell'app. Chi non vuole toccare il codice
può aggiungere un package a mano dalle impostazioni.

---

## Consumi

- Fotocamera **chiusa** in stato rosso: è il caso più frequente.
- Analisi a 320×240 o 480×360, non alla risoluzione del sensore.
- Cadenza software: ~8 fps in attesa, ~22 in uso. In modalità Batteria si
  scende a 6/15 e solo CPU.
- Delegate GPU quando c'è, con ricaduta automatica su CPU.
- Nessuna anteprima video mentre gira in background: la superficie grafica costa
  più dell'inferenza.
- Il profilo di default viene scelto in base a RAM e core del telefono.

---

## Limiti, detti chiaramente

Sono vincoli di Android o del riconoscimento visivo, non cose "da sistemare
dopo".

**La notifica persistente non si può togliere.** Da Android 9 la fotocamera è
vietata ai processi in background. L'unico modo legittimo di usarla mentre sei
dentro un'altra app è un *foreground service* di tipo `camera`, che per
definizione mostra una notifica. È la condizione che il sistema impone per
garantire che nessuno riprenda di nascosto. Per lo stesso motivo il servizio
**va acceso dalla schermata dell'app**: se partisse in background, da Android 11
in poi il sistema gli negherebbe la fotocamera.

**Serve il servizio di accessibilità.** È l'unica API con cui un'app può
scorrere dentro un'altra app. Android mostrerà un avviso severo quando lo
attivi: è normale, e vale la pena leggerlo. AirScroll chiede il minimo — può
eseguire gesti, non può leggere lo schermo.

**Non funziona ovunque.** Il sistema blocca i gesti sintetici sulle schermate di
sistema (impostazioni di sicurezza, richieste di permessi, alcune schermate
bancarie). Nelle app che rifiutano l'accessibilità non succede niente.

**Il riconoscimento ha bisogno di luce.** Al buio o in controluce forte il
modello perde la mano. Non c'è un modo software per aggirarlo.

**I costruttori uccidono i servizi in background.** Su molti telefoni cinesi
bisogna disattivare a mano le ottimizzazioni batteria, altrimenti AirScroll
smette di funzionare dopo qualche minuto senza dire niente.

**Il consumo non è zero.** Con la fotocamera aperta si consuma: da qui il
disegno a tre stati, che tiene il sensore spento la maggior parte del tempo. In
uso continuo aspettati un consumo confrontabile con una videochiamata a bassa
risoluzione.

**Niente riavvio automatico.** Dopo un riavvio del telefono AirScroll va
riacceso a mano. È una scelta: un'app che si riavvia da sola e apre la
fotocamera è esattamente ciò che nessuno vuole.

**Non è ancora stato provato su un telefono vero.** Il codice è completo e i
test unitari del motore passano, ma le costanti (soglie, tempi, velocità) sono
scelte ragionate, non misurate sul campo. Aspettati di dover ritoccare
sensibilità e zona neutra dalle impostazioni la prima volta.

---

## Privacy

- I fotogrammi restano in memoria per il tempo di un'inferenza e non vengono mai
  scritti su disco né inviati da nessuna parte.
- L'app non dichiara il permesso `INTERNET`.
- Il servizio di accessibilità è configurato per **non** poter leggere i
  contenuti delle finestre: riceve solo il nome del package in primo piano.
- Le uniche cose salvate sono le tue preferenze e quattro numeri di
  calibrazione, in locale.

---

## English summary

AirScroll is an offline, on-device hand-gesture engine for Android. Thumbs up to
start, move your hand to scroll like an invisible finger (progressive speed, dead
zone from a Face-ID-style calibration), sideways for volume, closed fist for two
seconds to stop. Camera is closed unless a supported app is in the foreground.

Build with `./gradlew :app:assembleRelease` (JDK 17 + Android SDK 35), or grab
the APK from the GitHub Actions artifacts. Adding support for a new app means
creating a module under `apps/` and registering it in one line — the engine
itself never changes.

Read the *Limiti* section above before filing issues: the persistent
notification, the accessibility service requirement and the manual start are
Android platform constraints, not oversights.
