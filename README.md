# NutriForma

**IT** · App Android per la dieta, **100% in italiano**, **solo offline**: nessun login, nessun server, nessun permesso INTERNET. Un solo profilo, pensato per una persona molto sportiva.
**EN** · Android diet app, fully in Italian and fully offline (no login, no backend, no INTERNET permission). Single profile, tuned for a very sporty person.

Versione corrente / current version: **1.2.0** (`versionCode 4`).

## Cosa fa / What it does

| Scheda / Tab | IT | EN |
|---|---|---|
| **Dieta** | Settimana generata secondo le regole, cambio pasto con alternative valide, turno lungo, pasto libero, riepilogo delle regole, kcal e macro per pasto/giorno, spunta "Completato" e complimenti quando la giornata o la settimana vanno bene | Weekly plan built from the diet rules, meal swaps, long-shift day, free meal, rule summary, kcal/macros per meal and day, "done" ticks and encouragement when a day or week goes well |
| **Spesa** | Lista raggruppata per reparto, "Da comprare" / "Già in casa", intera settimana o giorni scelti, voci extra scritte a mano | Shopping list grouped by supermarket section, bought items, whole week or chosen days, manual extras |
| **Progressi** | Acqua (obiettivo 1,7 L), consiglio del giorno, grafici Peso / Vita / Cosce, misure recenti, complimenti per obiettivo acqua, progressi e costanza | Water tracker (1.7 L goal), daily tip, weight / waist / thigh charts, recent measurements, compliments for water goal, progress and consistency |
| **Profilo** | Dati personali (nome, cognome, sesso, anno di nascita, altezza, peso) bloccati dopo il salvataggio con pulsante **Modifica**, **IMC**, obiettivo, livello sportivo, fabbisogno stimato, "Non mi piace / Intolleranze", pasti liberi | Personal data (name, surname, sex, birth year, height, weight) locked after saving with an **Edit** button, **BMI**, goal, sport level, estimated needs, food exclusions, free meals |
| **Impostazioni** (ingranaggio in alto a destra nel Profilo) | Promemoria e notifiche, backup e ripristino, informazioni (versione e avvertenza) | Reminders & notifications, backup & restore, about (version and disclaimer) |

La barra inferiore ha solo 4 schede (Dieta, Spesa, Progressi, Profilo); Impostazioni si apre dall'icona a forma di ingranaggio del Profilo.

### Profilo, peso e IMC

- Dopo il salvataggio i dati personali si leggono in sola lettura; **Modifica** apre i campi, **Salva** convalida e conferma, **Annulla** scarta le modifiche. Al primo avvio la scheda si apre già in modifica.
- Il **peso** ha un'unica fonte: salvarlo dal Profilo scrive la misura di oggi (vita, cosce e nota già registrate oggi restano) e viene letto da Progressi, dal fabbisogno stimato e dall'IMC; un peso registrato in Progressi compare subito nel Profilo.
- **IMC** = peso (kg) / altezza (m)²: sotto 18,5 *Sottopeso* · 18,5–24,9 *Normopeso* · 25,0–29,9 *Sovrappeso* · da 30,0 *Obesità*. È un valore indicativo: non distingue massa muscolare e massa grassa.
- Il fabbisogno giornaliero (formula di Mifflin-St Jeor, fattore di attività e obiettivo) è solo informativo e non cambia il piano.

### Regole della dieta / Diet rules

Ogni settimana il generatore rispetta questi limiti (il riepilogo in Dieta li mostra in tempo reale):

- **Colazione dolce** 2 volte · **pasto libero** 1 o 2 a scelta (a pranzo) · **minestrone** a cena 1 volta.
- **Cena**: primo 3–4 volte (pasta solo a cena; 2 con verdure e 2 con proteine, i piatti completi contano come primo proteico) e secondo 1–2 volte.
- **Carne** solo a pranzo, al massimo 4 volte (rossa al massimo 1) · **pesce** almeno 2 volte · **uova** 5–6 in tutto, in 2–3 occasioni.
- **Tonno** max 1 · **affettati** max 2 · **patate** max 1 a settimana.
- **Burro di arachidi** e **spremuta d'arancia**: al massimo una volta al giorno.
- Le porzioni crescono con il livello sportivo (da standard a +40%); i pezzi e i cucchiai non scalano.
- Gli alimenti in "Non mi piace / Intolleranze" non vengono mai proposti.
- Le alternative nel cambio pasto che superano un limite sono segnalate e non selezionabili.

## Privacy / Privacy

- **IT** · Tutto resta sul telefono: database locale, nessuna rete, nessun account, nessuna analisi. L'unico permesso richiesto è `POST_NOTIFICATIONS` (solo per i promemoria, solo se li attivi).
- **EN** · Everything stays on the device: local database, no network, no account, no analytics. The only permission is `POST_NOTIFICATIONS` (reminders only, only if you enable them).
- I valori nutrizionali sono indicativi e l'app non sostituisce il parere di un medico o di un nutrizionista. / Nutrition values are approximate; the app does not replace medical advice.

## Installazione / Installing

