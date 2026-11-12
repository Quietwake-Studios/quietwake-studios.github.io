# Quiet Words

**Canvas de diseño:** [Quiet Words](https://claude.ai/artifact/TFyB6fy62EYshzScLAaEKk) · Sistema de diseño de la familia: [Quiet Studios](https://claude.ai/artifact/59yz1mHLFU3u3jvSfugf7B)

Sopa de letras con **temas para adultos**: cada tablero es una colección de palabras de un tema (Océano, Espacio, Botánica, Café y té…), y las letras que no usa ninguna palabra forman la **palabra oculta**. Lo que la aleja de lo infantil no es el arte sino el vocabulario ("abisal", "nebulosa", "tueste"), la tipografía, la calma y los hápticos; nada de dibujos ni mascotas. Gratis para siempre, sin anuncios, sin compras y sin recolectar datos. Cuarta app de la familia Quiet (ver `../CLAUDE.md` y `../studio/roadmap.md`).

- **Nombre (aprobado 2026-11-08):** Quiet Words; en la tienda "Quiet Words: Word Search" / "Quiet Words: Sopa de letras"; en la pantalla de inicio, Quiet Words. "Sopa de letras" / "word search" es el nombre genérico de la mecánica (de dominio público, años 60–70): nunca usar nombres, textos ni tableros de otras apps o revistas.
- Bundle `com.tavoohoh.quietwords` (registrado por Xcode el 2026-11-08, con Game Center e iCloud), SKU `quietwords-ios`, app ID `6832387795` ("Quiet Words: Word Search", creada por el usuario en la web), prefijo de Game Center `qw_`.

## Principios no negociables

Los de la familia: sin anuncios, compras, suscripciones ni tip jar; sin backend, analytics ni SDKs; 100 % offline (Game Center es un extra); recompensas solo cosméticas (única excepción, la de la familia: "Números rojos", pistas más baratas solo en libre); sin identificadores propios ni ATT. Frameworks: SwiftUI, SwiftData, GameKit (y StoreKit solo para `requestReview()`). **Todo el contenido viaja en la app** (listas curadas en JSON) y **todos los tableros se generan en el dispositivo**.

## Stack

| Pieza | Decisión |
|---|---|
| UI | SwiftUI. El tablero: letras posicionadas + una capa `Canvas` con las cápsulas del marcador. |
| Kit compartido | `../QuietKit` (QuietCore + QuietUI). |
| Lenguaje | Swift 6, strict concurrency. |
| Sistemas mínimos | iOS 18 / iPadOS 18; en Mac, la app de iPad. |
| Persistencia | SwiftData (partidas, retos por día e idioma, perfil = progreso + álbum). |
| Online | GameKit únicamente. |
| Tests | Swift Testing (Core y app) y XCUITest (jugar con el dedo). |
| Dependencias externas | Ninguna. |

Un solo target iOS (iPhone y iPad), un bundle ID, proyecto con XcodeGen (`project.yml`), como Quiet Sudoku.

## Comandos

- `xcodegen generate` tras agregar o mover archivos. No editar el `.pbxproj`.
- **Contenido:** `python3 scripts/validate-words.py [tema]` (0 errores para hacer build; dice qué arreglar y dónde), `python3 scripts/new-theme.py <id> "<Name>" "<Nombre>" <sf.symbol>` (tema vacío), `python3 scripts/lock-daily.py <yyyy-MM-dd>` (candado del reto al publicar una versión; `--show` lo muestra).
- Tests del motor: `cd Packages/QuietWordsCore && swift test -c release` (21 tests, ~30 s; incluye 10.000 tableros y un año de retos con los 11 temas en los dos idiomas. En debug tardan mucho más y el límite de tiempo sube a 150 ms).
- Tests de la app y UI: `xcodebuild test -project QuietWords.xcodeproj -scheme QuietWords -destination 'id=<udid>' -derivedDataPath build/dd` (11 de app: regla del reto, Números rojos, recordatorios, tutorial; 8 de UI: ganar arrastrando, tocar primera y última letra, adivinar, definición desde el resumen, Igual que el sistema, logros, pistas más baratas, tutorial).
- Simuladores propios de esta app, **uno a la vez**: "QW iPhone 17" y "QW iPad 13". Apagar por udid (`xcrun simctl shutdown <udid>`), nunca `shutdown all`.
- Textos: `python3 scripts/strings.py` después de compilar con `-derivedDataPath build/dd` (falla si una clave extraída no tiene español).
- Ícono: `python3 scripts/render-icon.py` (estándar y Gold, claro/oscuro/tinted, y el ícono de Inicio). Letras aprobadas el 2026-11-11 (opción A): S E A / O K R / L I N con dos trazos que comparten la S, SEA en la fila y SOL en la columna; el favicon de `../docs/quiet-words/` es la misma figura en SVG.
- **Publicación** (`AppStore/app-store-connect.md`: campos para crear la app, privacidad, versión y TestFlight). **Nada se sube ni se registra sin permiso explícito del usuario.**
  - Textos: `fastlane/metadata/{en-US,es-MX}` (solo la estructura de carpetas; no se usa la gema), validar con `python3 scripts/validate-metadata.py`, subir con `scripts/upload-metadata.py [--apply]` (textos, categoría Palabras + Puzle, copyright). Cliente `scripts/asc.py`, credenciales fuera del repo en `~/.appstoreconnect` (las de Quiet Numbers). Subtítulo (aprobado 2026-11-11): "Daily themed puzzles" / "Sopas con tema y reto diario".
  - Game Center: `AppStore/game-center.json` (11 logros, 620 puntos, y 5 clasificaciones; IDs verificados contra el código), `scripts/setup-game-center.py [--apply]` los crea con las 11 insignias de `AppStore/achievements/` (1024 sin alfa, `python3 scripts/achievement-badges.py`, con `scripts/render-svg.swift`).
  - TestFlight: "What to Test" en `AppStore/testflight.md`; `scripts/ExportOptions.plist` (app-store-connect, upload, firma automática, sin Team ID). Notas para Apple y guion del video en `AppStore/app-review-notes.md`.
  - **Ficha visual (2026-11-11, esquema de la familia copiado de Quiet Sudoku):** capturas reales con `scripts/screenshots.sh [iphone|ipad]` (estado `-demo` de `App/Debug/DemoData.swift`; crea su propio simulador "QW Shots …" y lo borra por udid al terminar; sonido apagado en ese simulador porque los tonos necesitan el audio del Mac) → `AppStore/screenshots/<iphone-6.9|ipad-13>/<en|es>/<light|dark>/`. Imágenes: `python3 scripts/store-images.py` → `AppStore/store-images/<iphone-6.9|iphone-6.3|ipad-13>/<en|es>/01-board … 05-hidden` (1 sopa con temas, 2 reto diario, 3 amigos, 4 recompensas con el álbum, 5 palabra oculta). Header y búsqueda ("Sopa de letras / que no se repite." · "Cada partida, un tablero nuevo. En español y en inglés."; en inglés "Word search / that never repeats."; la tarjeta de la búsqueda es el reto de las capturas, miércoles 11 · Botánica): HTML estático en `AppStore/creative/` (el teléfono de la búsqueda es `phone.<lang>.png`, la captura 01 en claro a 780 px) → `python3 scripts/creative-assets.py` → `AppStore/creative-assets/<en|es>/{header,search}.png`. Estilo en `AppStore/store-style.json` (editor: `python3 ../studio/store-editor/build.py words <carpeta>`). Nada subido a App Store Connect.
  - Web: `../docs/quiet-words/{index,privacy,support}.html` (GitHub Pages desde `/docs`, se publica con el push a main).
- **Argumentos DEBUG:** `-startGame` abre una partida libre (`-seedKey <texto>`, `-level gentle|medium|hard`, `-theme <id>`, `-words en|es`, `-found <n>`, `-nearlyWon`, `-won` encuentra la última, `-hint`); `-open <daily|leaderboards|collection|album|achievements|settings|tutorial|newGame>`; `-uiTesting` (almacén propio; las celdas marcan "start"/"end" de la siguiente palabra y la palabra oculta se lee en `hiddenWord`), `-resetStore`, `-tutorial`, `-unlockAll`, `-date <ISO8601>`, `-forceLookUp` (muestra la acción del diccionario aunque el simulador no tenga diccionarios). `-demo` (estado fijo para la tienda, en memoria; `-completeToday`, `-demoClock <segundos>`) y `-albumTheme <id>` (abre el álbum de un tema).

## QuietKit

Usa `SeededRandom`, `FNV1a`, `DailyKey`, `Streaks`, `Palette`, `DailyReminderPlan` (QuietCore) y de QuietUI: `Theme`, componentes (`CapsuleButtonStyle`, `RoundIconLabel`, `Segments`, `GroupSection`, `ThemedToggleStyle`, `.screen`, `.card`), `HapticsService`, `ToneService`, `GameCenterService`, `DailyReminders`, `FriendsTodayCard`, `FriendProfileView`, `Fireworks`, `SplashView`. La normalización del texto, el generador y el álbum viven en el Core del juego (no se repiten en otra app todavía).

## Arquitectura

```
quiet-words/
├── project.yml · Config/ (Base.xcconfig; Local.xcconfig con el Team ID, ignorado por git)
├── App/
│   ├── QuietWordsApp.swift · AppModel.swift · Preferences.swift
│   ├── Features/   Home, NewGame, Game (GameModel, BoardView, WordParts, GameView, SummarySheet),
│   │               DailyChallenge (+ CalendarView), Leaderboards, Collection (álbum, recompensas y logros), Settings, Tutorial
│   ├── Services/   PersistenceService (SwiftData), FeedbackService, Reminders, AppClock
│   ├── DesignSystem/ Appearance (marcador, letra, Letras más grandes), LanguagePill, Rewards, DayKeyFormat
│   └── Resources/  Assets, Localizable.xcstrings, InfoPlist.xcstrings, PrivacyInfo.xcprivacy
├── Packages/QuietWordsCore/
│   ├── Sources/QuietWordsCore/
│   │   ├── Words.swift          WordLanguage, normalización, Word, WordGroup
│   │   ├── Themes.swift         WordTheme (un JSON = un tema), ThemeLibrary (temas, bloqueo, candado)
│   │   ├── Board.swift          Direction, Level, Placement, Board
│   │   ├── Generator.swift      tablero denso con la palabra oculta, validación
│   │   ├── DailyChallenge.swift DailyLock, tema y palabras del día
│   │   ├── GameSession.swift    Scoring, GameSession, GameSummary
│   │   ├── Progress.swift       Achievement, PlayerProgress, Album
│   │   └── Resources/Themes/*.json, Resources/Blocklist/{en,es}.txt
│   └── Tests/
├── AppTests/ UITests/ scripts/
```

## Contenido: temas (lo central)

**Un tema = un archivo JSON** en `Packages/QuietWordsCore/Sources/QuietWordsCore/Resources/Themes/` (decisión 2026-11-08): la app carga toda la carpeta, sin registro, sin código y sin IDs. El nombre del archivo es el id.

```json
{
  "symbol": "water.waves",
  "en": { "name": "Ocean", "hidden": ["SWELL", "TIDE"], "common": ["Coral", "Tide pool"], "crafted": ["Abyssal"], "rare": ["Benthic"] },
  "es": { "name": "Océano", "hidden": ["MAREA"], "common": ["Coral", "Pez espada"], "crafted": ["Abisal"], "rare": ["Bentónico"] }
}
```

- Palabras como se muestran en la lista ("León marino"). En el tablero se normalizan: mayúsculas, sin tildes ni diéresis, **la Ñ se queda**, sin espacios ni guiones ("LEONMARINO").
- **Cada idioma es su propia lista**, no una traducción. Grupos: `common` (Suave), + `crafted` y `regional` (Medio), + `rare` (Difícil, con al menos 3 que no sean `common`).
- **Regionalismos equilibrados, no evitados (decisión 2026-11-08, reemplaza la regla de "español neutro" de la propuesta):** las palabras de un país o región (Pandebono, Cafezinho, Palta y Aguacate, Courgette y Zucchini) van en la lista `regional`, **máximo 20 por tema e idioma** (el validador falla si pasa), sumando regiones de toda LatAm, España, Asia, África… donde entren de forma natural; en los dos idiomas. Espacio y Biología no llevan. En temas que ya son del mundo entero (Música, Geografía, Comidas, historia), los platos, artistas y lugares son el tema y van en los grupos normales; `regional` es para las variantes locales de una palabra (Comidas: palta/aguacate, frutilla/fresa). Diminutivos como "Cafecito" no son regionalismos (se quedó "Cafecito"; "Tecito" salió).
- **Largo 3–12 letras en el tablero** (no 13: el tablero más grande es 12×12; cambio frente a la propuesta, 2026-11-08). Palabra oculta: 4–9 letras.
- El álbum guarda la forma del tablero, no un índice: reordenar o agregar palabras no rompe el progreso.
- Mínimo por tema e idioma para el reto: 200 palabras, ≥ 80 `common`, ≥ 15 ocultas (validador); para el modo libre, 120. Un tema con un solo idioma no entra al reto.
- **Lista de bloqueo** en/es (`Resources/Blocklist`): ninguna palabra de la lista (ni una de sus partes, "Sea lion" → SEA, LION) puede serlo, y en el tablero no puede leerse en ninguna dirección salvo dentro de una palabra puesta (CÁLCULO, PEACOCK).
- Proceso: AI propone ~260 → validador → el usuario tacha → ~200. **La revisión humana es obligatoria.**
- **Temas del lanzamiento (lista definitiva, 2026-11-08): 11**, todos escritos, de 213 a 245 palabras por idioma y ~20 ocultas: `biology` (Biología), `botany` (Botánica), `coffee_tea` (Café y té), `food` (Comidas: frutas, verduras y platos del mundo), `geography` (Geografía), `modern_age` (Siglo XX y hoy, 1914–hoy), `mountain` (Montaña), `music` (Música: instrumentos, géneros y artistas de todo el mundo, apellidos o nombres de ≤ 12 letras), `ocean` (Océano), `renaissance_industry` (Renacimiento e industria, ≈1450–1914), `space` (Espacio). Los nombres de los dos de historia son propuesta y se pueden cambiar. Sin marcas comerciales ni figuras políticas polarizantes recientes. **Pendientes de la revisión del usuario.**

## Reglas del juego

- **Tablero denso (decisión 2026-11-08):** las palabras cubren todo el tablero salvo las celdas de la palabra oculta; **no hay relleno al azar**.

| Nivel (en / es) | Tablero | Palabras (medido) | Direcciones | Palabra oculta |
|---|---|---|---|---|
| Gentle / Suave | 9×9 | ~14 (8–20) | → ↓ ↘ | 4–6 letras |
| Medium / Medio | 11×11 | ~17 (13–24) | → ↓ ↘ ↗ | 5–7 |
| Hard / Difícil | 12×12 | ~19 (14–28) | las 8 | 6–9 |

  La tabla aprobada decía ~11/20/22; el número sale de los cruces y es lo que mide el generador con las listas actuales.
- Se marca una palabra **arrastrando** de la primera a la última letra: el trazo salta de letra en letra y se ajusta a la dirección más cercana (sectores de 45°). Vale en cualquier sentido (de la última a la primera también). Alternativa accesible: **tocar la primera y la última letra**; si la segunda no está en línea, pasa a ser la nueva primera.
- **Palabra oculta (2026-11-08):** las letras que sobran, en orden de lectura, forman una palabra del tema. Se puede adivinar antes de terminar: **3 intentos por partida, fallar no cuesta**. Si no, se revela al encontrar la última palabra (las letras se encienden en orden y van a sus casillas).
- **Pista:** ilumina la primera letra de una palabra que falte (anillo punteado hasta encontrarla). En el reto, de hoy o pasado, cuesta siempre lo normal.
- Sin vidas ni límite de tiempo; el reloj solo cuenta para el puntaje y el desempate.

## Puntaje

- Palabra: 10 × letras. Bono por tiempo al terminar: hasta +50 % de los puntos de palabras, lineal hasta 0 en el objetivo (Suave 3 min, Medio 5, Difícil 8).
- Palabra oculta: +100 si se adivina antes, +30 si se revela al final. Pista: −30, **aunque el puntaje quede negativo** (2026-11-09, regla de la familia; antes tenía piso en 0). Con "Números rojos", −10 solo en libre (`Scoring.cheapHints`, `AppModel.freeScoring`); cada partida guarda su `Scoring`, así que el cambio aplica desde la siguiente.
- Reto: mejor del día por idioma (más puntaje, luego menos tiempo; `GameCenterService.rankedByScoreAndTime`).

## Generador

Determinista (solo `SeededRandom`; nunca `shuffled()` ni `Hasher`). Intenta `"<clave>#0"`, `"#1"`… hasta 600:
1. Palabras elegibles del nivel (grupo, largo ≤ lado, sin palíndromos), barajadas y ordenadas de mayor a menor con un poco de azar.
2. Por cada palabra, la mejor posición por dirección (más cruces); gana **la dirección menos usada** que tenga lugar (si no, los tableros densos se vuelven filas apiladas), y como mucho una palabra del largo del lado.
3. Se para apenas las celdas libres caben una palabra oculta del nivel; esas celdas reciben la oculta en orden de lectura.
4. Validación: cada palabra se lee **exactamente una vez** en las 8 direcciones (nada de "MAR" dentro de "MARINO"), ninguna palabra bloqueada suelta, Difícil con ≥ 3 `crafted`/`rare`.
- Medido (2026-11-08, Mac en release): 1–4 ms por tablero; 10.000 tableros sin fallas.

## Reto diario

Regla de la familia (`../CLAUDE.md`), con lo propio:
- **Un tablero por idioma** (semilla `"<día>#en"` / `"<día>#es"`) y **una clasificación diaria por idioma**; **racha y calendario son uno solo**: un día cuenta una vez, en el idioma que sea. **Se puede jugar el de hoy en los dos idiomas** (decisión 2026-11-08), cada uno a su clasificación; hacer los dos da el logro oculto Bilingüe.
- **Tema del día:** los temas bilingües en un orden barajado por vuelta (cada tema una vez por vuelta, nunca dos días seguidos entre vueltas). **Palabras del día:** cada tema recorre su lista en un orden fijo por semilla y cada aparición empieza más adelante.
- **Nivel por día:** lunes y martes Suave, miércoles a viernes Medio, sábado Difícil, domingo Difícil con palabra oculta larga (las 3 longitudes más largas del nivel).
- **Candado del contenido:** `Themes/daily-lock.json` (lo escribe `lock-daily.py` al publicar una versión): desde una fecha, qué temas rotan y cuántas palabras de cada lista usan, con un digest por lista. **Las palabras nuevas van al final de su grupo**; el validador falla si se mueve o borra una ya fijada. Antes del primer candado (hoy) el reto usa todo; la rotación empieza el 2026-11-01.
- Guardado por día e idioma (`daily-<día>#<idioma>`), siempre el intento en curso; "Continuar / Empezar de nuevo"; "Repetición" desde el segundo intento.
- Tests: misma fecha → mismo tablero (5 referencias en/es, `DailyTests`), un año de retos válido en los dos idiomas, regla del reto en la app (`DailyRuleTests`).

## Game Center

| ID | Qué |
|---|---|
| `qw_daily_en`, `qw_daily_es` | Reto diario por idioma: recurrente 24 h desde 05:00 UTC, tiempo en `context`. La app envía al del idioma jugado y muestra primero el del idioma elegido. |
| `qw_best_hard_en`, `qw_best_hard_es` | Mejor puntaje en modo libre Difícil, por idioma. |
| `qw_longest_streak` | Racha más larga (una sola). |

El reto diario guarda el más reciente y acepta 0 o negativo (`always: true`); mejor Difícil y racha solo valores > 0.

Partidas guardadas en iCloud: `Snapshot` = perfil (progreso + álbum) + retos; la fusión nunca pierde progreso. `GKAccessPoint` solo en Inicio. Amigos: `FriendsTodayCard` en Reto diario y `FriendProfileView` con los tableros del idioma elegido. **Nada creado en App Store Connect.**

## Logros y recompensas (aprobados 2026-11-08)

| ID | Logro (es / en) | Condición | Recompensa |
|---|---|---|---|
| `qw_first_puzzle` | Primera sopa / First Puzzle | Terminar una sopa | Marcador Lápiz |
| `qw_words_100` | Cien palabras / A Hundred Words | 100 palabras encontradas | Tema Salvia y arena |
| `qw_words_1000` | Mil palabras / A Thousand Words | 1.000 palabras | Ícono dorado |
| `qw_words_10000` | Diez mil palabras / Ten Thousand Words | 10.000 palabras | Letra Mono |
| `qw_theme_complete` | Tema completo / Theme Complete | Completar un tema del álbum | Letra Serif |
| `qw_hidden_early` | A simple vista / In Plain Sight | Adivinar la oculta antes de terminar, sin pistas | Marcador Tinta |
| `qw_daily_7` | Hábito / Habit | Racha de 7 retos | Tema Índigo y durazno |
| `qw_daily_30` | Constancia / Consistency | Racha de 30 retos | Tema Salvia y coral |
| `qw_hard_fast` | Pulso firme / Steady Hand | Difícil sin pistas en menos de 10 min (2026-11-08) | Marcador Subrayado |
| `qw_bilingual` | Bilingüe / Bilingual (oculto) | Reto del día en los dos idiomas | Solo el logro |
| `qw_in_the_red` | Números rojos / In the Red (oculto, 20 puntos; 2026-11-09) | Llevar el puntaje por debajo de cero | Pistas a 10 en vez de 30 solo en libre; el reto diario siempre a 30. Interruptor "Pistas más baratas" en Recompensas: aparece al ganarlo, encendido |

Siempre disponibles: marcador Resaltador, letra Redonda, Petróleo y coral, **Alto contraste** y **Letras más grandes**.

**Números rojos (2026-11-09, pedido del usuario tras probar el build):** la regla de la familia (ver `quiet-numbers/CLAUDE.md`). En Numbers, Solitaire y Sudoku la pista pasa de 10 a 3 (−70 %); en Words la pista vale 30 y se eligió **10**: el mismo recorte redondeado (30 × 0,3 = 9) y justo lo que vale una letra (`perLetter`), así que una pista "cuesta una letra". Es la única recompensa que toca el juego, por eso se puede apagar: interruptor "Pistas más baratas" arriba en Recompensas (`@AppStorage("cheapHints")`, encendido por defecto, borrado con "Restablecer progreso"); no aparece hasta ganar el logro. El logro se evalúa con cada pista (`ProgressEvent.scoreChanged`).

**Logros en Colección (2026-11-09, el usuario no tenía forma de saber cómo ganarlos):** Colección tiene tres pestañas, Álbum · Recompensas · Logros. Logros: "N ganados de 11" y una fila por logro con nombre, condición, progreso cuando cuenta algo (palabras 100/1.000/10.000, mejor racha 7/30, % del tema más avanzado para Tema completo, `Album.bestThemeFraction`), sello si está ganado y la recompensa. Los ocultos (Bilingüe y Números rojos) dicen "Logro oculto" sin condición ni recompensa hasta ganarlos. Las otras apps muestran los logros como sección al final de Colección; en Words va como pestaña porque Colección ya está dividida en segmentos. UI tests `testAchievementsTab` y `testCheapHintsToggle`.

## Estética y UX

Sigue el canvas. Lo propio:
- **Idioma de las palabras separado del de la app** (2026-11-08): se elige en el **primer paso del tutorial** (las dos opciones a la vista; la del sistema solo viene marcada, nunca se deduce), y se cambia **donde se empieza a jugar** con la pastilla EN · ES (tarjeta del reto en Inicio y en Reto diario, con check en el idioma ya hecho hoy; Nueva partida, junto al nivel). Se recuerda el último y **no se pregunta antes de cada partida ni salta solo** (respuesta del usuario, 2026-11-08). También en Ajustes. Un álbum por idioma.
- El nombre del tema se muestra en el idioma de la app; la lista, en el de las palabras.
- Pantalla de juego (iPhone): volver, puntaje y "Tema · Nivel · tiempo", tablero, palabra oculta, lista debajo (3 columnas; 2 desde Dynamic Type XL, 1 en accesibilidad, con scroll), Pista −30 y Adivinar +100. iPad: lista y controles al lado del tablero. Atajo H = pista.
- Colores por rol (en cualquier tema y modo): trazo `primary` 30 % + borde; encontradas con el marcador (Resaltador `primary` 18 %, 24 % en oscuro); destello `accent`; pista: anillo punteado `accent`; letras que sobran: `accent`. Encontradas en la lista: tachadas y `muted` (no solo color).
- Hápticos y tonos: un tic y una nota más alta por cada letra que crece el trazo; `.success` al encontrar; `.invalid` al fallar la oculta (con sacudida). Un trazo que no es palabra se recoge sin castigo.
- Reduce Motion: sin vuelo de letras ni fuegos; fundidos cortos. Fuegos solo al completar un reto.
- **Definiciones (2026-11-08):** como "Look Up" de Libros, `UIReferenceLibraryViewController(term:)`: la hoja del diccionario del sistema, local, sin red y sin datos ("Buscar en la web" lo abre el jugador). Aparece solo si `dictionaryHasDefinition(forTerm:)` da true (depende de los diccionarios que el jugador tenga descargados): un libro junto a la palabra encontrada en el **álbum de un tema** y en las **palabras del Resumen**; la palabra va como se muestra, con tildes (`App/DesignSystem/LookUp.swift`). El simulador no trae diccionarios: la acción no aparece y nada falla (verificado); `-forceLookUp` la muestra para el UI test.
- **Apariencia (decisión 2026-11-08):** todos los temas tienen versión clara y oscura (variantes nuevas en `QuietCore/Palette.swift`: oscuras para Salvia y arena, Salvia y coral y Alto contraste; clara para Índigo y durazno; `PaletteTests` revisa AA en los dos modos). Se probó primero en Words y el usuario lo aprobó para toda la familia el mismo día: `Theme(palette, colorScheme:)`, con `nil` sigue al sistema. Ajustes › Apariencia: "Igual que el sistema" (encendido por defecto); al apagarlo aparece Claro · Oscuro, preseleccionado en el contrario del modo actual para que el cambio se vea al instante (`@AppStorage("appearanceMode")`: system/light/dark, `AppearanceRoot`). Las tarjetas de temas en Recompensas muestran el tema en claro y en oscuro. UI test `testMatchSystemToggle`.
- **Accesibilidad:** cada letra es un elemento ("S, fila 3, columna 5"), dos toques marca primera y última (el mismo flujo del toque); la lista se lee "encontrada / por encontrar"; Dynamic Type en todo menos el tablero.

## Localización

Inglés (development language) y español con String Catalogs (`scripts/strings.py`). "English words" y "Palabras en español" van siempre en su propio idioma.

## Privacidad

"Data Not Collected"; mismo `PrivacyInfo.xcprivacy` que Quiet Sudoku. Web (página, privacidad y soporte en/es) en `../docs/quiet-words/`.

## Fases

0. **Contenido:** formato, validador, lista de bloqueo y 5 temas en/es. Acepta: el validador pasa **y el usuario aprueba las listas**.
1. **Motor:** generador denso, palabra oculta, puntaje, reto con candado. Acepta: misma fecha → mismo tablero, 10.000 tableros válidos, tiempo de generación medido.
2. **Jugable:** arrastre y toque, hápticos, palabra oculta, resumen, tutorial con el idioma. Acepta: una partida completa en iPhone y iPad en el simulador.
3. **Persistencia y reto diario** con calendario y recordatorios. Acepta: `DailyRuleTests` y `ReminderTests`.
4. **Game Center:** logros, 5 clasificaciones, partidas guardadas, amigos. Acepta: funciona en modo avión y sincroniza al reconectar (en dispositivo).
5. **Álbum y recompensas.**
6. **Pulido y lanzamiento:** 30 temas, capturas, ficha (con el esquema de `quiet-release`), web, TestFlight (con el usuario: Bundle ID y App Store Connect).

## Estado (2026-11-08)

Build **1.0 (1)** en TestFlight, grupo interno "Internal" (2026-11-08); textos de la ficha subidos en en-US y es-MX. Falta para enviar a revisión: capturas, activar Game Center en la versión, subtítulo y la aprobación de las listas.

| Fase | Estado |
|---|---|
| 0. Contenido | 🟡 Validador, `new-theme.py`, `lock-daily.py`, bloqueo en/es y los 11 temas del lanzamiento con regionales (0 errores). Listas aprobadas por el usuario tras una revisión superficial (2026-11-11); salen así en la 1.0. |
| 1. Motor | ✅ 21 tests del Core en release: referencias por fecha en/es, 10.000 tableros, un año de retos, candado, sesión, puntaje, progreso y álbum. 1–4 ms por tablero en Mac. |
| 2. Jugable | ✅ en simulador: partida completa en "QW iPhone 17" y "QW iPad 13" (UI tests de arrastre y toque en los dos), adivinar, resumen, tutorial con el paso del idioma. Falta probar en iPhone real. |
| 3. Persistencia y reto | ✅ en simulador: guardado por día e idioma, Continuar / Empezar de nuevo, racha única, días pasados, calendario, recordatorios; `DailyRuleTests` (7) y `ReminderTests` (2). |
| 4. Game Center | 🟡 10 logros (con insignias, en/es) y 5 clasificaciones creados en App Store Connect (2026-11-08, `setup-game-center.py --apply`; reto diario desde 2026-11-09 05:00 UTC). Falta activarlos en la versión 1.0 y verificar envío y sincronización en el dispositivo. `qw_in_the_red` (2026-11-09) está en `game-center.json` con su insignia pero **no creado** en App Store Connect (lo hace quiet-studio con permiso del usuario). |
| 5. Álbum y recompensas | 🟡 Álbum por tema e idioma con insignias y definiciones del diccionario del sistema, recompensas seleccionables y bloqueadas con su condición, ícono dorado. Falta revisar en dispositivo (con diccionarios descargados). |
| 6. Lanzamiento | ⬜ App Store pendiente hasta tener capturas reales (decisión 2026-11-08). |

## Preguntas abiertas

- [x] Revisión de las 22 listas: superficial y aprobada (2026-11-11). Antes de cambiarlas en la 1.1, candado con fecha 2026-11-01 (`BACKLOG.md`).
- [ ] Nombres de los dos temas de historia ("Renacimiento e industria", "Siglo XX y hoy").
- [x] Subtítulo de la tienda y letras del ícono: decididos con la ficha (2026-11-11).
- [ ] iPad en vertical: la lista debajo del tablero, para la 1.1 (`BACKLOG.md`).
