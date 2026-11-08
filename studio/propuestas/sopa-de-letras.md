# Sopa de letras: propuesta técnica (2026-11-08)

Cuarto juego de la familia. Este documento cierra lo técnico (contenido, generador, reto, datos) para que un agente de diseño arme el canvas con la plantilla de `quiet-design-canvas`. Cuando el usuario apruebe nombre y decisiones, esto pasa al `CLAUDE.md` de `quiet-<app>/` y este archivo se borra.

## Idea

Sopa de letras clásica (mecánica genérica de dominio público, de los años 60–70) con **temas para adultos**: cada tablero es una colección de palabras de un tema (Océano, Espacio, Botánica, Café y té…). Lo que la aleja de lo infantil no es el arte sino el **vocabulario** (palabras con oficio: "abisal", "nebulosa", "helecho", "tueste") y el tono: tipografía, calma, nada de dibujos.

- **Nombre (aprobado 2026-11-08):** Quiet Words; en la tienda "Quiet Words: Word Search" / "Quiet Words: Sopa de letras". Bundle `com.tavoohoh.quietwords`, SKU `quietwords-ios`, prefijo de Game Center `qw_`. No encontramos una app con ese nombre; confirmar en App Store Connect justo antes de registrar (no se puede borrar).
- **Gratis siempre**, como Numbers, Solitaire y Sudoku.

## Reglas

- Tablero cuadrado de letras; la lista de palabras del tema a un lado (iPad) o debajo (iPhone).
- Se marca una palabra arrastrando de la primera a la última letra; el trazo se ajusta a las 8 direcciones. Alternativa accesible: tocar la primera y la última letra.
- Dificultad = tamaño + direcciones. **Tableros densos (decisión 2026-11-08):** las palabras cubren todo el tablero salvo las letras de la palabra oculta; no hay relleno al azar.

| Nivel (en / es) | Tablero | Palabras (aprox.) | Direcciones | Palabra oculta |
|---|---|---|---|---|
| Gentle / Suave | 9×9 | ~11 | → ↓ ↘ | 4–6 letras |
| Medium / Medio | 11×11 | ~20 | → ↓ ↘ ↗ | 5–7 |
| Hard / Difícil | 12×12 | ~22 | las 8 (incluye al revés) | 6–9 |

- **Palabra oculta (aprobada 2026-11-08):** las letras que no usa ninguna palabra, leídas en orden, forman una palabra del tema (p. ej. "MAREA" en Océano). Se puede adivinar antes de terminar para un bono: **3 intentos por partida, fallar no cuesta**. Si no se adivina, se revela al encontrar la última palabra. Es lo que más aleja el juego de una sopa de revista.
- **Pista:** ilumina la primera letra de una palabra que falte; cuesta puntos. En el reto cuesta lo normal (regla de la familia).
- Sin vidas ni límite de tiempo; el reloj solo cuenta para el puntaje y el desempate.

## Puntaje

- Cada palabra: 10 × letras.
- Bono por tiempo al terminar: hasta +50 % según el tiempo contra un objetivo por tamaño de tablero (Suave 3 min, Medio 5, Difícil 8), lineal hasta 0.
- Palabra oculta: +100 si se adivina antes de terminar, +30 si sale sola al final.
- Pista: −30 cada una (nunca deja el puntaje bajo 0).
- Reto: mejor puntaje del día y, a igual puntaje, menor tiempo (`GameCenterService.rankedByScoreAndTime`).

## Contenido: diccionario por temas (lo central)

**Decisión propuesta:** no usar un diccionario general. Un diccionario no sabe de temas, trae palabras raras o vulgares y no distingue lo común de lo rebuscado. En cambio, **listas curadas por tema y por idioma**, escritas por nosotros (con AI) y revisadas por el usuario, empaquetadas en el Core como JSON. Todo offline.

### Formato: agregar un tema es agregar un archivo (decisión 2026-11-08)

