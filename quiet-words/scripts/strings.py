#!/usr/bin/env python3
"""Writes App/Resources/Localizable.xcstrings and InfoPlist.xcstrings (en source, es).

    scripts/strings.py

Keys are the English strings in code. Run after adding text to views (build first with
`xcodebuild … -derivedDataPath build/dd`); it fails if an extracted key has no Spanish here.
"English words" and "Palabras en español" stay in their own language on purpose.
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

ES = {
    "#%lld among friends": "#%lld entre amigos",
    "%@ done · %@ to go": "%1$@ hecho · falta %2$@",
    "%@ finished today's challenge with %@. Up for it?": "%1$@ terminó el reto de hoy con %2$@. ¿Te animas?",
    "%@ · %@": "%1$@ · %2$@",
    "%@ · %@ · %@": "%1$@ · %2$@ · %3$@",
    "%@ · best %lld": "%1$@ · mejor %2$lld",
    "%@ · not played yet": "%@ · sin jugar todavía",
    "%@, found": "%@, encontrada",
    "%@, row %lld, column %lld": "%1$@, fila %2$lld, columna %3$lld",
    "%@, row %lld, column %lld, first letter": "%1$@, fila %2$lld, columna %3$lld, primera letra",
    "%@, row %lld, column %lld, in a found word": "%1$@, fila %2$lld, columna %3$lld, en una palabra encontrada",
    "%@, to find": "%@, por encontrar",
    "%lld of %lld found": "%1$lld de %2$lld encontradas",
    "%lld×%lld": "%1$lld×%2$lld",
    "%lld×%lld · about %lld words · %lld directions": "%1$lld×%2$lld · unas %3$lld palabras · %4$lld direcciones",
    "%lld×%lld · long hidden word": "%1$lld×%2$lld · palabra oculta larga",
    "A Hundred Words": "Cien palabras",
    "A Thousand Words": "Mil palabras",
    "A word of the theme made of the letters no word uses. Get it now for +%lld.":
        "Una palabra del tema hecha con las letras que no usa ninguna palabra. Si la adivinas ahora, +%lld.",
    "Account": "Cuenta",
    "Achievement": "Logro",
    "Achievement unlocked": "Logro desbloqueado",
    "Album": "Álbum",
    "All": "Todas",
    "Any theme": "Cualquier tema",
    "App icon": "Ícono de la app",
    "Back": "Volver",
    "Badge": "Insignia",
    "Badge earned": "Insignia ganada",
    "Badge not earned yet": "Insignia por ganar",
    "Best %lld": "Mejor %lld",
    "Best streak": "Mejor racha",
    "Best · Hard": "Mejor · Difícil",
    "Bigger letters on the board.": "Letras más grandes en el tablero.",
    "Bilingual": "Bilingüe",
    "Board": "Tablero",
    "Board letters": "Letra del tablero",
    "Cancel": "Cancelar",
    "Challenge complete!": "¡Reto completado!",
    "Check your connection and try again.": "Revisa tu conexión e inténtalo de nuevo.",
    "Collection": "Colección",
    "Complete 30 daily challenges in a row.": "Completa 30 retos diarios seguidos.",
    "Complete 7 daily challenges in a row.": "Completa 7 retos diarios seguidos.",
    "Complete the day's challenge in both languages.": "Completa el reto del día en los dos idiomas.",
    "Completed": "Completado",
    "Consistency": "Constancia",
    "Continue": "Continuar",
    "Continue this challenge?": "¿Seguir con este reto?",
    "Continue · %@ · %@": "Continuar · %1$@ · %2$@",
    "Couldn't load": "No se pudo cargar",
    "Daily Challenge": "Reto diario",
    "Daily challenge": "Reto diario",
    "Daily · %@ · %@": "Reto · %1$@ · %2$@",
    "Done": "Listo",
    "Done in both languages": "Hecho en los dos idiomas",
    "Drag from the first letter to the last.": "Arrastra de la primera letra a la última.",
    "Earned": "Ganado",
    "English": "inglés",
    "English words": "English words",
    "Español": "Español",
    "Find %@": "Encuentra %@",
    "Find 1,000 words.": "Encuentra 1.000 palabras.",
    "Find 10,000 words.": "Encuentra 10.000 palabras.",
    "Find 100 words.": "Encuentra 100 palabras.",
    "Find every word of a theme in the album.": "Encuentra todas las palabras de un tema del álbum.",
    "Finish a Hard puzzle without hints in under 10 minutes.": "Termina una sopa Difícil sin pistas en menos de 10 minutos.",
    "Finish your first puzzle.": "Termina tu primera sopa.",
    "First Puzzle": "Primera sopa",
    "Found": "Encontradas",
    "Friends": "Amigos",
    "Game": "Juego",
    "Game Center": "Game Center",
    "Games, the album, streaks and rewards are erased here and in iCloud. Sound and settings stay.":
        "Se borran las partidas, el álbum, las rachas y las recompensas, aquí y en iCloud. El sonido y los ajustes se quedan.",
    "Gentle": "Suave",
    "Global": "Global",
    "Gold": "Dorado",
    "Gold icon": "Ícono dorado",
    "Guess": "Adivinar",
    "Guess the hidden word": "Adivina la palabra oculta",
    "Guess the hidden word before the end, without hints.": "Adivina la palabra oculta antes de terminar, sin pistas.",
    "Guess the hidden word, %lld points": "Adivinar la palabra oculta, %lld puntos",
    "Guessed · +%lld": "Adivinada · +%lld",
    "Habit": "Hábito",
    "Haptics": "Hápticos",
    "Hard": "Difícil",
    "Hidden word": "Palabra oculta",
    "Hidden word: %@": "Palabra oculta: %@",
    "High contrast": "Alto contraste",
    "Highlighter": "Resaltador",
    "Hint": "Pista",
    "Hint, costs %lld points": "Pista, cuesta %lld puntos",
    "Hints": "Pistas",
    "Home": "Inicio",
    "How to play": "Cómo jugar",
    "In Plain Sight": "A simple vista",
    "In progress": "A medias",
    "Indigo & peach": "Índigo y durazno",
    "Indigo & peach theme": "Tema Índigo y durazno",
    "Ink": "Tinta",
    "Ink marker": "Marcador Tinta",
    "Larger letters": "Letras más grandes",
    "Leaderboards": "Clasificaciones",
    "Marker": "Marcador",
    "Medium": "Medio",
    "Menu": "Menú",
    "Missing": "Faltan",
    "Missing words show only their length.": "Las que faltan solo muestran su largo.",
    "Mono": "Mono",
    "Mono letters": "Letra Mono",
    "New Game": "Nueva partida",
    "New best": "Nuevo récord",
    "New in the album": "Nuevas en el álbum",
    "Next challenge in %@": "Siguiente reto en %@",
    "Next month": "Mes siguiente",
    "No scores yet": "Todavía no hay puntajes",
    "Now %@": "Ahora %@",
    "Open Settings": "Abrir Ajustes",
    "Opens the album": "Abre el álbum",
    "Opens their profile": "Abre su perfil",
    "Or tap its first letter, then its last. Words can run down and diagonally too.":
        "O toca su primera letra y luego la última. Las palabras también van hacia abajo y en diagonal.",
    "Palabras en español": "Palabras en español",
    "Pencil": "Lápiz",
    "Pencil marker": "Marcador Lápiz",
    "Play": "Jugar",
    "Play %@": "Jugar %@",
    "Play a surprise": "Jugar una sorpresa",
    "Play again": "Jugar otra vez",
    "Play today in both languages: each one has its leaderboard, and the streak counts the day once. Past days don't go to the leaderboard, but finishing one counts toward your streak.":
        "Juega el de hoy en los dos idiomas: cada uno tiene su clasificación y la racha cuenta el día una vez. Los días pasados no van a la clasificación, pero terminar uno cuenta para tu racha.",
    "Play with friends": "Juega con tus amigos",
    "Previous month": "Mes anterior",
    "Privacy": "Privacidad",
    "Puzzle complete!": "¡Sopa completa!",
    "Puzzles": "Sopas",
    "Quiet Words collects nothing. Your games, album and progress stay on this device; with Game Center they also sync through your Apple account.":
        "Quiet Words no recolecta nada. Tus partidas, tu álbum y tu progreso se quedan en este dispositivo; con Game Center también se sincronizan con tu cuenta de Apple.",
    "Replay · %@ · %@": "Repetición · %1$@ · %2$@",
    "Reset all progress?": "¿Restablecer todo el progreso?",
    "Reset progress": "Restablecer progreso",
    "Revealed · +%lld": "Revelada · +%lld",
    "Rewards": "Recompensas",
    "Rounded": "Redonda",
    "Sage & coral": "Salvia y coral",
    "Sage & coral theme": "Tema Salvia y coral",
    "Sage & sand": "Salvia y arena",
    "Sage & sand theme": "Tema Salvia y arena",
    "Score %lld": "Puntaje %lld",
    "Scores from Game Center · updated just now": "Puntajes de Game Center · recién actualizados",
    "Serif": "Serif",
    "Serif letters": "Letra Serif",
    "Settings": "Ajustes",
    "Sign in": "Iniciar sesión",
    "Sign in to Game Center in Settings to see leaderboards. Your progress is saved on this device either way.":
        "Inicia sesión en Game Center en Ajustes para ver las clasificaciones. Tu progreso se guarda en este dispositivo de todas formas.",
    "Signed in": "Conectado",
    "Skip": "Saltar",
    "Sound": "Sonido",
    "Spanish": "español",
    "Standard": "Estándar",
    "Start over": "Empezar de nuevo",
    "Start playing": "Empezar a jugar",
    "Start this puzzle over?": "¿Empezar esta sopa de nuevo?",
    "Steady Hand": "Pulso firme",
    "Step %lld of %lld": "Paso %1$lld de %2$lld",
    "Streak": "Racha",
    "Streak %lld": "Racha %lld",
    "Surprise": "Sorpresa",
    "Teal & coral": "Petróleo y coral",
    "Ten Thousand Words": "Diez mil palabras",
    "The app stays in your language. You can change this anytime where you start a game, or in Settings.":
        "La app sigue en tu idioma. Puedes cambiarlo cuando quieras donde empiezas una partida, o en Ajustes.",
    "The hidden word": "La palabra oculta",
    "The leftover letters": "Las letras que sobraban",
    "The letters no word uses spell a word of the theme. Guess it early for +100, or it shows up at the end.":
        "Las letras que no usa ninguna palabra forman una palabra del tema. Adivínala antes por +100, o aparece al final.",
    "Theme": "Tema",
    "Theme Complete": "Tema completo",
    "Themed lists in English": "Listas temáticas en inglés",
    "Themed lists in Spanish, with Ñ": "Listas temáticas en español, con Ñ",
    "Time": "Tiempo",
    "Time bonus": "Bono por tiempo",
    "Today": "Hoy",
    "Today's challenge is waiting.": "El reto de hoy te espera.",
    "Underline": "Subrayado",
    "Underline marker": "Marcador Subrayado",
    "Version": "Versión",
    "Which words do you want to find?": "¿Qué palabras quieres encontrar?",
    "Won later": "Ganado después",
    "Word language": "Idioma de las palabras",
    "Words": "Palabras",
    "Words in %@": "Palabras en %@",
    "Words in %@, today's challenge done": "Palabras en %@, reto de hoy hecho",
    "Words only; the app stays in your language. Each language keeps its own album.":
        "Solo las palabras; la app sigue en tu idioma. Cada idioma tiene su propio álbum.",
    "You left %@ halfway.": "Dejaste %@ a medias.",
    "Your 5-minute break.": "Tu pausa de 5 minutos.",
    "Your streak": "Tu racha",
    "guessed early · +%lld": "adivinada antes · +%lld",
    "revealed · +%lld": "revelada · +%lld",
    "Look Up": "Definición",
    "Appearance": "Apariencia",
    "Match system": "Igual que el sistema",
    "Light": "Claro",
    "Dark": "Oscuro",
    "Missing words show only their length. Tap a word with a book to see its definition.":
        "Las que faltan solo muestran su largo. Toca una palabra con un libro para ver su definición.",
    "Shows the definition": "Muestra la definición",
    "of %lld · badges: %lld": "de %1$lld · insignias: %2$lld",
    "Hint": "Pista",
}

KIT = {
    "Remind you at %@?": "¿Te aviso a las %@?",
    "Remind me": "Avísame",
    "Not now": "Ahora no",
    "Only on days you haven't played the challenge yet. You can change it in Settings.":
        "Solo los días que aún no hayas jugado el reto. Puedes cambiarlo en Ajustes.",
    "Daily challenge reminder": "Recordatorio del reto",
    "At %@ if you haven't played; with a streak of 3 or more, another at %@.":
        "A las %1$@ si no has jugado; con una racha de 3 o más, otro a las %2$@.",
    "Notifications are off for this app. Turn them on in Settings.":
        "Las notificaciones de esta app están desactivadas. Actívalas en Ajustes.",
    "Friends today": "Amigos hoy",
    "You're #%lld": "Vas #%lld",
    "None of your friends has played today's challenge yet.": "Ninguno de tus amigos ha jugado el reto de hoy.",
    "Couldn't load this profile. Check your connection and try again.":
        "No se pudo cargar este perfil. Revisa tu conexión e inténtalo de nuevo.",
    "View in Game Center": "Ver en Game Center",
    "You": "Tú",
    "Today's challenge": "Reto de hoy",
    "Best score": "Mejor puntaje",
    "Longest streak": "Racha más larga",
    "Last %lld challenges": "Últimos %lld retos",
    "You won %lld of %lld": "Ganaste %1$lld de %2$lld",
    "No past challenges yet.": "Todavía no hay retos anteriores.",
}


# Plurals: key → (en one, en other, es one, es other).
PLURALS = {
    "%lld friends already played; %@ leads with %@.": ("%lld friend already played; %@ leads with %@.", "%lld friends already played; %@ leads with %@.", "%1$lld amigo ya jugó; %2$@ va primero con %3$@.", "%1$lld amigos ya jugaron; %2$@ va primero con %3$@."),
    "%lld letters · the leftover ones": ("%lld letter · the leftover one", "%lld letters · the leftover ones", "%lld letra · la que sobra", "%lld letras · las que sobran"),
    "%lld streak": ("%lld-day streak", "%lld-day streak", "Racha de %lld día", "Racha de %lld días"),
    "%lld tries left · a miss costs nothing": ("%lld try left · a miss costs nothing", "%lld tries left · a miss costs nothing", "Queda %lld intento · fallar no cuesta", "Quedan %lld intentos · fallar no cuesta"),
    "%lld words": ("%lld word", "%lld words", "%lld palabra", "%lld palabras"),
    "%lld words to the badge": ("%lld word to the badge", "%lld words to the badge", "Falta %lld palabra para la insignia", "Faltan %lld palabras para la insignia"),
    "Don't lose your %lld-day streak.": ("Don't lose your %lld-day streak.", "Don't lose your %lld-day streak.", "No pierdas tu racha de %lld día.", "No pierdas tu racha de %lld días."),
    "Hidden word, %lld letters": ("Hidden word, %lld letter", "Hidden word, %lld letters", "Palabra oculta, %lld letra", "Palabra oculta, %lld letras"),
    "Missing word, %lld letters": ("Missing word, %lld letter", "Missing word, %lld letters", "Palabra que falta, %lld letra", "Palabra que falta, %lld letras"),
    "Your %lld-day streak ends at midnight.": ("Your %lld-day streak ends at midnight.", "Your %lld-day streak ends at midnight.", "Tu racha de %lld día termina a la medianoche.", "Tu racha de %lld días termina a la medianoche."),
}

# Not translated.
VERBATIM = {""}


def unit(value):
    return {"stringUnit": {"state": "translated", "value": value}}


def plural(one, other):
    return {"variations": {"plural": {"one": unit(one), "other": unit(other)}}}


def extracted_keys():
    keys = set()
    for f in (ROOT / "build/dd").rglob("*.stringsdata"):
        if "QuietWords.build" not in str(f):
            continue
        data = json.loads(f.read_text())
        for entries in data.get("tables", {}).values():
            keys.update(e["key"] for e in entries)
    return keys


def main():
    strings = {}
    used = extracted_keys()
    # Only what the app uses (the extracted keys) plus the kit's texts: no stale entries.
    for key, es in ES.items():
        if key in used:
            strings[key] = {"localizations": {"es": unit(es)}}
    for key, es in KIT.items():
        strings[key] = {"localizations": {"es": unit(es)}}
    for key, (en_one, en_other, es_one, es_other) in PLURALS.items():
        if key not in used:
            continue
        strings[key] = {"localizations": {"en": plural(en_one, en_other), "es": plural(es_one, es_other)}}
    for key in VERBATIM:
        strings[key] = {"shouldTranslate": False}
    catalog = {"sourceLanguage": "en", "strings": dict(sorted(strings.items())), "version": "1.0"}
    (ROOT / "App/Resources/Localizable.xcstrings").write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n")

    info = {"sourceLanguage": "en", "version": "1.0", "strings": {
        "CFBundleDisplayName": {"shouldTranslate": False},
        "CFBundleName": {"shouldTranslate": False},
        "NSGKFriendListUsageDescription": {"localizations": {
            "en": unit("Show your Game Center friends on Quiet Words leaderboards."),
            "es": unit("Muestra a tus amigos de Game Center en las clasificaciones de Quiet Words.")}},
    }}
    (ROOT / "App/Resources/InfoPlist.xcstrings").write_text(json.dumps(info, ensure_ascii=False, indent=2) + "\n")

    missing = sorted(used - set(strings))
    for key in missing:
        print(f"Missing Spanish: {key!r}")
    if missing:
        sys.exit(1)
    print(f"{len(strings)} strings written; every extracted key has Spanish.")


if __name__ == "__main__":
    main()
