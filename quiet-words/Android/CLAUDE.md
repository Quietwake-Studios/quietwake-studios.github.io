# Quiet Words en Android

Proyecto Gradle de Quiet Words para Android. Cada app tiene su carpeta `Android/` junto a la de iOS (decisión 2026-11-13); los demás juegos seguirán el mismo esquema cuando se decida. La filosofía, el reto diario y las reglas del juego son los de `../../CLAUDE.md` y `../CLAUDE.md`: este archivo solo dice lo que cambia en Android.

## Decisiones (2026-11-13)

- **Kotlin + Jetpack Compose, nativo.** El motor de cada juego se porta de Swift a Kotlin; iOS no se toca. No se usa Kotlin Multiplatform ni Flutter.
- **El mismo reto en las dos plataformas:** un mismo día da el mismo tablero en iOS y en Android. Se verifica con **fixtures generados desde Swift** (`fixtures/` de cada proyecto): vectores de FNV-1a, SplitMix64 y claves de día en `QuietKit/Android/fixtures/`; normalización, temas, retos y tableros libres en `fixtures/`. Si un test de fixtures falla, manda Swift y se corrige Kotlin.
- **Una sola fuente de contenido:** los temas, la lista de bloqueo y `daily-lock.json` se leen de los recursos del paquete de iOS (`iOS/Packages/QuietWordsCore/Sources/QuietWordsCore/Resources/`; Gradle los toma como recursos, no se copian). La ruta está en un solo lugar: `quietWords.iosResources` en `gradle.properties`. El candado del reto aplica igual a las dos plataformas.
- **Clasificaciones y logros:** Google Play Games Services v2, con los mismos IDs de concepto que Game Center (`qw_daily_en`…); los jugadores de iOS y Android no se ven entre sí.
- **Cuenta de Google Play personal** (la empresa no está registrada, así que no hay D-U-N-S). Las cuentas personales nuevas deben pasar una **prueba cerrada con al menos 12 testers durante 14 días seguidos** antes de pedir producción.
- Paquete `com.tavoohoh.quietwords` (el mismo nombre del bundle de iOS).
- **Dependencias permitidas:** stdlib de Kotlin, kotlinx (serialización, coroutines), AndroidX/Jetpack y Play Games. Nada más: sin Firebase, analytics, anuncios ni SDKs de terceros. Data safety: declarar solo lo que recoja Play Games.
- minSdk 26, targetSdk/compileSdk 36. JDK 17 (JBR en `~/Library/Java/JavaVirtualMachines/jbr-17.0.12`). Gradle wrapper 8.14.3 (ya está en el caché).

## Estructura

```
quiet-words/Android/
├── settings.gradle.kts · build.gradle.kts · gradle.properties · gradle/libs.versions.toml · gradlew
├── words-core/      Kotlin JVM puro: port de QuietWordsCore (palabras, temas, generador, reto, sesión, progreso, álbum)
├── words-app/       App Android (Compose): MainActivity, AppModel (navegación, preferencias, argumentos debug), WordsApp,
│                    game/ (GameModel, BoardView, WordParts, GameScreen, GuessSheet, SummarySheet), home/, newgame/, tutorial/, design/
├── scripts/render-icon.py   ícono adaptable y de Inicio, con el dibujo de iOS (../iOS/scripts/render-icon.py)
└── fixtures/        JSON generados desde Swift (no se editan a mano)

QuietKit/Android/
├── quietkit-core/   Kotlin JVM puro: FNV1a, SeededRandom (SplitMix64), DailyKey, Streaks, Palette, DailyReminderPlan
├── quietkit-ui/     Biblioteca Android (Compose): QuietTheme (colores por rol claro/oscuro, Reduce Motion), QuietType,
│                    CapsuleButton, RoundIconButton, Segments, QuietScreen, quietSurface, QuietHaptics, Fireworks, QuietIcons,
│                    QuietwakeLockup y QuietSplash (lockup del estudio)
└── fixtures/        core.json, generado desde Swift
```

`quietkit-core` y `quietkit-ui` se incluyen desde `settings.gradle.kts` (`projectDir = ../../QuietKit/Android/…`) y usan el catálogo de versiones del proyecto que los incluye. Paquetes: `com.tavoohoh.quiet.core`, `com.tavoohoh.quiet.ui`, `com.tavoohoh.quietwords.core` y `com.tavoohoh.quietwords` (app). La app no repite reglas: todo pasa por `words-core` (`FreeGame` arma la partida libre, agregado en la Fase 2).

