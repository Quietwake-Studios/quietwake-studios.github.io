package com.tavoohoh.quietnumbers.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quietnumbers.R
import com.tavoohoh.quietnumbers.core.Board
import com.tavoohoh.quietnumbers.core.Cell
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The grid (port of iOS `BoardView`): rows identified by their first cell (rows only ever
 * leave whole, so the id stays stable while the board collapses and grows). Numbers can be
 * tapped one after the other, or dragged from one to the other; a drag from a ghost or a
 * gap scrolls the board.
 */
@Composable
fun BoardView(model: GameModel, cellWidth: Dp, cellHeight: Dp, modifier: Modifier = Modifier) {
    val colors = QuietTheme.colors
    val density = LocalDensity.current
    val cells = model.board.cells
    val rows = (cells.indices step Board.COLUMNS).map { cells[it].id }
    val listState = rememberLazyListState()
    val rowPx = with(density) { cellHeight.toPx() }
    val colPx = with(density) { cellWidth.toPx() }
    val topPadding = 8.dp
    val topPx = with(density) { topPadding.toPx() }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragAt by remember { mutableStateOf<Offset?>(null) }

    // + scrolls to the new numbers; a hint to its row.
    LaunchedEffect(model.additions) {
        if (model.additions > 0) listState.animateScrollToItem(maxOf(0, rows.size))
    }
    LaunchedEffect(model.hintIDs) {
        val index = cells.indexOfFirst { it.id in model.hintIDs }
        if (index >= 0) listState.animateScrollToItem(maxOf(0, index / Board.COLUMNS - 2))
    }

    /** Scroll position in px from the top of the grid (all rows are the same height). */
    fun scrollY(): Float = listState.firstVisibleItemIndex * rowPx + listState.firstVisibleItemScrollOffset - topPx

    fun cellAt(point: Offset): Cell? {
        val y = point.y + scrollY()
        if (point.x < 0 || y < 0 || point.x >= colPx * Board.COLUMNS) return null
        val index = (y / rowPx).toInt() * Board.COLUMNS + (point.x / colPx).toInt()
        return model.board.cells.getOrNull(index)
    }

    Box(
        modifier
            .width(cellWidth * Board.COLUMNS)
            .testTag("board")
            .pointerInput(model, rowPx, colPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val from = cellAt(down.position)?.takeIf { !it.isCleared } ?: return@awaitEachGesture
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (dragging) {
                                change.consume()
                                cellAt(change.position)?.takeIf { it.id != from.id && !it.isCleared }?.let(model::tap)
                            }
                            break
                        }
                        if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            dragging = true
                            dragFrom = from.id
                            model.beginDrag(from)
                        }
                        if (dragging) {
                            change.consume()
                            dragAt = change.position
                        }
                    }
                    dragFrom = null
                    dragAt = null
                }
            },
    ) {
        // Behind the numbers: the match line, scrolled with the grid.
        Canvas(Modifier.matchParentSize()) {
            val line = model.matchLine ?: return@Canvas
            val y0 = -scrollY()
            val width = when (line.tier) {
                MatchLine.Tier.SUBTLE -> 2f
                MatchLine.Tier.MEDIUM -> 3f
                MatchLine.Tier.DRAMATIC -> 4f
            } * density.density
            val alpha = if (line.tier == MatchLine.Tier.SUBTLE) 0.6f else 1f
            for ((from, to) in line.segments) {
                drawLine(
                    colors.primary.copy(alpha = alpha), Offset(from.x * colPx, y0 + from.y * rowPx), Offset(to.x * colPx, y0 + to.y * rowPx),
                    strokeWidth = width, cap = StrokeCap.Round,
                )
            }
        }
        LazyColumn(state = listState, modifier = Modifier.matchParentSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = topPadding)) {
            items(rows, key = { it }) { firstID ->
                val start = model.board.indexOfCell(firstID) ?: return@items
                val end = minOf(start + Board.COLUMNS, cells.size)
                BoardRow(model, start, end, cellWidth, cellHeight, flashing = firstID in model.flashingRows, modifier = Modifier.animateItem())
            }
            item(key = "bottom") { Spacer(Modifier.height(cellHeight)) }
        }
        // Over the numbers: the drag line, the bursts and the rising points.
        Canvas(Modifier.matchParentSize()) {
            val id = dragFrom ?: return@Canvas
            val at = dragAt ?: return@Canvas
            val index = model.board.indexOfCell(id) ?: return@Canvas
            val start = Offset((index % Board.COLUMNS + 0.5f) * colPx, (index / Board.COLUMNS + 0.5f) * rowPx - scrollY())
            drawLine(colors.primary.copy(alpha = 0.5f), start, at, strokeWidth = 3 * density.density, cap = StrokeCap.Round)
        }
        val shift = -scrollY()
        for (pop in model.pops) {
            androidx.compose.runtime.key(pop.id) {
                PopView(pop, Offset(pop.center.x * colPx, pop.center.y * rowPx + shift), cellHeight.value / 46f)
            }
        }
        model.floatingPoints?.let { points ->
            androidx.compose.runtime.key(points.id) {
                FloatingPointsView(points, Offset(points.center.x * colPx, points.center.y * rowPx + shift), cellHeight) { model.dismissFloatingPoints(points) }
            }
        }
    }
}

