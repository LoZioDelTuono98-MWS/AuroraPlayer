# Aurora – visualizzatore musicale per Android

Lettore/visualizzatore a tutto schermo che si aggancia a **qualsiasi app musicale** del telefono
(Spotify, SimpMusic, YouTube Music, player locali…) e fa ballare le grafiche a ritmo.

## Cosa c'è dentro
- **5 visualizzatori**: Anello di barre, Onda circolare, Barre specchiate, Oscilloscopio, **Logo personalizzato**
- **Logo con perimetro di frequenze**: carichi un PNG (o scrivi un testo) e Aurora ne calcola il contorno;
  le frequenze escono dai bordi del logo come Barre, Onda o Punti
- **Sfondo**: copertina dell'album sfocata, immagine tua dalla galleria, o gradiente animato; zoom sui bassi
- **Effetti**: particelle (polvere stellare / braci / bolle), foschia colorata, flash sul battito, scossa sui bassi, bagliore neon
- **Colori** presi automaticamente dalla copertina, oppure palette Neon, Tramonto, Oceano, Arcobaleno, Bianco
- **Pannello liquid glass** in alto a sinistra con copertina, titolo, artista, app sorgente, barra di avanzamento
  e tasti indietro / play-pausa / avanti in vetro
- Gesti: **doppio tocco** = cambia stile, **tieni premuto** = impostazioni, tocco = mostra/nasconde i comandi

## Come compilarlo
1. Installa **Android Studio** (gratis) da developer.android.com/studio
2. *File → Open* e scegli la cartella `AuroraPlayer`
3. Aspetta la sincronizzazione Gradle (la prima volta scarica qualche centinaio di MB)
4. Collega il telefono con il *Debug USB* attivo (Impostazioni → Info telefono → tocca 7 volte "Numero build",
   poi Opzioni sviluppatore → Debug USB) e premi ▶ **Run**
   - Oppure *Build → Build APK(s)* e installa l'APK da `app/build/outputs/apk/debug/`

Requisiti: Android 9 o superiore.

## Primo avvio
- **Accesso ai brani in riproduzione** (accesso notifiche): serve a leggere titolo/copertina e a controllare il player.
  Su Android 13+, se l'interruttore è grigio perché l'app non viene dal Play Store:
  *Info app → ⋮ → Consenti impostazioni con restrizioni*.
- **Audio reattivo** (permesso microfono): Android lo chiede per analizzare l'audio in uscita.
  Non viene registrato nulla: si leggono solo le frequenze in tempo reale.

## Note
- L'analisi usa il "Visualizer" di sistema sul mix globale: funziona sulla grande maggioranza dei telefoni.
  Su alcuni modelli, se le barre restano ferme, prova a disattivare equalizzatori/effetti audio di sistema (es. Dolby).
- Se Gradle non trova una versione di libreria, accetta l'aggiornamento proposto da Android Studio.

## Struttura
- `media/` – sessione multimediale attiva (info brano + comandi), funziona con qualunque player
- `audio/AudioAnalyzer.kt` – FFT → 64 bande logaritmiche, bassi/medi/alti, rilevamento battito, guadagno automatico
- `logo/` – estrazione del contorno del logo (maschera alfa → tracciamento bordo → normali verso l'esterno)
- `ui/scene/` – motore grafico (sfondo, foschia, particelle, visualizzatori)
- `ui/glass/` – componenti liquid glass
- `ui/SettingsSheet.kt` – impostazioni in 4 schede
