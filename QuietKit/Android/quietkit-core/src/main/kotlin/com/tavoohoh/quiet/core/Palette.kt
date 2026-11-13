package com.tavoohoh.quiet.core

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** The family's color themes, by role, as (light, dark) hex values. Port of QuietCore's `Palette`. */
enum class Palette(val rawValue: String) {
    TEAL_CORAL("tealCoral"), SAGE_SAND("sageSand"), INDIGO_PEACH("indigoPeach"), SAGE_CORAL("sageCoral"),
    HIGH_CONTRAST("highContrast");

    enum class Role { BACKGROUND, SURFACE, SURFACE2, INK, MUTED, PRIMARY, ON_PRIMARY, ACCENT, GHOST, SEPARATOR, SUIT_RED }

    data class Hex(val light: String, val dark: String)

    /** Surfaces get a border, so nothing depends on color alone. */
    val isHighContrast: Boolean get() = this == HIGH_CONTRAST

    fun hex(role: Role): Hex = VALUES.getValue(this).getValue(role)

    companion object {
        fun fromRaw(raw: String): Palette? = entries.firstOrNull { it.rawValue == raw }

        private fun roles(vararg pairs: Pair<String, String>): Map<Role, Hex> =
            Role.entries.zip(pairs.toList()).associate { (role, p) -> role to Hex(p.first, p.second) }

        // Order: background, surface, surface2, ink, muted, primary, onPrimary, accent, ghost, separator, suitRed.
        private val VALUES: Map<Palette, Map<Role, Hex>> = mapOf(
            TEAL_CORAL to roles(
                "#F3F7F8" to "#0F1A1D", "#FFFFFF" to "#172629", "#E3EEF0" to "#1F3236", "#16201F" to "#E8F1F2",
                "#5E6F72" to "#8A9DA0", "#0E6B78" to "#5BC2CF", "#FFFFFF" to "#062327", "#B9472F" to "#FF9A85",
                "#CFD8DA" to "#2C3A3D", "#E3EAEC" to "#263A3E", "#B9472F" to "#FF9A85",
            ),
            SAGE_SAND to roles(
                "#F6F4EE" to "#161A17", "#FFFFFF" to "#1F2420", "#ECE9E0" to "#2A302B", "#1F2622" to "#ECEFE8",
                "#5F665F" to "#9AA39A", "#387454" to "#7FC79E", "#FFFFFF" to "#0F2418", "#9A5B16" to "#E0A45C",
                "#D5D3CB" to "#343A35", "#E6E2D8" to "#2C322D", "#B4503A" to "#F08C74",
            ),
            INDIGO_PEACH to roles(
                "#F5F4FB" to "#13121F", "#FFFFFF" to "#1C1B2C", "#E9E7F5" to "#27253A", "#1A1930" to "#ECEBF7",
                "#5E5C78" to "#9593AE", "#4B48C4" to "#9A98FF", "#FFFFFF" to "#15133A", "#B4521F" to "#FFAE82",
                "#D3D1E4" to "#302E44", "#E4E2F0" to "#2A2840", "#B4452A" to "#FFAE82",
            ),
            SAGE_CORAL to roles(
                "#F7F3EC" to "#171A17", "#FFFFFF" to "#202420", "#EFEAE0" to "#2B302B", "#1E2420" to "#EDF0EA",
                "#646A64" to "#9CA49C", "#4D7157" to "#8DBF9A", "#FFFFFF" to "#13241A", "#B4503A" to "#F0927A",
                "#D9D4CA" to "#353A35", "#E8E2D6" to "#2D322D", "#B4503A" to "#F0927A",
            ),
            HIGH_CONTRAST to roles(
                "#FFFFFF" to "#000000", "#FFFFFF" to "#000000", "#E6EEF9" to "#14233A", "#000000" to "#FFFFFF",
                "#3A3A3C" to "#D1D1D6", "#003F91" to "#6FB1FF", "#FFFFFF" to "#000000", "#B85500" to "#FF9F45",
                "#B0B0B5" to "#48484A", "#000000" to "#FFFFFF", "#B3001B" to "#FF6B6B",
            ),
        )
    }
}

/** WCAG contrast between two `#RRGGBB` colors. */
object Contrast {
    fun ratio(a: String, b: String): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Red, green, blue in 0..1. */
    fun components(hex: String): Triple<Double, Double, Double> {
        val value = hex.drop(1).toLongOrNull(16) ?: 0L
        return Triple(((value shr 16) and 0xFF) / 255.0, ((value shr 8) and 0xFF) / 255.0, (value and 0xFF) / 255.0)
    }

    private fun luminance(hex: String): Double {
        fun channel(c: Double) = if (c <= 0.03928) c / 12.92 else exp(2.4 * ln((c + 0.055) / 1.055))
        val (r, g, b) = components(hex)
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }
}
