# NutriForma

**IT** · App Android per la dieta, **100% in italiano**, **offline** (nessun login, nessuna rete, nessun permesso INTERNET).
**EN** · Android diet app, fully in Italian, fully offline (no login, no network, no INTERNET permission).

## Cosa fa / What it does

| Scheda / Tab | IT | EN |
|---|---|---|
| **Dieta** | Settimana generata secondo le regole della dieta, cambio pasto, turno lungo, pasto libero | Weekly plan built from the diet rules, meal swaps, long-shift day, free meal |
| **Spesa** | Lista raggruppata per reparto, "Da comprare" / "Già in casa", extra manuali | Grouped shopping list, bought items, manual extras |
| **Progressi** | Acqua (1,7 L), consiglio del giorno, grafici Peso/Vita/Cosce | Water tracker, daily tip, weight/waist/thigh charts |
| **Profilo** | Obiettivo, livello sportivo, "Non mi piace", pasti liberi, promemoria, backup | Goal, sport level, food exclusions, free meals, reminders, backup |

## Installazione / Installing

Serve Android 8.0 (API 26) o successivo. / Requires Android 8.0 (API 26) or newer.

### Da GitHub Releases (consigliato / recommended)
1. **IT** · Apri la pagina **Releases** del repository dal telefono e scarica `NutriForma-<versione>-debug.apk`. Aprilo e, se richiesto, consenti "Installa app sconosciute" per il browser/file manager.
   **EN** · Open the repository's **Releases** page on your phone, download `NutriForma-<version>-debug.apk`, open it and allow "Install unknown apps" if asked.
2. Le release si creano da sole quando si pubblica un tag `v*` (es. `git tag v1.0.0 && git push --tags`); ogni push produce comunque l'APK come *artifact* della GitHub Action.
   Releases are created automatically when a `v*` tag is pushed; every push also uploads the APK as a workflow artifact.

### Via USB
1. **IT** · Sul telefono: *Impostazioni → Info telefono →* tocca 7 volte "Numero build", poi *Opzioni sviluppatore → Debug USB*. Collega il cavo e accetta la richiesta.
   **EN** · On the phone: enable Developer options (tap *Build number* 7 times) and **USB debugging**, connect the cable and accept the prompt.
2. Dal computer / From the computer:
   ```
   adb install -r NutriForma-<versione>-debug.apk
   ```
   oppure compila e installa direttamente / or build and install directly:
   ```
   .\gradlew.bat installDebug      (Windows)
   ./gradlew installDebug          (macOS / Linux / Git Bash)
   ```
3. In alternativa copia l'APK sul telefono e aprilo dal file manager. / Or copy the APK to the phone and open it from a file manager.

> Gli APK di CI sono firmati con una chiave di debug temporanea: per installare sopra una versione compilata in locale (o viceversa) **disinstalla prima** l'app — fai prima un backup!
> CI APKs use a throw-away debug key: uninstall first when switching between a locally built and a CI build — **export a backup first**.

## Backup e ripristino / Backup & restore

- **Profilo → Esporta backup (.json)**: salva un file dove vuoi (Storage Access Framework, nessun permesso speciale). / Saves a `.json` file anywhere you choose.
- **Profilo → Ripristina da backup**: sostituisce i dati attuali (piani, misure, acqua, spunte della spesa, impostazioni). / Replaces current data after a confirmation.
- **Profilo → Scegli cartella backup automatico** (es. `Documents/DietaBackup`): l'app aggiorna `dieta_backup_auto.json` quando generi una settimana o registri misure. / The app refreshes `dieta_backup_auto.json` whenever a week is generated or measurements are logged.
- Non fanno parte del backup: voci extra della spesa, spunte "Completato" dei pasti, cartella di backup (dipende dal dispositivo). / Not included: manual shopping extras, meal "done" ticks, the backup folder path (device-specific).

## Sviluppo / Development

```
.\gradlew.bat testDebugUnitTest assembleDebug
```
L'APK è in `app/build/outputs/apk/debug/`. / The APK ends up in `app/build/outputs/apk/debug/`.

Stack: Kotlin, Jetpack Compose (Material 3, solo tema chiaro), Room, DataStore, WorkManager, kotlinx-serialization, DI manuale.

*I valori nutrizionali sono indicativi. L'app non sostituisce il parere di un medico o di un nutrizionista. / Nutrition values are approximate; the app does not replace medical advice.*
