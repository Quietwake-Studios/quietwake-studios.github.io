package com.tavoohoh.quiet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.tavoohoh.quiet.core.SeededRandom
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Classic fireworks for a win (port of QuietUI's `Fireworks`): rockets rise from the bottom,
 * burst into sparks that fall with gravity and fade. Drawn from the time since start with our
 * seeded random, so a seed always looks the same. Doesn't take touches. With Reduce Motion it
 * draws nothing.
 */
@Composable
fun Fireworks(colors: List<Color>, modifier: Modifier = Modifier, rockets: Int = 12, seed: ULong = 7uL) {
    if (QuietTheme.reduceMotion || colors.isEmpty()) return
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (time < fireworksDuration(rockets)) {
            withFrameNanos { time = (it - start) / 1_000_000_000f }
        }
    }
    Canvas(modifier.clearAndSetSemantics {}) {
        val list = makeRockets(size, rockets, colors.size, seed)
        for (rocket in list) draw(rocket, time - rocket.launch, colors, this)
    }
}

/** When the last spark is gone (seconds). */
fun fireworksDuration(rockets: Int = 12): Double = (rockets - 1) * STAGGER + RISE + SPARK_LIFE

private const val STAGGER = 0.26
private const val RISE = 0.7
private const val SPARK_LIFE = 1.4
private const val SPARKS = 40
private const val GRAVITY = 220.0

private class Rocket(val launch: Double, val x: Double, val burst: Offset, val speed: Double, val color: Int, val twinkle: Boolean)

private fun makeRockets(size: Size, count: Int, colors: Int, seed: ULong): List<Rocket> {
    val random = SeededRandom(seed)
    fun unit() = random.nextInt(0..10_000) / 10_000.0
    return (0 until count).map { i ->
        val x = size.width * (0.15 + 0.7 * unit())
        Rocket(
            launch = i * STAGGER,
            x = x,
            burst = Offset((x + (unit() - 0.5) * size.width * 0.2).toFloat(), (size.height * (0.15 + 0.3 * unit())).toFloat()),
            speed = 140 + 90 * unit(),
            color = random.nextInt(0..maxOf(colors, 1) - 1),
            twinkle = unit() > 0.5,
        )
    }
}

private fun draw(rocket: Rocket, t: Double, colors: List<Color>, scope: DrawScope) = with(scope) {
    if (t < 0) return@with
    val d = density
    val color = colors[rocket.color]
    if (t < RISE) {
        val p = 1 - (1 - t / RISE).pow(2)
        val y = size.height + (rocket.burst.y - size.height) * p
        val x = rocket.x + (rocket.burst.x - rocket.x) * p
        drawLine(
            color.copy(alpha = 0.8f), Offset(x.toFloat(), y.toFloat()),
            Offset((x - (rocket.burst.x - rocket.x) * 0.06).toFloat(), (y + 18 * d).toFloat()),
            strokeWidth = 2 * d, cap = StrokeCap.Round,
        )
        return@with
    }
    val s = t - RISE
    if (s >= SPARK_LIFE) return@with
    val fade = (1 - s / SPARK_LIFE).toFloat()
    if (s < 0.15) {
        drawCircle(color.copy(alpha = (0.35 * (1 - s / 0.15)).toFloat()), radius = (44 * d * (s / 0.15)).toFloat(), center = rocket.burst)
    }
    for (j in 0 until SPARKS) {
        val angle = j.toDouble() / SPARKS * 2 * PI + rocket.color * 0.3
        val v = rocket.speed * d * (if (j % 2 == 0) 1.0 else 0.72)
        val drag = (1 - exp(-1.8 * s)) / 1.8
        val fall = 0.5 * GRAVITY * d * s * s
        val x = rocket.burst.x + cos(angle) * v * drag
        val y = rocket.burst.y + sin(angle) * v * drag + fall
        val tailX = rocket.burst.x + cos(angle) * v * drag * 0.78
        val tailY = rocket.burst.y + sin(angle) * v * drag * 0.78 + fall * 0.9
        val blink = if (rocket.twinkle && s > 0.6) (if (sin(s * 40 + j) > 0) 1f else 0.3f) else 1f
        val c = color.copy(alpha = (fade * blink).coerceIn(0f, 1f))
        drawLine(c, Offset(tailX.toFloat(), tailY.toFloat()), Offset(x.toFloat(), y.toFloat()), strokeWidth = 3 * d, cap = StrokeCap.Round)
        drawCircle(c, radius = 1.8f * d, center = Offset(x.toFloat(), y.toFloat()))
    }
}