**Versiones (2026-11-13):** AGP 8.13.0, compileSdk/targetSdk 36, Compose BOM 2026.06.01 (Compose 1.11), activity-compose 1.13.0, lifecycle 2.10.0, material-icons-extended 1.7.8. Compose 1.12 (BOM 2026.08+) ya pide compileSdk 37 y AGP 9.1: no subir sin cambiar los dos. Firma: solo la de debug; no hay keystore de release.

## Comandos

Con `JAVA_HOME` en el JBR 17:

- Tests: `./gradlew test` (desde esta carpeta; corre `quietkit-core` y `words-core`, ~40 s).
- App: `./gradlew :words-app:assembleDebug` → `words-app/build/outputs/apk/debug/words-app-debug.apk`; instalar con `adb -s <serial> install -r <apk>`. `local.properties` (ignorado) con `sdk.dir=/Users/gus/Library/Android/sdk`.
- Tests de UI (Compose, en el emulador encendido): `ANDROID_SERIAL=<serial> ./gradlew :words-app:connectedDebugAndroidTest` (4: ganar arrastrando, tocar primera y última + TalkBack, adivinar mal y bien, tutorial con los dos idiomas).
- Emuladores propios de esta app, **uno a la vez**, sin ventana: `QW_Phone` ("QW Phone", Pixel 9, 1080×2424) y `QW_Tablet` ("QW Tablet", Pixel Tablet, 2560×1600 horizontal), los dos con `android-36;google_apis_playstore;arm64-v8a`. `~/Library/Android/sdk/emulator/emulator -avd QW_Phone -no-window -no-audio -no-boot-anim -port 5580` (tablet: `-port 5582`); apagar por serial (`adb -s emulator-5580 emu kill`), nunca todos ni los AVD del usuario (Pixel_8_API_33, Pixel_9). Se crearon escribiendo `~/.android/avd/QW_*.ini` y `config.ini` a mano: el `avdmanager` instalado (cmdline-tools 8.0) no conoce `pixel_9` y además renombra `~/.android/devices.xml` a `.old` (pasó una vez y se restauró).
- Modo oscuro del emulador: `adb -s <serial> shell cmd uimode night yes|no`.
- **Argumentos debug** (extras del intent, solo en debug; como los de iOS): `adb shell am start -S -n com.tavoohoh.quietwords/.MainActivity --ez startGame true --es seedKey <texto> --es level gentle|medium|hard --es theme <id> --ei found <n> --ez nearlyWon true --ez won true --ez hint true --ez stroke true --ei clock <s>`; `--es open newGame|tutorial|daily`; `--es words en|es`; `--ez tutorialDone true`; `--ez uiTesting true` (almacén propio vacío; las celdas llevan `AnswerMark` "start"/"end" de la siguiente palabra y la fila de la oculta `HiddenAnswer`, propiedades de semántica que TalkBack no lee).
- Ícono: `python3 scripts/render-icon.py` (necesita Pillow y SF Pro Rounded del Mac).
- Regenerar fixtures desde Swift (solo cuando cambie a propósito el motor o el contenido de iOS; luego `./gradlew test` debe pasar; cada línea desde esta carpeta):
  - `cd ../../QuietKit/iOS && QUIET_WRITE_FIXTURES=$PWD/../Android/fixtures swift test --filter FixtureWriter`
  - `cd ../iOS/Packages/QuietWordsCore && QUIET_WRITE_FIXTURES=$PWD/../../../Android/fixtures swift test -c release --filter FixtureWriter`
  - Sin la variable, el test `FixtureWriter` de Swift no hace nada.

## Fases (no avanzar sin cumplir el criterio)

1. **Motor:** Gradle, `quietkit-core` y `words-core` con tests. Acepta: los fixtures de Swift pasan (hash, PRNG y al menos 20 retos en/es idénticos letra por letra) y 1.000 tableros libres válidos.
2. **Jugable:** tablero con arrastre y toque, palabra oculta, pista, resumen, tutorial con el idioma, tema y apariencia del canvas de Words. Acepta: una partida completa en el emulador (teléfono y tablet). Sin tonos (van con la Fase 3 o 4).
3. **Persistencia y reto diario:** guardado, racha, calendario, recordatorios locales. Acepta: tests de la regla del reto y de recordatorios.
4. **Colección y ajustes:** álbum, recompensas, logros, Números rojos.
5. **Play Games:** logros y 5 clasificaciones. Acepta: funciona sin conexión y envía al reconectar (en un dispositivo).
6. **Publicación:** ficha de Play Store en/es, capturas, Data safety, clasificación de contenido (IARC), prueba cerrada de 12 testers × 14 días, producción.

