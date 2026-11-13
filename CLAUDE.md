# The Quiet Project

Nombre del estudio: **Quietwake Studios**; en el splash y como marca corta, **Quietwake** (decisión 2026-11-13; antes Quiet Studios, que estaba tomado en Google Play y restringido en Apple; "Studios" y no "Games" para no cerrar la puerta a apps que no sean juegos). Los juegos siguen siendo la familia **Quiet** (Quiet Numbers, Quiet Words…). Web: `https://quietwake-studios.github.io/` (repo `quietwake-studios/quietwake-studios.github.io` en la organización `quietwake-studios`, Pages desde `/docs`); el repo `tavoohoh/quiet-studios` solo redirige los links viejos que tienen las versiones ya enviadas.

Estudio que construye, con ayuda de AI, apps y juegos para la App Store. Cada app parte de una mecánica conocida y no patentada (2048, Number Match, idle/incremental donde las tareas generan moneda del juego para mejorar y facilitar las siguientes tareas, etc.) y la reinterpreta con identidad propia.

## Filosofía de la familia Quiet

Apps que respetan la atención y la privacidad del usuario:

- **Sin anuncios, sin compras in-app, sin suscripciones, sin tip jar.**
- **Sin backend propio, sin analytics, sin SDKs de terceros.** Todo corre en el dispositivo; meta: etiqueta "Data Not Collected" en la App Store.
- **Offline primero.** Los servicios de Apple (Game Center, iCloud) son extras que sincronizan cuando hay conexión.
- **Recompensas cosméticas**, ganadas jugando, nunca pagando, y sin alterar la jugabilidad.
- **Minimalismo tranquilo:** la personalidad está en animaciones y hápticos, no en ruido visual.

## Propiedad intelectual

Tomamos mecánicas genéricas, nunca nombres, marcas, assets, textos ni arte de los juegos originales. Cada app tiene nombre y estética propios.

## Plataforma

Ecosistema Apple (iPhone, iPad, Mac) primero. Ecosistema controlado: lo que funciona en un iPhone con el target soportado funciona en el resto.

**Android (decisión 2026-11-13):** empieza con Quiet Words, nativo en Kotlin + Compose, en `quiet-words/Android/` (ver su `CLAUDE.md`). El reto del día es el mismo en las dos plataformas y el contenido sale de los mismos archivos; clasificaciones con Google Play Games.

Stack por defecto (cada app puede ajustarlo en su propio CLAUDE.md):

- SwiftUI nativo, Swift 6 con strict concurrency.
- Un solo target multiplataforma por app, compra universal (un bundle ID).
- SwiftData para persistencia local; GameKit como único servicio online.
- La lógica del juego en un Swift Package propio sin dependencias de UI, testeado con Swift Testing.
- Cero dependencias externas.
- Localización con String Catalogs: inglés (development language y fallback) y español.

## Estructura del repositorio

Cada app vive en su propio directorio en la raíz, con su propio `CLAUDE.md` que contiene el brief y las decisiones específicas:

- `quiet-numbers/` — puzzle de números (emparejar iguales o que sumen 10). Primera app de la familia.
- `quiet-solitaire/` — solitario clásico (Klondike, robar 1). Segunda app, jugable en el simulador.
- `quiet-sudoku/` — sudoku 9×9 con solución única y dificultad por técnicas lógicas. Tercera app, jugable en el simulador (2026-10-30).
- `quiet-words/` — sopa de letras con temas para adultos en/es y palabra oculta. Cuarta app, jugable en el simulador (2026-11-08).
- **Plataformas por carpeta (decisión 2026-11-13):** cada app tiene `iOS/` y `Android/` dentro de su carpeta (`quiet-words/iOS`, `quiet-words/Android`), y el kit igual (`QuietKit/iOS`, `QuietKit/Android`). Android empieza con Quiet Words.
- `QuietKit/` — paquete compartido: `QuietCore` (PRNG, día del reto, rachas, paleta de temas) y `QuietUI` (tema, componentes, hápticos, tonos, Game Center, splash). Lo usan Quiet Solitaire y Quiet Numbers (migrado el 2026-10-28, para su 1.1; sin cambios visibles ni en los tableros de cada fecha).

