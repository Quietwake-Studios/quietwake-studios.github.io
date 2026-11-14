# Quiet Numbers en Android

Proyecto Gradle de Quiet Numbers para Android, espejo de `../iOS` (decisión 2026-11-13: después de Quiet Words). La filosofía, el reto diario, las reseñas y los Ajustes son los de `../../CLAUDE.md`; las reglas del juego, las de `../CLAUDE.md`. Este archivo solo dice lo que cambia en Android; lo que ya decidió Quiet Words para Android (`../../quiet-words/Android/CLAUDE.md`) vale igual aquí salvo que se diga otra cosa.

## Decisiones (2026-11-14)

- **Kotlin + Jetpack Compose, nativo**, con la misma estructura que Quiet Words Android. iOS no se toca (solo se le agregó el test `FixtureWriter`, que no hace nada sin `QUIET_WRITE_FIXTURES`).
- **Android es espejo de iOS:** mismas pantallas, reglas y textos (de `../iOS/App/Resources/Localizable.xcstrings`); solo cambia lo que la plataforma obliga (Roboto, íconos Material, Play Games).
- **El mismo tablero en las dos plataformas:** misma semilla o fecha → mismo tablero, la misma meta y las mismas partidas jugada por jugada. Se verifica con **fixtures escritos por Swift** en `fixtures/` (60 días × 5 etapas, 7 semillas clásicas y 6 partidas enteras con parejas, +, pistas, fallos y deshacer, comparando el estado completo en cada paso). Si un fixture falla, manda Swift y se corrige Kotlin.
- Paquete `com.tavoohoh.quietnumbers` (el bundle de iOS). minSdk 26, compileSdk/targetSdk 36, JDK 17, Gradle 8.14.3, el mismo `gradle/libs.versions.toml` que Words (copiado; si cambia una versión, cambiarla en los dos).
- **Dependencias permitidas:** las de Words (stdlib, kotlinx, AndroidX/Jetpack, Play Games v2, In-App Review). Nada más.
- **QuietKit primero:** lo que Words Android ya tiene y Numbers repite **se mueve a `QuietKit/Android`** en vez de copiarse, y Words tiene que seguir pasando todos sus tests después de cada movida. Movido hasta ahora: `clock()` (Format.kt) y `DayFormat` (2026-11-14). Pendiente, en la fase que lo necesite: el calendario mensual y la tarjeta del reto (Fase 3), el patrón de guardado en JSON (`WordsStore` → un almacén genérico), los recordatorios con sus textos, `GroupSection`/`GroupRow`/`GroupToggle` y la fila de Ajustes (Fase 4), Colección (pestañas, tarjetas de recompensa, filas de logros), `LauncherIcon` y la plomería de Play Games de la app (`pushToPlayGames`, `CloudSnapshot`) (Fases 4–5). Ya estaban en el kit: tema, componentes, hápticos, tonos, fuegos, splash, `DailyReminders`, `StoreReview`/`ReviewPrompt`, `PlayGamesService`, `Leaderboards`/`ScoreTag`.

## Estructura

```
quiet-numbers/Android/
├── settings.gradle.kts · build.gradle.kts · gradle.properties · gradle/libs.versions.toml · gradlew
├── numbers-core/    Kotlin JVM puro: port de QuietNumbersCore (Board, MatchRules, BoardGenerator,
│                    GameSession con Saved para guardar, DailyChallenge, Achievements/PlayerProgress)
├── numbers-app/     App Android (Compose): MainActivity, AppModel (navegación, argumentos debug), NumbersApp,
│                    game/ (GameModel, BoardView, GameScreen, SummarySheet), home/, design/ (FeedbackService)
└── fixtures/        boards.json y games.json, generados desde Swift (no se editan a mano)
```

`quietkit-core` y `quietkit-ui` se incluyen desde `settings.gradle.kts` igual que en Words.

## Comandos

Con `JAVA_HOME` en el JBR 17 (`~/Library/Java/JavaVirtualMachines/jbr-17.0.12/Contents/Home`), desde esta carpeta:

- Tests: `./gradlew test` (`numbers-core` y `quietkit-core`).
- App: `./gradlew :numbers-app:assembleDebug` → `numbers-app/build/outputs/apk/debug/numbers-app-debug.apk`.
- Tests de UI: `ANDROID_SERIAL=<serial> ./gradlew :numbers-app:connectedDebugAndroidTest` (6: tocar dos números, arrastrar de uno a otro, fallo + pista + deshacer + "+", una partida hasta el resumen y vuelta a Inicio, vaciar el tablero pasa a la etapa 2, el reto muestra su meta).
- Emuladores: los de Words (`QW_Phone` puerto 5580, `QW_Tablet` 5582), **uno a la vez**, apagar por serial; nunca los AVD del usuario ni `~/.android/devices.xml`.
- Si se toca QuietKit/Android: correr también `./gradlew test` y los tests de UI de `../../quiet-words/Android`.
- **Argumentos debug** (extras, solo en debug): `--ez uiTesting true` (sin splash); `--ez startGame true` con `--es seedKey <texto>`, `--es boardValues 5,3,7,…` (números exactos), `--ei plusRemaining <n>`, `--ei moves <n>` (juega las primeras parejas) y `--ez hint true`; `--es open daily`; `--es summary daily|classic` (la última jugada ya hecha: reto completado con fuegos o "No quedan jugadas"); `--es date <ISO-8601>`.
- Regenerar fixtures (solo si cambia a propósito el motor de iOS; luego `./gradlew test` debe pasar):
  `cd ../iOS/Packages/QuietNumbersCore && QUIET_WRITE_FIXTURES=$PWD/../../../Android/fixtures swift test -c release --filter FixtureWriter`