@Composable
private fun BoardRow(model: GameModel, start: Int, end: Int, cellWidth: Dp, cellHeight: Dp, flashing: Boolean, modifier: Modifier) {
    val colors = QuietTheme.colors
    val cells = model.board.cells
    Box(modifier.width(cellWidth * Board.COLUMNS).height(cellHeight)) {
        if (flashing) RowClearEffect(cellHeight)
        Row {
            for (index in start until end) {
                val cell = cells[index]
                androidx.compose.runtime.key(cell.id) {
                    CellView(
                        model, cell, index, cellWidth, cellHeight,
                        isSelected = model.selectedID == cell.id || cell.id in model.connectingIDs,
                        isMissed = cell.id in model.missIDs,
                        isHinted = cell.id in model.hintIDs,
                        shakes = model.shakes[cell.id] ?: 0,
                        entranceDelay = model.addedFromID?.takeIf { cell.id >= it }?.let { minOf((cell.id - it) * 25L, 1200L - 160L) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CellView(
    model: GameModel, cell: Cell, index: Int, width: Dp, height: Dp, isSelected: Boolean, isMissed: Boolean,
    isHinted: Boolean, shakes: Int, entranceDelay: Long?,
) {
    val colors = QuietTheme.colors
    val reduceMotion = QuietTheme.reduceMotion
    val ring = height * 40f / 46f
    val label = stringResource(R.string.cell, cell.value, index / Board.COLUMNS + 1, index % Board.COLUMNS + 1)
    val cleared = stringResource(R.string.cleared)
    // Shake: ±4 dp, three times (240 ms) on each miss.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakes) {
        if (shakes == 0 || reduceMotion) return@LaunchedEffect
        shake.snapTo(0f)
        shake.animateTo(1f, tween(240, easing = LinearEasing))
        shake.snapTo(0f)
    }
    // Numbers just added with + scale and fade in one by one.
    val entrance = remember(cell.id) { Animatable(if (entranceDelay == null) 1f else 0f) }
    LaunchedEffect(cell.id) {
        if (entranceDelay != null && entrance.value < 1f) {
            delay(entranceDelay)
            entrance.animateTo(1f, tween(160))
        }
    }
    val color = when {
        cell.isCleared -> colors.ghost
        isMissed -> colors.accent
        isSelected -> colors.primary
        cell.isAdded -> colors.accent
        else -> colors.ink
    }
    Box(
        Modifier
            .size(width, height)
            .offset { IntOffset((4 * density * sin(shake.value * PI * 6)).roundToInt(), 0) }
            .graphicsLayer {
                alpha = entrance.value
                val s = if (reduceMotion) 1f else 0.8f + 0.2f * entrance.value
                scaleX = s
                scaleY = s
            }
            .clearAndSetSemantics {
                contentDescription = label
                if (cell.isCleared) stateDescription = cleared
                selected = isSelected
                role = Role.Button
                testTag = "cell-$index"
                if (!cell.isCleared) onClick { model.tap(cell); true }
            }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null, enabled = !cell.isCleared,
            ) { model.tap(cell) },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            isSelected || isMissed,
            enter = if (reduceMotion) fadeIn() else fadeIn() + scaleIn(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium), initialScale = 0.6f),
            exit = fadeOut(),
        ) {
            val c = if (isMissed) colors.accent else colors.primary
            Box(Modifier.size(ring).background(c.copy(alpha = 0.14f), CircleShape).border(2.dp, c, CircleShape))
        }
        if (isHinted) HintRing(ring)
        Text(
            "${cell.value}",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontFeatureSettings = QuietType.TABULAR,
                fontSize = with(LocalDensity.current) { (height * 27f / 46f).toSp() }, textAlign = TextAlign.Center,
            ),
            color = color,
        )
    }
}

/** Dashed ring on a hinted pair; blinks twice, then stays. */
@Composable
private fun HintRing(diameter: Dp) {
    val colors = QuietTheme.colors
    val reduceMotion = QuietTheme.reduceMotion
    val blink = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) return@LaunchedEffect
        repeat(2) {
            blink.animateTo(0.2f, tween(250))
            blink.animateTo(1f, tween(250))
        }
    }
    Canvas(Modifier.size(diameter).alpha(blink.value)) {
        val d = density
        drawCircle(colors.primary, size.minDimension / 2 - d, style = Stroke(2 * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * d, 3 * d))))
    }
}

