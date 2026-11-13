package com.tavoohoh.quiet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The studio's lockup: the "Q en pausa" mark (a ring and a rounded bar, on a 48 grid) over
 * "quietwake". The name is a brand: never translated. Colors are the lockup's own, not the palette's.
 */
@Composable
fun QuietwakeLockup(modifier: Modifier = Modifier, markSize: Dp = 80.dp) {
    val dark = QuietTheme.colors.isDark
    val ring = Color(if (dark) 0xFF5BC2CF else 0xFF0E6B78)
    val bar = Color(if (dark) 0xFFFF9A85 else 0xFFB9472F)
    val text = Color(if (dark) 0xFFE8F1F2 else 0xFF16201F)
    Column(
        modifier.clearAndSetSemantics { contentDescription = "quietwake" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Canvas(Modifier.size(markSize)) {
            val u = size.width / 48f
            drawCircle(ring, radius = 14 * u, center = Offset(21 * u, 22 * u), style = Stroke(width = 5 * u))
            drawLine(bar, Offset(31 * u, 38 * u), Offset(43 * u, 38 * u), strokeWidth = 5 * u, cap = StrokeCap.Round)
        }
        Text("quietwake", color = text, style = QuietType.title.copy(fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 0.sp))
    }
}

/**
 * The lockup for a moment on launch, on the theme's background (the system splash before it
 * shows only that background, so the hand-off is seamless). A tap skips it.
 */
@Composable
fun QuietSplash(onFinished: () -> Unit, durationMillis: Long = 1500) {
    LaunchedEffect(Unit) {
        delay(durationMillis)
        onFinished()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(QuietTheme.colors.background)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onFinished)
            .testTag("splash"),
        contentAlignment = Alignment.Center,
    ) {
        QuietwakeLockup()
    }
}
