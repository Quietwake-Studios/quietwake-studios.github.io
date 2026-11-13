# Quiet Words en Android

Proyecto Gradle de Quiet Words para Android. Cada app tiene su carpeta `Android/` junto a la de iOS (decisión 2026-11-13); los demás juegos seguirán el mismo esquema cuando se decida. La filosofía, el reto diario y las reglas del juego son los de `../../CLAUDE.md` y `../CLAUDE.md`: este archivo solo dice lo que cambia en Android.

## Decisiones (2026-11-13)

- **Kotlin + Jetpack Compose, nativo.** El motor de cada juego se porta de Swift a Kotlin; iOS no se toca. No se usa Kotlin Multiplatform ni Flutter.
- **El mismo reto en las dos plataformas:** un mismo día da el mismo tablero en iOS y en Android. Se verifica con **fixtures generados desde Swift** (`fixtures/` de cada proyecto): vectores de FNV-1a, SplitMix64 y claves de día en `QuietKit/Android/fixtures/`; normalización, temas, retos y tableros libres en `fixtures/`. Si un test de fixtures falla, manda Swift y se corrige Kotlin.
- **Una sola fuente de contenido:** los temas, la lista de bloqueo y `daily-lock.json` se leen de los recursos del paquete de iOS (`Packages/QuietWordsCore/Sources/QuietWordsCore/Resources/`; Gradle los toma como recursos, no se copian). La ruta está en un solo lugar: `quietWords.iosResources` en `gradle.properties`. El candado del reto aplica igual a las dos plataformas.
- **Clasificaciones y logros:** Google Play Games Services v2, con los mismos IDs de concepto que Game Center (`qw_daily_en`…); los jugadores de iOS y Android no se ven entre sí.
- **Cuenta de Google Play personal** (la empresa no está registrada, así que no hay D-U-N-S). Las cuentas personales nuevas deben pasar una **prueba cerrada con al menos 12 testers durante 14 días seguidos** antes de pedir producción.
- Paquete `com.tavoohoh.quietwords` (el mismo nombre del bundle de iOS).
- **Dependencias permitidas:** stdlib de Kotlin, kotlinx (serialización, coroutines), AndroidX/Jetpack y Play Games. Nada más: sin Firebase, analytics, anuncios ni SDKs de terceros. Data safety: declarar solo lo que recoja Play Games.
- minSdk 26, targetSdk/compileSdk 36. JDK 17 (JBR en `~/Library/Java/JavaVirtualMachines/jbr-17.0.12`). Gradle wrapper 8.14.3 (ya está en el caché).

## Estructura

```
quiet-words/Android/
├── settings.gradle.kts · build.gradle.kts · gradle.properties · gradle/libs.versions.toml · gradlew
├── words-core/      Kotlin JVM puro: port de QuietWordsCore (palabras, temas, generador, reto, sesión, progreso, álbum)
├── words-app/       App Android (Compose), Fase 2 (todavía no existe)
└── fixtures/        JSON generados desde Swift (no se editan a mano)

QuietKit/Android/
├── quietkit-core/   Kotlin JVM puro: FNV1a, SeededRandom (SplitMix64), DailyKey, Streaks, Palette, DailyReminderPlan
└── fixtures/        core.json, generado desde Swift
```

`quietkit-core` se incluye desde `settings.gradle.kts` (`projectDir = ../../QuietKit/Android/quietkit-core`) y usa el catálogo de versiones del proyecto que lo incluye. Paquetes: `com.tavoohoh.quiet.core` y `com.tavoohoh.quietwords.core`. Más adelante, `quietkit-ui` (tema, componentes, hápticos, Play Games) junto a `quietkit-core`.

## Comandos

Con `JAVA_HOME` en el JBR 17:

- Tests: `./gradlew test` (desde esta carpeta; corre `quietkit-core` y `words-core`, ~40 s).
- Regenerar fixtures desde Swift (solo cuando cambie a propósito el motor o el contenido de iOS; luego `./gradlew test` debe pasar; cada línea desde esta carpeta):
  - `cd ../../QuietKit && QUIET_WRITE_FIXTURES=$PWD/Android/fixtures swift test --filter FixtureWriter`
  - `cd ../Packages/QuietWordsCore && QUIET_WRITE_FIXTURES=$PWD/../../Android/fixtures swift test -c release --filter FixtureWriter`
  - Sin la variable, el test `FixtureWriter` de Swift no hace nada.
- Al mover los archivos de iOS a `iOS/`: cambiar solo `quietWords.iosResources` en `gradle.properties` y las rutas de arriba.

## Fases (no avanzar sin cumplir el criterio)

1. **Motor:** Gradle, `quietkit-core` y `words-core` con tests. Acepta: los fixtures de Swift pasan (hash, PRNG y al menos 20 retos en/es idénticos letra por letra) y 1.000 tableros libres válidos.
2. **Jugable:** tablero con arrastre y toque, palabra oculta, pista, resumen, tutorial con el idioma, tema y apariencia del canvas de Words. Acepta: una partida completa en el emulador (teléfono y tablet).
3. **Persistencia y reto diario:** guardado, racha, calendario, recordatorios locales. Acepta: tests de la regla del reto y de recordatorios.
4. **Colección y ajustes:** álbum, recompensas, logros, Números rojos.
5. **Play Games:** logros y 5 clasificaciones. Acepta: funciona sin conexión y envía al reconectar (en un dispositivo).
6. **Publicación:** ficha de Play Store en/es, capturas, Data safety, clasificación de contenido (IARC), prueba cerrada de 12 testers × 14 días, producción.

## Estado

- 2026-11-13: plan aprobado (Kotlin + Compose, Play Games desde el inicio, cuenta personal).
- 2026-11-13: **Fase 1 cumplida.** Kotlin 2.3.21, kotlinx-serialization 1.10.0, JUnit 5.14.4, Gradle 8.14.3 (AGP 8.13.0 en el catálogo para la Fase 2). 55 tests pasan (25 en `quietkit-core`, 30 en `words-core`): los de Swift portados más los fixtures, idénticos a Swift letra por letra: FNV-1a (17 textos, con tildes, Ñ y emoji), SplitMix64 y `nextInt` (7 semillas), 500 claves de día, todas las palabras de los 11 temas en/es, 324 retos (2026-10-25 a 2027-03-31 y 4 fechas lejanas, en/es) y 148 tableros libres. 3.000 tableros libres válidos (1.000 por nivel). Generación en JVM (Mac, tras calentar): Suave 0,4 ms, Medio 1,4 ms, Difícil 2,2 ms por tablero. Sin divergencias Swift/Kotlin.