Serve Android 8.0 (API 26) o successivo. / Requires Android 8.0 (API 26) or newer.

### Da GitHub Releases (consigliato / recommended)
1. **IT** · Apri la pagina **Releases** del repository dal telefono e scarica `NutriForma-v<versione>.apk` (APK di release firmato). Aprilo e, se richiesto, consenti "Installa app sconosciute" per il browser/file manager.
   **EN** · Open the repository's **Releases** page on your phone, download `NutriForma-v<version>.apk` (signed release APK), open it and allow "Install unknown apps" if asked.
2. Gli aggiornamenti si installano sopra la versione precedente (stessa firma). Con [Obtainium](https://github.com/ImranR98/Obtainium) puntato al repository l'aggiornamento è automatico. / Updates install over the previous version (same signature); Obtainium pointed at the repository picks them up automatically.

### Via USB
1. **IT** · Sul telefono: *Impostazioni → Info telefono →* tocca 7 volte "Numero build", poi *Opzioni sviluppatore → Debug USB*. Collega il cavo e accetta la richiesta.
   **EN** · On the phone: enable Developer options (tap *Build number* 7 times) and **USB debugging**, connect the cable and accept the prompt.
2. Dal computer / From the computer:
   ```
   .\gradlew.bat installDebug      (Windows)
   ./gradlew installDebug          (macOS / Linux / Git Bash)
   ```
   oppure / or `adb install -r app-debug.apk`.

> L'APK di debug e quello di release hanno firme diverse: per passare dall'uno all'altro **disinstalla prima** l'app — fai prima un backup!
> Debug and release APKs use different signing keys: uninstall first when switching — **export a backup first**.

## Backup e ripristino / Backup & restore

Tutto in **Impostazioni → Backup e ripristino** / everything under **Settings → Backup and restore**:

- **Esporta backup (.json)**: salva un file dove vuoi (Storage Access Framework, nessun permesso speciale). / Saves a `.json` file anywhere you choose.
- **Ripristina da backup**: sostituisce i dati attuali dopo una conferma. / Replaces current data after a confirmation.
- **Cartella backup automatico** (es. `Documents/DietaBackup`) + interruttore: l'app aggiorna `dieta_backup_auto.json` a ogni modifica. Si vedono sempre l'esito e la data dell'ultimo backup. / Keeps `dieta_backup_auto.json` up to date after every change; the result and last backup time are always shown.
- Il file contiene profilo (anche nome e cognome), piani settimanali, misure, acqua, spunte della spesa, voci extra e pasti completati. Restano sul dispositivo la cartella di backup e le impostazioni di servizio. I backup creati da versioni precedenti si ripristinano senza problemi (i campi nuovi risultano vuoti). / The file holds profile (including name), weekly plans, measurements, water, shopping ticks and extras, completed meals. The backup folder is device-specific. Backups made by earlier versions restore fine (new fields come back empty).

## Sviluppo / Development

Stack: Kotlin, Jetpack Compose (Material 3, solo tema chiaro), Room, DataStore, WorkManager, kotlinx-serialization, DI manuale, grafici disegnati con `Canvas` (nessuna libreria esterna).

```
.\gradlew.bat testDebugUnitTest assembleDebug     (Windows)
./gradlew testDebugUnitTest assembleDebug         (macOS / Linux / Git Bash)
.\gradlew.bat lintDebug                           (controlli lint)
```

L'APK è in `app/build/outputs/apk/debug/`. I test sono JVM puri (nessun emulatore): regole, generatore, calcoli nutrizionali, IMC, complimenti, backup, ViewModel. / The APK ends up in `app/build/outputs/apk/debug/`; tests are pure JVM (no emulator).

Struttura: `domain/` (Kotlin puro: regole, generatore, nutrizione, IMC, consigli e complimenti), `data/` (Room, DataStore, repository, backup), `notifications/` (promemoria WorkManager), `ui/` (una cartella per scheda: `dieta`, `spesa`, `progressi`, `profilo`, `impostazioni`, più `promemoria` e `theme`).

## CI e rilascio / CI and releases

La GitHub Action `.github/workflows/android-apk.yml`:

- **Push / pull request**: esegue `testDebugUnitTest assembleDebug` e carica l'APK di debug come *artifact* (`NutriForma-debug-apk`) con i report dei test. / Runs the unit tests, builds the debug APK and uploads it as a workflow artifact.
- **Tag `v*`** (es. `git tag v1.2.0 && git push --tags`): esegue i test, compila l'APK di **release firmato** e lo allega a una GitHub Release (`NutriForma-v1.2.0.apk`). / Builds the signed release APK and attaches it to a GitHub Release.
- Secret richiesti per la firma / required signing secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Senza tutte le variabili d'ambiente di firma l'APK `release` in locale resta non firmato. / Without all signing variables a local `release` build stays unsigned.
- **Ogni rilascio deve aumentare sia `versionCode` sia `versionName`** in `app/build.gradle.kts` (Obtainium propone l'aggiornamento solo se la versione sale). / Every release must bump both `versionCode` and `versionName`.
