# Backlog · Quiet Words

## En curso

### Candado del reto antes de tocar listas o temas
- **Qué:** la 1.0 (4) salió sin candado: el reto usa todas las palabras de cada lista con la rotación desde `unlockedEpoch` (2026-11-01). Antes de cambiar cualquier lista, correr `python3 scripts/lock-daily.py 2026-11-01` con las listas **tal como están en la 1.0**. La fecha es esa y no la de la 1.1, porque la rotación toma la fecha de la entrada como época. Así la 1.1 da los mismos tableros que la 1.0 en cada día. Después, las palabras nuevas van al final y entran con otra entrada, fechada cuando la 1.1 esté en los teléfonos.
- **También al agregar temas:** la rotación sale de la lista de temas bilingües, así que un tema nuevo cambia qué tema toca cada día. En la versión que traiga temas nuevos: primero el candado 2026-11-01 con los 11 temas y las listas de la 1.0, después los temas nuevos con una segunda entrada fechada cuando esa versión ya esté en los teléfonos.
- **Las palabras no se gastan:** cada tema recorre su lista en un orden fijo y vuelve a empezar; con ~200 por tema no hace falta agregar palabras pronto (usuario, 2026-11-11).
- **Por qué:** el reto es el mismo para todos. Si dos versiones dan tableros distintos el mismo día, la clasificación diaria deja de ser pareja.

## Siguiente

### Más temas después del lanzamiento
- **Qué:** la lista de la propuesta (Bosque, Aves, Clima y cielo, Minerales y gemas, Arquitectura, Pintura, Escritura y libros, Mitología griega, Navegación…), ~200 palabras por idioma y hasta 20 regionales, con la misma revisión.

### Ficha de la App Store (fase 6)
- **Estado (2026-11-11):** ficha visual aprobada por el usuario: capturas, 5 imágenes, header y búsqueda ("Sopa de letras que no se repite." / "Word search that never repeats."), subtítulo ("Daily themed puzzles" / "Sopas con tema y reto diario") e ícono (opción A: SEA y SOL). Subida con el build 1.0 (4) y enviada a revisión el 2026-11-11 (Contests: Infrequent, 4+).

### iPad vertical: la lista debajo del tablero (1.1)
- **Qué:** en iPad en vertical la partida deja mucho espacio arriba y abajo del tablero porque la lista y los controles van al lado (se ve en la imagen 1 de iPad). En vertical, poner la lista debajo del tablero como en iPhone (a 3–4 columnas) y dejar el lado a lado para horizontal. Volver a sacar las capturas de iPad (`scripts/screenshots.sh ipad`, `scripts/store-images.py`).
- **Por qué no en la 1.0:** decisión del usuario (2026-11-11), la 1.0 sale como está.

### Game Center en App Store Connect
- **Qué:** 11 logros con insignias (`qw_in_the_red`, 2026-11-09, aún sin crear en App Store Connect) y 5 clasificaciones (`qw_*`), con `game-center.py` / `setup-game-center.py` como Quiet Sudoku. Solo con permiso del usuario.

### Web y privacidad
- `../docs/quiet-words/{index,privacy,support}.html`.

## Ideas

- Que la pastilla EN · ES salte sola al idioma pendiente del reto: descartado por ahora (2026-11-08, el usuario prefiere el último usado).

## Hecho
