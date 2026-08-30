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

**[Scarica l'ultima release](https://github.com/toninlucas8-afk/Airscroll/releases/latest)**
e apri il file `.apk` sul telefono. E' la via piu' semplice: link diretto,
niente zip, niente login.

Per pubblicarne una nuova ci sono due strade, entrambe gestite da
`.github/workflows/release.yml`, che esegue i test, compila e allega l'APK con
il suo checksum SHA-256:

- **dal browser, anche da telefono**: Actions -> Release -> *Run workflow*,
  scrivi la versione (es. `v0.2.0`) e conferma. Il tag lo crea la
  pubblicazione stessa.
- **da terminale**: `git tag v0.2.0 && git push origin v0.2.0`.

In alternativa, `.github/workflows/android.yml` compila a ogni push e allega
l'APK come artifact della run (**Actions → Android → l'ultima run → Artifacts**).
Gli artifact pero' sono zip e richiedono di essere loggati: da telefono la
release e' molto piu' comoda.

### Installare

1. Apri l'APK sul telefono e installalo (va concessa l'installazione da fonti
   sconosciute).
2. Apri AirScroll e segui l'introduzione: fotocamera, servizio di accessibilità,
   sovrapposizione, notifiche.
3. Fai la calibrazione.
4. Accendi l'interruttore **dalla schermata principale** (vedi sotto: deve
   partire mentre l'app è aperta).
5. Su Xiaomi, Huawei, Samsung e simili: togli AirScroll dalle ottimizzazioni
   batteria, altrimenti il servizio viene chiuso dopo pochi minuti.

### Android dirà che l'app è pericolosa

Succede, ed è previsto. AirScroll chiede la fotocamera **insieme** al servizio
di accessibilità: è la stessa combinazione che usano gli stalkerware, quindi
Android la tratta con sospetto a prescindere da cosa faccia davvero l'app. Le
manifestazioni sono due, distinte.

**Play Protect: "app dannosa".** Compare durante l'installazione. È un giudizio
automatico basato sui permessi richiesti e sul fatto che l'app non arriva da uno
store, non su un'analisi del codice. Scegli *Installa comunque*.

**"Impostazione bloccata" sull'accessibilità (Android 13+).** Il servizio
risulta grigio e non attivabile. Android blocca i permessi sensibili per tutto
ciò che non arriva da uno store. Si sblocca una volta sola: Impostazioni → App →
AirScroll → **tre puntini** in alto a destra → *Consenti impostazioni con
restrizioni*. L'app te lo spiega al primo avvio, con un pulsante che porta
dritto a quella schermata.

**Perché puoi verificare invece di fidarti.** AirScroll non dichiara il permesso
`INTERNET`: non è una promessa, è un fatto controllabile in
[`AndroidManifest.xml`](app/src/main/AndroidManifest.xml), dove l'elenco dei
permessi è di dodici righe. Il servizio di accessibilità è configurato con
`canRetrieveWindowContent="false"` in
[`accessibility_service_config.xml`](app/src/main/res/xml/accessibility_service_config.xml),
cioè non può leggere i contenuti dello schermo nemmeno volendo. E l'APK delle
release lo compila GitHub Actions da questo codice, non una macchina privata.

Firmare l'APK con una chiave stabile riduce un po' gli allarmi e permette gli
aggiornamenti in place. Se vuoi farlo, crea un keystore e mettilo nei secret del
repository come `AIRSCROLL_KEYSTORE_BASE64` (più `AIRSCROLL_KEYSTORE_PASSWORD`,
`AIRSCROLL_KEY_ALIAS`, `AIRSCROLL_KEY_PASSWORD`): il workflow di release lo usa
da solo.

```bash
keytool -genkeypair -v -keystore airscroll.jks -alias airscroll \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 airscroll.jks   # il valore da incollare nel secret
```

Non aspettarti che l'avviso sparisca del tutto: senza una distribuzione via
store, per un'app con questi permessi, non c'è modo di evitarlo.

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

## Se il riconoscimento non parte

Nel laboratorio, nella palestra e in calibrazione, quando MediaPipe non riesce
ad avviarsi compare un riquadro rosso con un blocco di dettagli tecnici e un
pulsante **Copia i dettagli**. Quel blocco e' la segnalazione: dice quale dei
tre punti si e' rotto, senza doverlo indovinare.

```
--- AirScroll: diagnosi riconoscimento ---
modello        : OK, 8373440 byte
libreria nativa: NON caricata
libreria (det.): UnsatisfiedLinkError: dlopen failed: ... is not 16 KB aligned
pagina memoria : 16384 byte
ABI            : arm64-v8a
Android        : 16 (API 36)
dispositivo    : ...
```

Tre cose possono rompersi, e sono distinguibili:

- **il modello non c'e'** o **e' compresso** nell'APK: e' un errore di
  confezionamento, `tools/verify_apk.py` lo intercetta prima della pubblicazione;
- **la libreria nativa non si carica**: quasi sempre l'incompatibilita' fra una
  libreria allineata a 4 KB e un telefono con pagine di memoria da 16 KB.
  Riguarda i telefoni Android usciti dal 2025 e si risolve solo aggiornando
  MediaPipe (da 0.10.26 in avanti). `.github/workflows/probe-mediapipe.yml`
  verifica l'allineamento leggendo l'intestazione ELF degli AAR pubblicati,
  perche' il changelog su questo non e' affidabile.

Le prime due versioni pubblicate sono state inservibili proprio per guasti di
questo tipo, invisibili a compilazione e a installazione. Da qui la regola:
ogni APK viene aperto e ispezionato prima di essere pubblicato, e l'app non
tira mai a indovinare la causa di un fallimento.

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