Crear temas tiene que ser fácil. Un tema es **un archivo JSON** en `<Core>/Resources/Themes/`, y nada más: la app carga todos los archivos de la carpeta (sin registro central, sin código, sin IDs a mano). El nombre del archivo es el id del tema.

`Themes/ocean.json`:

```json
{
  "symbol": "water.waves",
  "en": {
    "name": "Ocean",
    "hidden": ["SWELL", "TIDE", "REEF"],
    "common":  ["Coral", "Wave", "Shell", "Tide pool"],
    "crafted": ["Abyssal", "Plankton", "Undertow"],
    "rare":    ["Benthic", "Nudibranch"]
  },
  "es": {
    "name": "Océano",
    "hidden": ["MAREA", "OLEAJE", "ARRECIFE"],
    "common":  ["Coral", "Ola", "Concha", "Estrella de mar"],
    "crafted": ["Abisal", "Plancton", "Resaca"],
    "rare":    ["Bentónico", "Nudibranquio"]
  }
}
```

- **Palabras como texto simple**, tal como se muestran en la lista ("Estrella fugaz", "Pez espada"). La app normaliza para el tablero: mayúsculas, sin tildes ni diéresis (Á→A, Ü→U), **la Ñ se queda**, sin espacios ni guiones ("ESTRELLAFUGAZ").
- **Cada idioma es su propia lista**, no una traducción: "tueste" no tiene un equivalente corto en inglés y no hace falta. Un tema puede salir con un solo idioma mientras se escribe el otro (no entra al reto hasta tener los dos).
- Tres grupos en vez de un número por palabra: `common` (conocida), `crafted` (con oficio), `rare`. Suave usa `common`; Medio `common` + `crafted`; Difícil los tres, con al menos 3 de `crafted`/`rare`.
- El álbum guarda la palabra normalizada, no un índice, así que corregir el orden o agregar palabras no rompe el progreso de nadie.
- `scripts/new-theme.py <id> "<Name>" "<Nombre>" <símbolo>` crea el archivo vacío; `scripts/validate-words.py` dice qué falta. Con AI: "escribe el tema Café y té" → el agente llena el JSON → validador → revisión del usuario.

### Cuántas palabras

- **30 temas al lanzar × ~200 palabras por idioma** (≈ 12.000 entradas, ~300 KB). Con tableros densos (~11–22 palabras), un tema aguanta ~11 apariciones sin repetir; como el reto rota los 30 temas, eso es casi un año sin repetir una palabra en el reto.
- Mínimo por tema e idioma para entrar al reto: 200 palabras, ≥ 80 `common`, ≥ 15 palabras ocultas de 4–9 letras. Para el modo libre basta con 120.

### Temas iniciales (sin nada infantil ni de marcas)

Océano, Espacio, Botánica, Bosque, Aves, Fauna salvaje, Montaña, Clima y cielo, Minerales y gemas, Geografía, Cocina, Especias, Café y té, Panadería, Huerta, Música, Arquitectura, Pintura, Escritura y libros, Teatro y cine (oficio, no títulos), Mitología griega, Navegación, Viajes, Textiles, Oficios, Ciencia, Cuerpo humano, Matemáticas, Tiempo y calendario, Jardín.

Nada de nombres propios con marca (equipos, películas, productos). Mitología y geografía usan nombres de dominio público.

### Español neutro

Evitar palabras que cambian por país (palta/aguacate, frutilla/fresa, ordenador/computador). Si una es clave para el tema, se acepta solo la forma más extendida en LatAm. El validador marca una lista de pares regionales conocidos.

### Pipeline y validación

`scripts/validate-words.py` corre en CI local y antes de cada build:

