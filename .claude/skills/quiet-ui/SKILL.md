---
name: quiet-ui
description: Componentes, pantallas que se repiten y patrones de interacción de las apps de Quietwake Studios (QuietUI). Usar al construir o modificar cualquier pantalla, al agregar un componente, o al decidir animaciones, hápticos, textos y accesibilidad.
---

# UI de la familia

Fuente: el sistema de diseño de la familia Quiet (link en `CLAUDE.md` de la raíz; leer su `README.md`, `pantallas.md` y la tarjeta del componente) y el código de `QuietKit/iOS/Sources/QuietUI/`. Si no coinciden, manda el código y se corrige el sistema de diseño.

## Componentes (QuietUI)

| Necesito | Uso |
|---|---|
| Botón principal / secundario | `.buttonStyle(CapsuleButtonStyle())` / `CapsuleButtonStyle(isProminent: false)` |
| Acción secundaria con energía | `AccentButtonStyle()` |
| Volver, menú | `RoundIconLabel(systemImage:tint:)` (48 pt, arriba) |
| Tarjeta | `.card()` o `SurfaceShape(shape:)` |
| Pantalla empujada | `.screen(Text("Título")) { trailing }` (barra del sistema oculta) |
| Segmentos | `Segments(selection:options:)` |
| Ajustes | `GroupSection(title:)` + `ThemedToggleStyle()` |
| Elogio, aviso corto | `Toast(text:)` |
| Celebración | `Fireworks(colors:seed:)` (nada con Reduce Motion) |
| Feedback | `HapticsService` (`.select`, `.place`, `.success`, `.invalid`) y `ToneService` |
| Anchos | `Layout.boardWidth/column/buttons/calendar(sizeClass)`, `.readableColumn()` |
| Tipografía | `Font.score`, `.counter`, `.stat`; `Animation.quiet` |
| Recordatorio del reto | `DailyReminders` (19:00 y 21:00, plan en `DailyReminderPlan` de QuietCore), `.dailyReminderTriggers`, `.dailyReminderOffer`, `DailyReminderToggle` en Ajustes |
| Reseñas | `StoreReview(appStoreID:)` en el `AppModel` (política pura `ReviewPrompt` en QuietCore) y `RateAppRow(appName:review:)` en Ajustes |
| Amigos | `FriendsTodayCard` (Reto diario), `FriendProfileView` (frente a frente, últimos 7 retos), `PlayerAvatar`; datos con `GameCenterService.dailyEntries`, `headToHead`, `friendsToday` |

Algo que no está en la tabla y se usará en dos apps: va a QuietUI (con su tarjeta en el sistema de diseño), no a la app.

## Pantallas que se repiten

Inicio, Juego, Resumen de partida, Reto diario, Clasificaciones, Colección, Ajustes, Tutorial y Splash, con la estructura de `pantallas.md` del sistema de diseño. Copiar la pantalla equivalente de la otra app y cambiar el contenido del juego; no rediseñar el esqueleto.

## Patrones

- Colores solo por rol (`@Environment(\.quietTheme)`); probar cada pantalla en Petróleo y coral claro y oscuro, en un tema de un modo y en Alto contraste.
- Un toque nunca espera a una animación: el siguiente toque la termina.
- Animaciones con `withAnimation(reduceMotion ? .easeInOut(duration: 0.15) : .quiet)`; efectos largos solo para celebrar.
- Elogios: uno a la vez, 1,2 s. Errores: sacudida + háptico, sin texto.
- Fin de partida: hoja de resumen con estadísticas; fuegos solo al completar/ganar; logros de la partida en una tarjeta, sin avisos encima.
- Cerrar el resumen (2026-11-13): cada botón que lo cierra llama `app.storeReview.summaryClosed(startsNewGame:)` (`true` con "Nueva partida"). A dónde vuelve lo decide Inicio con `AppModel.summaryReturnsToDaily`: **cualquier reto** (hoy o pasado) vuelve al Reto diario (`path = [.daily]`), la partida libre a Inicio (`path = []`). En un reto el botón dice "Listo" ("Done").
- Pedido de reseña (2026-11-13): lo hace `StoreReview` solo, una vez por instalación al cerrar el resumen de la 3.ª partida terminada, después de que la hoja se va. Si se cerró con "Nueva partida", espera a la próxima vuelta a Inicio o al Reto diario: Inicio llama `app.storeReview.returnedToMenu()` en `.onChange(of: path)` cuando `path.last` es `nil` o `.daily`. Nunca en medio de una partida, sobre un tablero nuevo ni con un diálogo propio. La cuenta vive en `UserDefaults` (`storeReview.*`); "Restablecer progreso" no la borra. Apagado con `-uiTesting`, `-startGame` y `-demo`.
- Ajustes iguales en todas las apps (orden: Jugar, Apariencia, Reto diario, Cuenta, Privacidad con la plantilla de `CLAUDE.md`, Restablecer progreso, Acerca de; mismas filas `SettingsRow` y mismos textos salvo el nombre): Privacidad, Restablecer progreso (confirmación con el mismo texto en todas: "All your game progress is erased." / "Se borra todo el progreso de tu partida.") y la sección **"Acerca de" / "About"** con `RateAppRow` ("Califica Quiet <App>", abre `https://apps.apple.com/app/id<ID>?action=write-review`) y Versión (solo el número, `CFBundleShortVersionString`).
- Racha en textos: "N-day streak" / "Racha de N días" con plurales del catálogo (clave `%lld-day streak`), nunca "N streak".
- Textos: verbo que dice lo que pasa, en String Catalog con español; plurales del catálogo; números con `FormatStyle` y `.monospacedDigit()`.
- Accesibilidad: `accessibilityLabel` en todo lo que es solo ícono, etiquetas útiles por celda o carta, objetivos ≥ 44 pt, Dynamic Type salvo el tablero (que tiene su opción de tamaño).
- Textos de QuietUI: `Text("…", bundle: .main)` (p. ej. `Rate %@` de `RateAppRow`); cada app los pone en su catálogo (el compilador no los extrae de la app: listarlos a mano, como `KIT` en `quiet-solitaire/iOS/scripts/strings.py`). Los textos de las notificaciones los arma cada app.
- Notificaciones: permiso solo en contexto (al volver de un primer reto completado, `.dailyReminderOffer`), nunca al abrir la app; máximo 2 al día; nada de Game Center se guarda.
- iPad: mismo layout centrado con anchos máximos; atajos de teclado (⌘Z deshacer, H pista).

## Antes de dar algo por terminado

- Compilar y correr los tests; mirar la pantalla en el simulador (un solo simulador, apagarlo después).
- Revisar que no quedaron textos sin traducir (el catálogo no debe tener claves sin español).
- Si cambió el diseño acordado, actualizar el canvas de la app o avisar al usuario.