## Fases (no avanzar sin cumplir el criterio)

1. **Motor:** `numbers-core` con tests. Acepta: los fixtures de Swift pasan (boards y partidas idénticas paso a paso) y los tests de iOS portados (parejas y conexiones, filas, +, pistas, deshacer, etapas, fin, logros).
2. **Jugable:** Inicio mínimo, juego (tablero con toque y arrastre, anillos, línea, rayos, "+N", fila que se ilumina y colapsa, sacudida, pista punteada, números del + uno a uno, elogios, etapa, 1–9 tachados, Deshacer · + · Pista), hápticos y tonos, resumen, fuegos del reto. Acepta: una partida completa en el emulador (teléfono y tablet), claro/oscuro, en/es.
3. **Persistencia y reto diario:** guardado, Continuar, reto por día (intentos sin límite, mejor del día, "Repetición"), racha, calendario, recordatorios. Acepta: tests de la regla del reto (los de iOS `AppTests`) y de recordatorios.
4. **Colección y ajustes:** temas, fichas, tipografía, efecto de fila, sonido, ícono dorado, logros, Números rojos, tutorial, Ajustes iguales a iOS. Acepta: cada logro desbloquea su recompensa y se aplica desde Colección.
5. **Play Games:** 11 logros y 3 clasificaciones (`daily_challenge` con desempate por tiempo, `best_score`, `longest_streak`), partida guardada. Acepta: funciona sin conexión y envía al reconectar (en un dispositivo).
6. **Publicación:** ficha de Play Store en/es, capturas, Data safety, prueba cerrada, producción.

## Estado

- 2026-11-14: **Fases 1 y 2 cumplidas en emulador** (pendiente la aprobación visual del usuario).
  - **Motor:** port directo de QuietNumbersCore (`Board` inmutable, `GameSession` con copia y `Saved` serializable, mismo orden de llamadas al `SeededRandom`). 42 tests propios: `SwiftFixtureTests` (60 días × 5 etapas y 7 semillas idénticos a Swift; 6 partidas de hasta 220 pasos con puntaje, etapa, +, pistas, racha, fin, deshacer, tiempo, celdas y parejas válidas iguales en cada paso), `GameSessionTests` (28), `MatchRulesTests` (4) y `AchievementsTests` (8), portados de iOS, más el tablero dorado del 2026-10-25. Con `quietkit-core`, 75 tests JVM. Swift: 78 tests del Core siguen pasando con el `FixtureWriter` nuevo.
  - **Jugable:** Inicio (ícono, nombre, tarjeta del Reto diario, Nueva partida), juego como iOS (puntaje 44, "Etapa N · Mejor N" o "Etapa 1 · Meta 550", 1–9 tachados, tablero de 9 columnas con celdas de (ancho − 48)/9 × 46 dp, cuadradas hasta 760 dp en tablet; toque y arrastre de número a número con línea, y arrastre desde un fantasma desplaza; anillo de selección con resorte, fallo en coral con sacudida ±4 dp, pista punteada que parpadea 2 veces, línea de 120 ms en 3 grosores, rayos de 260 ms, "+N" que sube 24 dp, fila que se ilumina 150 ms y colapsa, números del + que entran uno a uno, elogios como toast, cartel de etapa), Deshacer · + (con pulso al quedar trabado y rebote con "No hay parejas. Prueba +") · Pista, resumen desde abajo (máx. 520 dp, también en tablet como en iPad) con fuegos al completar el reto. Hápticos y tonos del kit (`FeedbackService`, notas de iOS). Reduce Motion sin línea, rayos ni sacudida. Ícono adaptable con el dibujo de iOS ("Long shot") en vectores; ícono de Inicio claro/oscuro de iOS. Tests de UI: 6 en QW Phone y QW Tablet.
  - **QuietKit:** `clock()` y `DayFormat` pasaron de Words al kit; Words sigue con 93 tests JVM y 16 de UI en QW Phone.
  - Diferencias con iOS: Roboto en lugar de SF Pro Rounded; íconos Material; los fuegos del kit; sin tutorial todavía (Fase 4, con Ajustes › Cómo jugar), sin guardado, Continuar, estadísticas ni menú de Inicio (Fases 3–4).