- Alfabeto: A–Z (+ Ñ en español) después de normalizar; largo 3–13.
- Sin duplicados dentro de un tema (después de normalizar) y conteo mínimo por tier.
- **Lista de bloqueo** en/es (groserías e insultos): ninguna palabra la contiene; también la usa el generador para el relleno (ver abajo).
- Reporte por tema: cantidad por grupo y largo, qué falta para entrar al reto, palabras nuevas desde el último candado del reto.
- Mensajes que dicen qué arreglar y dónde ("ocean.json › es › rare: 'Bentónico' repetida"), para que agregar un tema no requiera leer código.

Proceso para escribir un tema: AI propone ~260 → el validador filtra → el usuario revisa y tacha → queda en ~200. La revisión humana es obligatoria: una palabra mala en el reto la ven todos el mismo día.

## Generador

En el paquete Core, sin UI, con `SeededRandom` de QuietCore (nada de `Int.random`):

1. Elegir la palabra oculta (largo según nivel) y las palabras del tema según nivel (grupo y largo ≤ lado del tablero), de mayor a menor largo.
2. Colocar con backtracking: para cada palabra, probar posiciones y direcciones en orden barajado, **prefiriendo cruces** con letras ya puestas (tablero más denso, más difícil de leer de un vistazo). Si una palabra no cabe tras N intentos, se cambia por otra del mismo tier.
3. Seguir poniendo palabras hasta que las celdas libres sean exactamente tantas como letras tiene la oculta (con cruces se ajusta el número); si no se llega, cambiar palabras o la oculta y reintentar con la siguiente semilla derivada.
4. Escribir la oculta en las celdas libres, en orden de lectura. **No hay relleno al azar.**
5. **Validar:** cada palabra aparece **exactamente una vez** en las 8 direcciones (también palíndromos y una palabra dentro de otra: "MAR" en "MARINO" no puede ir en el mismo tablero); ninguna palabra de la lista de bloqueo aparece en ninguna dirección (los cruces pueden formar una). Si falla, rehacer con la siguiente semilla derivada.

Objetivo: < 30 ms por tablero en un iPhone viejo; si no, se ve en la fase 1.

## Reto diario

Sigue "Reto diario (igual en todos los juegos)" del `CLAUDE.md` de la raíz. Lo propio:

- **Tema del día:** un orden fijo de los 30 temas, barajado una vez por "vuelta" con la semilla de la vuelta (cada tema sale una vez cada 30 días, sin el mismo tema dos días seguidos entre vueltas).
- **Palabras del día:** cada tema recorre su lista en una permutación por semilla; cada aparición toma las siguientes, así no se repiten hasta agotar la lista.
- **Dificultad por día de la semana** (como Sudoku y Solitaire): lunes Suave … sábado Difícil, domingo Difícil con palabra oculta larga.
- **Idioma (decisión 2026-11-08):** el reto en inglés y en español son tableros distintos, con **una clasificación diaria por idioma** (comparar tiempos entre listas distintas no es justo). La racha y el calendario son uno solo: cuenta el reto completado en cualquier idioma.
- **Versionado del contenido:** agregar palabras no puede cambiar los tableros de días ya jugados. Regla simple para quien escribe temas: **las palabras nuevas van al final de su grupo**. Al publicar una versión, `scripts/lock-daily.py` anota en `Themes/daily-lock.json` cuántas palabras de cada grupo usa el reto desde qué fecha (y desde cuándo entra un tema nuevo a la rotación). El validador falla si alguien borra o reordena palabras ya anotadas; corregir una palabra mala del reto es una excepción explícita en el candado. Test obligatorio: la misma fecha da el mismo tablero con cualquier versión posterior de la app.
- **Los dos idiomas el mismo día (decisión 2026-11-08):** se puede jugar el reto de hoy en inglés y en español; cada uno va a su clasificación y la racha cuenta una vez. Logro oculto "Bilingüe".

## Modo libre

Se elige tema (o "Sorpresa") y nivel; partidas sin fin, mejor puntaje por nivel. Todos los temas abiertos desde el inicio: el contenido nunca se bloquea.

## Colección y recompensas (cosméticas)

