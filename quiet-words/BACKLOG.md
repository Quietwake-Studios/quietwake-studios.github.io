# Backlog · Quiet Words

## En curso

### Revisión del contenido (fase 0)
- **Estado (2026-11-08):** los 11 temas del lanzamiento en/es listos para que el usuario tache lo que no le guste (`Packages/QuietWordsCore/Sources/QuietWordsCore/Resources/Themes/`). Después: `scripts/validate-words.py`.

## Siguiente

### Más temas después del lanzamiento
- **Qué:** la lista de la propuesta (Bosque, Aves, Clima y cielo, Minerales y gemas, Arquitectura, Pintura, Escritura y libros, Mitología griega, Navegación…), ~200 palabras por idioma y hasta 20 regionales, con la misma revisión.

### Ficha de la App Store (fase 6)
- **Estado (2026-11-11):** ficha visual aprobada por el usuario: capturas, 5 imágenes, header y búsqueda ("Sopa de letras que no se repite." / "Word search that never repeats."), subtítulo ("Daily themed puzzles" / "Sopas con tema y reto diario") e ícono (opción A: SEA y SOL). Falta subirla a App Store Connect con un build 1.0 (4) que lleve el ícono nuevo; lo hace quiet-studio con permiso del usuario.

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