## Estado

- 2026-11-13: plan aprobado (Kotlin + Compose, Play Games desde el inicio, cuenta personal).
- 2026-11-13: **Fase 1 cumplida.** Kotlin 2.3.21, kotlinx-serialization 1.10.0, JUnit 5.14.4, Gradle 8.14.3 (AGP 8.13.0 en el catálogo para la Fase 2). 55 tests pasan (25 en `quietkit-core`, 30 en `words-core`): los de Swift portados más los fixtures, idénticos a Swift letra por letra: FNV-1a (17 textos, con tildes, Ñ y emoji), SplitMix64 y `nextInt` (7 semillas), 500 claves de día, todas las palabras de los 11 temas en/es, 324 retos (2026-10-25 a 2027-03-31 y 4 fechas lejanas, en/es) y 148 tableros libres. 3.000 tableros libres válidos (1.000 por nivel). Generación en JVM (Mac, tras calentar): Suave 0,4 ms, Medio 1,4 ms, Difícil 2,2 ms por tablero. Sin divergencias Swift/Kotlin.
- 2026-11-13: **Fase 2 cumplida en emulador** (pendiente la aprobación visual del usuario). `words-app` y `quietkit-ui`: Inicio mínimo (tarjeta del reto de hoy sin guardar, Nueva partida), Nueva partida (nivel, EN · ES, temas con su progreso vacío), juego (puntaje, "Tema · Nivel · tiempo", tablero, oculta, lista en 3 columnas, Pista −30, Adivinar +100 con 3 intentos), resumen, tutorial (primer paso: idioma de las palabras, las dos opciones a la vista, el del sistema preseleccionado). Tablet: lista y controles al lado del tablero. Borde a borde, back predictivo (la pantalla se encoge con el gesto), claro/oscuro del sistema con Petróleo y coral. Tablero igual que iOS: arrastre con sectores de 45°, cualquier sentido, toque primera/última, cápsulas del Resaltador (`primary` 18 %/24 %), trazo `primary` 30 % + borde, anillo punteado `accent`, letras que sobran en `accent`; el tablero queda fuera del gesto de volver (`systemGestureExclusion`). Hápticos con `performHapticFeedback` (tic por letra, éxito, error con sacudida). Reduce Motion = escala de animación 0 ("Quitar animaciones"). TalkBack: cada letra "S, row 3, column 5", doble toque = el flujo del toque; la lista dice encontrada / por encontrar. Strings en `values/` y `values-es/` desde el xcstrings; idioma por app (`locales_config`). 59 tests JVM (4 nuevos de `FreeGame`) y 4 de UI pasan en QW Phone y QW Tablet. Diferencias con iOS: Roboto en lugar de SF Pro (no se puede incluir), íconos Material en lugar de SF Symbols, sin definiciones del diccionario en el resumen, resumen como hoja inferior también en tablet. Falta: tonos, persistencia, reto real (Fase 3), menú de Inicio, recompensas (Fase 4).
- 2026-11-13: visuales de la Fase 2 **aprobados** (los íconos Material se quedan). Cambios: en tablet (la misma regla que pone la lista al lado del tablero) el resumen es una tarjeta centrada como en iPad; en teléfono sigue la hoja inferior; los fuegos van encima de los dos. **Splash de Quietwake Studios** (marca corta "quietwake"): el sistema (SplashScreen API, `core-splashscreen` 1.2.0, tema `Theme.QuietWords.Starting`) solo pinta el fondo del tema; después Compose muestra `QuietSplash` de `quietkit-ui` (la marca "Q en pausa" a 80 dp y "quietwake" debajo, colores propios en claro/oscuro, etiqueta de accesibilidad "quietwake") ~1,5 s y se funde a Inicio (sin fundido con Reduce Motion; un toque lo salta; no aparece con `uiTesting`). Tests: 59 JVM y 4 de UI en QW Phone y QW Tablet.
