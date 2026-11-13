package com.tavoohoh.quietwords.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.ui.graphics.vector.ImageVector

/** The theme's SF Symbol (from its JSON) as the closest Material icon. */
fun themeIcon(symbol: String): ImageVector = when (symbol) {
    "testtube.2" -> Icons.Outlined.Science
    "leaf" -> Icons.Outlined.Eco
    "cup.and.saucer" -> Icons.Outlined.LocalCafe
    "fork.knife" -> Icons.Outlined.Restaurant
    "globe.americas" -> Icons.Outlined.Public
    "airplane" -> Icons.Outlined.Flight
    "mountain.2" -> Icons.Outlined.Landscape
    "music.note" -> Icons.Outlined.MusicNote
    "water.waves" -> Icons.Outlined.Waves
    "building.columns" -> Icons.Outlined.AccountBalance
    "moon.stars" -> Icons.Outlined.NightsStay
    "shuffle" -> Icons.Outlined.Shuffle
    else -> Icons.Outlined.Category
}
