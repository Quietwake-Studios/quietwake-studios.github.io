# Notas para la revisión de Apple

Plantilla de Quiet Numbers. Va completa en App Store Connect › versión › App Review Information › Notes, con el video como adjunto de esa misma sección. Revisar antes de enviar (versión, build, lo que muestra el video).

```text
Hi, thanks for taking a look at Quiet Words.

What it is
A calm word search with themes: find the words of a theme hidden in a grid of letters. Every letter belongs to a word except a few, and those leftover letters spell one more word of the theme. We made it for people who want a few quiet minutes with a puzzle, without ads, purchases, accounts or tracking. There's also a daily challenge, the same board for everyone in each language, if you like a reason to come back.

How to try it
No login or demo account needed. Open the app and tap New Game; the first time, a short tutorial asks which words you want to find (English or Spanish, separate from the app's language) and shows how to play (you can replay it from Settings > How to Play). The Daily Challenge is on the Home screen. If the device is signed in to Game Center you'll also see leaderboards, achievements and friends who played today; without it, everything else works the same, offline too.

In the album (Collection > Album), a book icon next to a found word opens the system dictionary (UIReferenceLibraryViewController). It only appears when the device has a definition for that word.

After you finish a first daily challenge, the app asks if you'd like a reminder at 7 pm. These are local notifications scheduled on the device, at most two a day, and they can be turned off in Settings.

The recording
Attached, recorded on an iPhone with the latest iOS and the submitted build. It starts at launch and goes through the tutorial, a game, the daily challenge and its calendar, the album, the leaderboards, the rewards and Settings. There are no accounts, user content or purchases in the app, so there are no flows for those.

Services
Just Apple's: Game Center (optional), the player's own iCloud to sync progress between their devices, and the system dictionary. No servers of our own, no third-party SDKs, analytics, ads, payments or AI.

Regions
It works the same everywhere, in English and Spanish.

Anything regulated or third-party
No. Word search is a classic puzzle; the name, art, word lists and text are our own.
```

## Guion del video (2–3 min, iPhone, build en revisión)

1. Instalación limpia del build en revisión, para que salga el tutorial (paso del idioma incluido).
2. Grabar desde la pantalla de inicio de iOS: abrir la app (splash → Inicio).
3. Nueva partida → tutorial → elegir tema y nivel → encontrar palabras arrastrando y tocando, una pista, adivinar la palabra oculta → resumen.
4. Inicio → Reto diario: cambiar EN · ES, jugar unas palabras, salir y ver "Continuar / Empezar de nuevo", calendario.
5. Colección › Álbum de un tema (definición si el iPhone tiene el diccionario), Recompensas, Clasificaciones (Game Center con sesión iniciada), Ajustes.
