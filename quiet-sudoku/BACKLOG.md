# Backlog · Quiet Sudoku

## En curso

### 1.0 en TestFlight
- **Estado (2026-10-30):** build 1.0 (1) subido; ficha en/es y Game Center creados. Falta probar en iPhone e iPad desde TestFlight y lo de "Qué falta hacer a mano" en `iOS/AppStore/app-store-connect.md`.

## Siguiente

### Ficha y tienda: lo aprendido el 2026-11-18
- **Cuándo:** en la próxima versión que se envíe.
- **Qué:**
  - Cambiar en el header y la imagen de búsqueda (en/es) las frases "Nothing to sell. Just play." / "Calm, no catch." por algo sobre el juego: la guía 2.3.14 no acepta precios ni frases que no se puedan comprobar.
  - Capturas de iPhone Duo (opcionales hoy, dan la insignia; obligatorias desde abril de 2027 con el SDK de iOS 27.1).
- **Por qué:** checklist de `quiet-release` (revisión de app-store-compliance).

### Imágenes de la tienda (fase 6)
- **Cuándo:** antes de mandar la 1.0 a revisión.
- **Qué:** `iOS/scripts/screenshots.sh` y `store-images.py` (como Quiet Solitaire): 5 imágenes por iPhone 6.9" e iPad 13" en en/es, header 21:9 y búsqueda 3:2 (iOS 27+). Subirlas a la página App Store del canvas y correr el QA.

### Texto de "De memoria" en Game Center
- **Cuándo:** antes de reenviar la 1.0 a revisión.
- **Qué:** con la escala de 2026-11-13, `qsu_no_notes` pasó a "Difícil o Experto sin notas". El código, `iOS/scripts/game-center.py` y `iOS/AppStore/game-center.{md,json}` ya lo dicen; falta cambiar la descripción (antes/después, en/es) en App Store Connect (`setup-game-center.py` no actualiza los que ya existen: a mano o con la API).

### Challenges y activities de Game Center (iOS 26+)
- **Cuándo:** cuando Quiet Numbers los tenga (se construyen en QuietKit y se adoptan aquí).
- **Qué:** ver `quiet-numbers/BACKLOG.md`.

## Ideas

- **Revisar colores y contraste de las imágenes de la tienda** (header, búsqueda y capturas sobre el teal vivo; el usuario notó un contraste raro, 2026-11-05). Para la siguiente iteración de la ficha, igual en los tres juegos.

- Opción de clásico "Revisar al final" en vez de marcar los errores al instante.
- Más técnicas (cadenas, coloreo) para un nivel "Maestro".
- Variantes (diagonal, killer) como modos aparte, si un día se quiere crecer.

## Hecho