- **Álbum de palabras:** cada palabra encontrada queda en el álbum de su tema. Vive en Colección (segmento Álbum · Recompensas) y la estadística de Inicio lo abre. Progreso por tema ("Océano 87/152") e insignia al completar un tema. Es el gancho de largo plazo y no altera el juego.
- Recompensas ganadas jugando: temas de color de QuietKit, colores y estilos del trazo de selección ("marcadores"), tipografías del tablero (rounded, serif, mono).
- Logros (borrador): primera sopa, 100/1.000/10.000 palabras, completar un tema, palabra oculta sin pistas, reto 7/30 días seguidos, Difícil sin pistas en menos de 5 min.

## Game Center

Clasificaciones: reto diario **en inglés** y **en español** (recurrentes 24 h desde 05:00 UTC, tiempo en `context`; la app envía a la del idioma en que se jugó y muestra primero la del idioma elegido), mejor puntaje en modo libre Difícil por idioma, y racha (una sola). Partidas guardadas en iCloud (álbum incluido).

## Datos (SwiftData)

- `Attempt`/progreso del reto por día (ranura por día, "Continuar / Empezar de nuevo", como Numbers).
- Álbum: por tema e idioma, el conjunto de palabras normalizadas encontradas (~2 KB por tema completo).
- **Idioma de las palabras (decisión 2026-11-08):** separado del idioma de la app y **se elige en el tutorial**, como primer paso, con las dos opciones a la vista ("English words" / "Palabras en español"); nunca se deduce del sistema (alguien con el iPhone en inglés puede querer jugar en español). El del sistema solo viene preseleccionado. Cambiarlo no borra el álbum: hay un álbum por idioma.
- **Cambiar de idioma tiene que ser fácil (decisión 2026-11-08, para quien juega en los dos):** el idioma se ve y se cambia **donde se empieza a jugar**, sin ir a Ajustes: una pastilla "EN · ES" en la tarjeta del reto de hoy (con el estado de cada idioma: hecho / pendiente) y en Nueva partida, junto al nivel. No se pregunta antes de cada partida (sería fricción para quien juega en uno solo). Ajustes también lo tiene.

## QuietKit

Usa `DailyKey`, `SeededRandom`, `Streaks`, `Palette`, `GameCenterService`, `DailyReminders`, `Fireworks`, `SplashView`, `FriendProfile`. La normalización de texto y el generador quedan en el Core del juego (no se repiten en otro juego todavía).

## Fases

0. **Contenido primero:** formato, validador y 5 temas completos en/es revisados por el usuario. Criterio: el validador pasa y el usuario aprueba las listas.
1. **Motor:** generador, validación de unicidad y bloqueo, puntaje, palabra oculta; tests (misma fecha → mismo tablero, 10.000 tableros sin palabra bloqueada ni duplicada, tiempo de generación).
2. **Jugable:** tablero con arrastre y toque, hápticos por letra, animación al encontrar; partida completa en iPhone y iPad, VoiceOver en el tablero desde el principio (leer fila y columna, marcar con dos toques).
3. **Persistencia y reto diario** con calendario.
4. **Game Center.**
5. **Álbum y recompensas.**
6. **Pulido y lanzamiento** con los 30 temas.

## Decisiones del usuario (2026-11-08)

- Nombre Quiet Words; palabra oculta incluida, con tableros densos y 3 intentos sin costo; Suave con diagonal ↘; clasificación del reto separada por idioma y reto jugable en los dos; tildes solo en la lista; idioma elegido en el tutorial y fácil de cambiar al empezar a jugar; álbum dentro de Colección; temas como archivos JSON simples.
- Las imágenes de la App Store se revisan cuando haya capturas reales y deben seguir el esquema de las otras apps (`quiet-release` › Ficha de la App Store).

## Preguntas abiertas

1. Quién revisa las listas en español e inglés: propuesta, el usuario con ayuda de un agente; ~150 palabras × 2 por tema.