/** The default row-clear effect: the row lights up while an outline draws around it. */
@Composable
private fun RowClearEffect(height: Dp) {
    val colors = QuietTheme.colors
    val reduceMotion = QuietTheme.reduceMotion
    val progress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(150)) }
    val shape = RoundedCornerShape(height * 0.35f)
    Box(
        Modifier.fillMaxSize().padding(2.dp)
            .background(colors.primary.copy(alpha = 0.14f * progress.value), shape)
            .border(2.dp, colors.primary.copy(alpha = progress.value), shape),
    )
}

/** A matched number bursting: 8 rays move out from 15 to 25 (at 54 across) and fade. */
@Composable
private fun PopView(pop: Pop, center: Offset, scale: Float) {
    val colors = QuietTheme.colors
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pop.id) { progress.animateTo(1f, tween(GameModel.POP_MILLIS.toInt())) }
    val sizeDp = 54.dp * scale
    val half = with(LocalDensity.current) { (sizeDp / 2).toPx() }
    Canvas(
        Modifier.size(sizeDp).offset { IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt()) }.alpha(1 - progress.value),
    ) {
        val unit = size.minDimension / 54
        val inner = (15 + 10 * progress.value - 5) * unit
        val outer = inner + 10 * unit
        val c = Offset(size.width / 2, size.height / 2)
        for (ray in 0 until 8) {
            val angle = ray / 8.0 * 2 * PI
            val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(if (pop.isLongShot) colors.accent else colors.primary, c + dir * inner, c + dir * outer, strokeWidth = 2.5f * unit, cap = StrokeCap.Round)
        }
    }
}

/** "+4": appears at the middle of the matched pair, rises 24 dp and fades. */
@Composable
private fun FloatingPointsView(points: FloatingPoints, center: Offset, cellHeight: Dp, onDone: () -> Unit) {
    val colors = QuietTheme.colors
    val rise = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(points.id) {
        delay(points.delayMillis)
        shown = true
        launch { rise.animateTo(1f, tween(600)) }
        delay(600)
        onDone()
    }
    if (!shown) return
    val density = LocalDensity.current
    val risePx = with(density) { 24.dp.toPx() }
    val fontSize = with(density) { (cellHeight * 0.6f).toSp() }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    Text(
        if (points.amount >= 0) "+${points.amount}" else "${points.amount}",
        style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Black, fontFeatureSettings = QuietType.TABULAR),
        color = colors.primary,
        modifier = Modifier
            .onSizeChanged { size = it }
            .offset { IntOffset((center.x - size.width / 2).roundToInt(), (center.y - size.height / 2 - rise.value * risePx).roundToInt()) }
            .alpha(1 - rise.value),
    )
}
