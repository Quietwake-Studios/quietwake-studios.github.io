# App Store Connect: Quiet Words

Todo lo necesario para crear y publicar la app. Textos en `fastlane/metadata/{en-US,es-MX}` (validar con `scripts/validate-metadata.py`, subir con `scripts/upload-metadata.py [--apply]`, que muestra primero qué cambia), Game Center en `game-center.json` (crear con `scripts/setup-game-center.py [--apply]`; insignias en `AppStore/achievements/`, generadas con `scripts/achievement-badges.py`). Bundle ID `com.tavoohoh.quietwords` ya registrado (Xcode, con Game Center e iCloud, 2026-11-08). **Nada se sube sin permiso explícito del usuario.**

## Crear la app (Apps › +, en la web: la API no crea apps)

| Campo | Valor |
|---|---|
| Plataforma | iOS (iPhone y iPad; en Mac se ofrece la app de iPad) |
| Nombre | **Quiet Words: Word Search** (es-MX: **Quiet Words: Sopa de letras**); en la pantalla de inicio, Quiet Words |
| Idioma principal | English (U.S.) |
| Bundle ID | `com.tavoohoh.quietwords` |
| SKU | `quietwords-ios` |
| Acceso | Acceso completo |

## Información de la app

| Campo | Valor |
|---|---|
| Subtítulo | Borrador en `subtitle.txt` ("Calm themed puzzles" / "Sopas con tema y reto diario"); se decide con la ficha |
| Categoría | Juegos, subcategorías **Palabras** y **Puzle** (`upload-metadata.py` la pone) |
| Clasificación por edad | Cuestionario: todo "Ninguno"/"No" salvo **Contests: Infrequent** (clasificaciones y reto diario con amigos: competencia opcional, sin premios; "Frequent" sube a 13+; decisión 2026-11-11) → **4+** |
| Derechos de contenido | No contiene contenido de terceros (listas escritas por nosotros; definiciones del diccionario del sistema, que no viajan en la app) |
| Copyright | © 2026 Quietwake Studios |
| Localizaciones | English (U.S.) y Spanish (Mexico) |

## Precio y disponibilidad

Gratis para siempre, todos los países. Sin compras dentro de la app.

## Privacidad

- URL de la política: `https://quietwake.github.io/quiet-words/privacy.html` (GitHub Pages desde `/docs`, se publica con el push a main).
- Etiqueta: **Data Not Collected**. `App/Resources/PrivacyInfo.xcprivacy`: sin rastreo, cero tipos de datos, `UserDefaults` con razón `CA92.1`.

## Versión 1.0

| Campo | Valor |
|---|---|
| Capturas | Pendientes hasta tener capturas reales (no hacen falta para TestFlight interno) |
| Texto promocional, descripción, palabras clave, novedades | `fastlane/metadata/<idioma>/*.txt` |
| URL de soporte | `https://quietwake.github.io/quiet-words/support.html` |
| URL de marketing | `https://quietwake.github.io/quiet-words/` |
| Game Center | 11 logros (`qw_in_the_red` aún sin crear en App Store Connect, 2026-11-09) y 5 clasificaciones (`game-center.json`); activar Game Center en la versión y elegirlos |
| Cifrado | No usa cifrado propio (`ITSAppUsesNonExemptEncryption = NO`) |
| Publicación | Automática al aprobarse |

## TestFlight

- "What to Test" en `testflight.md` (en y es).
- Build: como Quiet Sudoku (`git worktree add --detach` de `main` en el scratchpad, copiar `Config/Local.xcconfig`, `xcodegen generate`, `xcodebuild archive … -configuration Release -destination 'generic/platform=iOS' -allowProvisioningUpdates`, `xcodebuild -exportArchive -exportOptionsPlist scripts/ExportOptions.plist`). El Team ID sale de `Config/Local.xcconfig` (firma automática), no del plist.

## Notas para la revisión de Apple

En `app-review-notes.md` (skill `quiet-release`, "Envío a revisión").