## Reto diario (igual en todos los juegos)

Todos los juegos tienen modo libre y reto diario, y el reto funciona como el de Quiet Numbers (decisión 2026-10-30). Lo genérico vive en QuietKit (`DailyKey`, `Streaks`, `GameCenterService`); cada juego solo define su tablero, su meta y su puntaje.

- **El mismo para todos:** clave `yyyy-MM-dd` → FNV-1a → semilla → PRNG propio (`SeededRandom`); nada que dependa de la plataforma. Cambia a la medianoche local. Test obligatorio: la misma fecha da siempre el mismo tablero.
- **Hoy, todas las veces que quieras (cambio 2026-11-02):** el reto del día se puede jugar y repetir sin límite, y **cada intento cuenta** para la clasificación diaria: al terminar cada intento, la app envía el **mejor resultado del día** (mejor puntaje y, a igual puntaje, menor tiempo), así sirve con clasificaciones "Most recent" y un empate más rápido actualiza el tiempo. Localmente se guarda el mejor puntaje del día.
- **Días pasados, también sin límite:** cualquier día de los últimos 30 se puede jugar las veces que se quiera. No se envía a la clasificación (no cuenta contra amigos), pero si el puntaje supera el guardado de ese día, se guarda el nuevo. No se llama "práctica"; si hace falta distinguirlo, cualquier intento después del primero dice "Repetición".
- **Completar un día pasado cuenta para la racha:** rellena ese día una sola vez (racha, calendario y logros de retos; un hueco rellenado puede unir dos rachas).
- **Salir guarda el intento actual:** cada reto a medias (hoy o pasado) se guarda por día, con una marca en el calendario, y lo guardado es siempre **el último estado del intento en curso**: "Empezar de nuevo" o reiniciar reemplaza la partida guardada; nunca vuelve el estado de un intento anterior (bug encontrado en Quiet Numbers el 2026-11-02). Al volver a un reto en curso se pregunta **"Continuar" o "Empezar de nuevo"**.
- **Mismas reglas para todos en el reto:** las pistas cuestan siempre lo normal (sin el descuento de "Números rojos"), hoy o en días pasados.
- **Termina con un resumen:** hoja con puntaje, tiempo y estadísticas del juego y los logros de la partida; fuegos artificiales de QuietUI solo al completar el reto (nada con Reduce Motion).
- **Clasificación diaria:** recurrente cada 24 h desde las 05:00 UTC. A igual puntaje gana el menor tiempo: el tiempo va en el `context` del puntaje (segundos + 1) y la app reordena (`GameCenterService.rankedByScoreAndTime`).
- **Calendario mensual:** muestra los días reales de cada mes (28 a 31), con flechas para ir al mes anterior, los completados (bandas entre días seguidos), los retos a medias y el día de hoy; racha y mejor racha a la vista. Se pueden jugar los días que caen dentro de los últimos 30; los más viejos se ven pero no se abren.

- **Notificaciones (locales, sin servidor):** recordatorio a las 19:00 si no se ha completado el reto de hoy, con lo que hicieron los amigos si ya se sabe; "racha en riesgo" a las 21:00 si sigue sin hacerse y hay racha de 3 o más. Detalle en el backlog de Quiet Numbers.
- **Al terminar un día pasado (2026-11-13):** el botón del resumen vuelve a la pantalla del Reto diario (calendario), no a Inicio, porque lo normal es seguir rellenando días. Terminar el reto de hoy o una partida libre sigue volviendo a Inicio.
- **Racha en textos:** "N-day streak" / "Racha de N días" (nunca "N streak").
- **Restablecer progreso (Ajustes):** pide confirmación con el mismo texto en todos los juegos y plataformas: "Se borra todo el progreso de tu partida." / "All your game progress is erased." Borra partidas, retos, rachas, logros, recompensas y el guardado en la nube; conserva ajustes (idioma, apariencia, sonido, recordatorios) y la marca del pedido de reseña.

Un juego que no cumpla algo de esto lo anota en su `BACKLOG.md` hasta alinearlo.

