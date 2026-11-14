package com.tavoohoh.quietwords.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.quietClickable
import com.tavoohoh.quiet.ui.quietSurface

/** A titled group of rows on `surface` (iOS QuietUI `GroupSection`). */
@Composable
fun GroupSection(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = QuietTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                title.uppercase(), style = QuietType.caption.copy(fontWeight = FontWeight.Bold), color = colors.muted,
                modifier = Modifier.padding(start = 16.dp).semantics { heading() },
            )
        }
        Column(Modifier.fillMaxWidth().quietSurface(RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 4.dp), content = content)
    }
}

@Composable
fun GroupDivider() = HorizontalDivider(thickness = 1.dp, color = QuietTheme.colors.separator.copy(alpha = 0.6f))

/** A row with a label, an optional value and a chevron when it opens something. */
@Composable
fun GroupRow(label: String, value: String?, modifier: Modifier = Modifier, chevron: Boolean = true, onClick: (() -> Unit)? = null) {
    val colors = QuietTheme.colors
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            .then(if (onClick != null) Modifier.quietClickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = QuietType.body, color = colors.ink)
        Spacer(Modifier.weight(1f))
        if (value != null) Text(value, style = QuietType.body, color = colors.muted)
        if (chevron && onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.muted, modifier = Modifier.size(22.dp))
    }
}

/** A switch row in the theme's colors (iOS `ThemedToggleStyle`); TalkBack reads it as a switch, on or off. */
@Composable
fun GroupToggle(label: String, detail: String?, checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val colors = QuietTheme.colors
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = QuietType.body, color = colors.ink)
            if (detail != null) Text(detail, style = QuietType.subheadline, color = colors.muted)
        }
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.primary, checkedThumbColor = colors.onPrimary,
                uncheckedTrackColor = colors.surface2, uncheckedThumbColor = colors.muted, uncheckedBorderColor = colors.surface2,
            ),
        )
    }
}
