package com.tavoohoh.quiet.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// MARK: Surfaces

/** `surface` (or `fill`), plus a 2 dp ink border in High contrast. */
@Composable
fun Modifier.quietSurface(shape: Shape, fill: Color? = null): Modifier {
    val colors = QuietTheme.colors
    val base = background(fill ?: colors.surface, shape)
    return if (colors.isHighContrast) base.border(2.dp, colors.ink, shape) else base
}

/** A card: `surface`, 24 dp corners. */
@Composable
fun Modifier.quietCard(padding: Dp = 18.dp, cornerRadius: Dp = 24.dp): Modifier =
    quietSurface(RoundedCornerShape(cornerRadius)).padding(padding)

// MARK: Buttons

/** Clickable with the family's pressed state (75 % opacity) and no ripple. */
@Composable
fun Modifier.quietClickable(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    return this
        .alpha(if (pressed) 0.75f else 1f)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}

/**
 * Full-width capsule, 56 dp. Prominent: `primary` with `onPrimary` text; otherwise
 * `surface2` with `primary` text. Disabled at 40 %.
 */
@Composable
fun CapsuleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = true,
    enabled: Boolean = true,
    /** Replaces what TalkBack reads from the label ("Hint, costs 30 points"). */
    description: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = QuietTheme.colors
    val fill = if (prominent) colors.primary else colors.surface2
    val text = if (prominent) colors.onPrimary else colors.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .background(fill, CircleShape)
            .quietClickable(enabled = enabled, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides text, LocalTextStyle provides QuietType.headline) {
            content()
        }
    }
}

/** A capsule button with a single text label. */
@Composable
fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = true,
    enabled: Boolean = true,
) = CapsuleButton(onClick, modifier, prominent, enabled) {
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** 48 dp round button on `surface` (back, more). */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val colors = QuietTheme.colors
    Box(
        modifier = modifier
            .size(48.dp)
            .quietSurface(CircleShape)
            .quietClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint ?: colors.ink, modifier = Modifier.size(22.dp))
    }
}

// MARK: Segments

/** Capsule segmented control: the selected segment is `ink` with `background` text. */
@Composable
fun <T> Segments(
    selection: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QuietTheme.colors
    Row(modifier = modifier.quietSurface(CircleShape).padding(3.dp)) {
        for ((value, title) in options) {
            val selected = value == selection
            val fill by animateColorAsState(if (selected) colors.ink else Color.Transparent, label = "segment")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(fill, CircleShape)
                    .quietClickable { onSelect(value) }
                    .semantics { this.selected = selected },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title, style = QuietType.subheadline.copy(fontWeight = FontWeight.SemiBold), maxLines = 1,
                    color = if (selected) colors.background else colors.muted,
                )
            }
        }
    }
}

// MARK: Screen chrome

/**
 * Pushed screens: our own header (round back button, title, optional trailing item) over
 * the theme's background, inside the safe area (the app draws edge to edge).
 */
@Composable
fun QuietScreen(
    title: String?,
    onBack: () -> Unit,
    backDescription: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val colors = QuietTheme.colors
    Column(modifier.fillMaxSize().background(colors.background)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp)) {
            RoundIconButton(QuietIcons.Back, backDescription, onBack, Modifier.align(Alignment.CenterStart))
            if (title != null) {
                Text(
                    title, style = QuietType.headline, color = colors.ink, maxLines = 1,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 60.dp).semantics { heading() },
                )
            }
            Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

/** A small capsule in `accent` with `onAccent` text (iOS `AccentButtonStyle`): Play on a `primary` card. */
@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = QuietTheme.colors
    Box(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .background(colors.accent, CircleShape)
            .quietClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = QuietType.headline, color = colors.onAccent, maxLines = 1)
    }
}