## Reseñas en la tienda (igual en todos los juegos, iOS y Android; decisión 2026-11-13)

- **Pedido automático una sola vez:** al cerrar el resumen de la **3.ª partida terminada** (libre o reto, gane o no), se pide la reseña con el diálogo del sistema: `AppStore.requestReview(in:)` de StoreKit en iOS y la In-App Review API de Google Play en Android. Nunca en medio de una partida ni encima del resumen, nunca con un diálogo propio antes ("¿te gusta?") y nunca más de una vez por instalación (el sistema además limita cuántas veces se muestra). La cuenta de partidas y la marca "ya se pidió" viven en las preferencias locales; "Restablecer progreso" no las borra.
- **Botón en Ajustes:** fila "Rate Quiet <App>" / "Califica Quiet <App>" que abre la página de reseña de la tienda: iOS `https://apps.apple.com/app/id<app id>?action=write-review`, Android `market://details?id=<paquete>` (con `https://play.google.com/store/apps/details?id=<paquete>` si no hay Play Store). IDs de la App Store: Numbers 6827562391, Solitaire 6828074077, Sudoku 6828993446, Words 6832387795.
- **Android es espejo de iOS:** misma estructura, pantallas, reglas y textos; solo cambia lo que la plataforma obliga (fuente, íconos de Material, Play Games en lugar de Game Center).

## Diseño

- **Sistema de diseño de la familia Quiet:** https://claude.ai/artifact/59yz1mHLFU3u3jvSfugf7B (tokens de color por tema, tipografía, componentes de QuietUI, pantallas que se repiten, reglas de la App Store, logos). Lo genera el código de `QuietKit`; si no coinciden, manda el código y se actualiza el artefacto.
- **Cada app tiene un solo artefacto de diseño:** un canvas (tipo "Design", título `Quiet <App>`) con 5 páginas: Overview, App Store, Screens, Styles y Components. Solo lleva lo propio del juego; nada que ya esté en el sistema de Quietwake Studios (decisión 2026-10-29). Plantilla, QA y procedimiento en `.claude/skills/quiet-design-canvas`. Su link va en la primera línea del `CLAUDE.md` de la app. Una app nueva no empieza a programar pantallas sin él.
- Links: [Quiet Numbers](https://claude.ai/artifact/3Z4eY31mrvGeqPvR8hUaZL) · [Quiet Solitaire](https://claude.ai/artifact/EwWvXPwMtEVpx47drg2AJV) · [Quiet Sudoku](https://claude.ai/artifact/CUwSvM9TWjrAphXWbcrGFL). Los artefactos anteriores (un sistema de diseño y un canvas por app) se borraron el 2026-10-29; sus archivos están en `quiet-<app>/design/backups/pre-plantilla-2026-10-29/`.
- **Respaldos:** con cada versión aprobada en la App Store se exporta el canvas de esa app como HTML a `quiet-<app>/design/backups/<versión>/` (procedimiento en `quiet-release`). Los artefactos son privados: para compartirlos, menú Share.

## Estudio

- `studio/roadmap.md`: juegos publicados, el siguiente (Sudoku) y las ideas; **todos** con la misma estructura (modo libre + retos diarios). Incluye los prerrequisitos antes de lanzar un juego de pago.
- `studio/medicion.md`: qué medir en App Store Connect y cuándo (empieza con las apps publicadas).

## Skills para agentes

En `.claude/skills/`: `quiet-new-app` (crear una app), `quiet-project-structure` (estructura y convenciones de código), `quiet-design-canvas` (canvas de un juego: páginas y contenido), `quiet-ui` (componentes, pantallas y patrones) `quiet-release` (TestFlight, ficha, Game Center, web) y `quiet-backlog` (backlog de cada juego en `quiet-<app>/BACKLOG.md`). Al cambiar una forma de trabajar, actualizar la skill correspondiente en el mismo cambio.

## Cómo trabajamos

- Antes de trabajar en una app, leer su `CLAUDE.md`.
- Desarrollo por fases con criterio de aceptación explícito; no avanzar de fase sin cumplirlo.
- Respetar los principios de la familia Quiet por encima de cualquier atajo (nada de SDKs, red ni tracking "temporales").
