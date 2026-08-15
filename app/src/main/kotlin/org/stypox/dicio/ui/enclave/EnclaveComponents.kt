package org.stypox.dicio.ui.enclave

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.stypox.dicio.ui.theme.AppTheme
import org.stypox.dicio.ui.theme.EnclaveTokens
import org.stypox.dicio.ui.theme.EyebrowStyle
import org.stypox.dicio.settings.datastore.Theme as SettingsTheme

/**
 * The building blocks shared by the Enclave screens, built to the measurements in the design
 * handoff so that a toggle or a card is identical wherever it appears.
 */

/**
 * The app-wide toggle: a 42×25dp track with a 21dp knob that travels 17dp, animating track colour
 * and knob position together over 200ms.
 *
 * This is a bespoke control rather than a Material `Switch` because the handoff pins exact
 * dimensions that Material's switch (which is larger, and has a knob that resizes when pressed)
 * cannot be configured into.
 */
@Composable
fun EnclaveToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) EnclaveTokens.Accent else EnclaveTokens.ToggleOff,
        animationSpec = tween(EnclaveTokens.ToggleAnimationMs),
        label = "toggleTrack",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) EnclaveTokens.ToggleTravel else 0.dp,
        animationSpec = tween(EnclaveTokens.ToggleAnimationMs),
        label = "toggleKnob",
    )

    Box(
        modifier = modifier
            .size(EnclaveTokens.ToggleTrackWidth, EnclaveTokens.ToggleTrackHeight)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onCheckedChange(!checked) }
            .padding(EnclaveTokens.TogglePadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(EnclaveTokens.ToggleKnob)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/**
 * A card / list group: [EnclaveTokens.Surface] at 16dp radius, outlined with the 9%-white hairline
 * rather than carrying a drop shadow.
 */
@Composable
fun EnclaveCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(EnclaveTokens.RadiusCard))
            .background(EnclaveTokens.Surface)
            .border(1.dp, EnclaveTokens.Line, RoundedCornerShape(EnclaveTokens.RadiusCard))
            .padding(contentPadding),
        content = content,
    )
}

/** An uppercase, wide-tracked label such as "SETTINGS · LOCAL AI". */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = EnclaveTokens.TextDim) {
    Text(text.uppercase(), style = EyebrowStyle, color = color, modifier = modifier)
}

/**
 * A small status pill, e.g. the green "On-device" badge or the amber "online" tag.
 *
 * The background is derived from [color] at 16% so a caller only has to supply the one semantic
 * colour, and the pill's fill can never drift out of step with its text.
 */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        } else {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

/** The primary CTA: 52dp tall, 16dp radius, filled with the accent gradient. */
@Composable
fun EnclavePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    brush: Brush = EnclaveTokens.AccentGradient,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(EnclaveTokens.ButtonHeight)
            .clip(RoundedCornerShape(EnclaveTokens.RadiusButton))
            .background(if (enabled) brush else Brush.linearGradient(
                listOf(EnclaveTokens.SurfaceVariant, EnclaveTokens.SurfaceVariant)
            ))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else EnclaveTokens.TextDim,
        )
    }
}

/** A secondary, outlined action. [danger] switches it to the destructive colour. */
@Composable
fun EnclaveOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val color = if (danger) EnclaveTokens.Danger else EnclaveTokens.TextMuted
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(EnclaveTokens.ButtonHeight)
            .clip(RoundedCornerShape(EnclaveTokens.RadiusButton))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(EnclaveTokens.RadiusButton))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

/** The tinted, rounded icon tile that leads a list row (34dp) or an onboarding row (38dp). */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

/**
 * A settings row: icon tile, title, one-line description, and a trailing control.
 *
 * The whole row is clickable and drives the same action as the toggle, so the tap target is the
 * full width rather than a 42dp switch.
 */
@Composable
fun EnclaveSettingRow(
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = EnclaveTokens.Accent,
    tag: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            IconTile(icon, iconTint)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = EnclaveTokens.Text,
                )
                tag?.invoke()
            }
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall,
                    color = EnclaveTokens.TextDim,
                )
            }
        }
        trailing?.invoke()
    }
}

/** A hairline divider matching the card border. */
@Composable
fun EnclaveDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 14.dp)
            .height(1.dp)
            .background(EnclaveTokens.Line)
    )
}

/** The header above a grouped card section, e.g. a green check + "Works offline". */
@Composable
fun SectionHeader(
    text: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0A1E)
@Composable
private fun EnclaveComponentsPreview() {
    AppTheme(theme = SettingsTheme.THEME_DARK) {
        Column(
            Modifier
                .background(EnclaveTokens.Bg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Eyebrow("Settings · Local AI")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("On-device", EnclaveTokens.Ok)
                StatusPill("online", EnclaveTokens.Warn)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EnclaveToggle(checked = true, onCheckedChange = {})
                EnclaveToggle(checked = false, onCheckedChange = {})
            }
            EnclavePrimaryButton("Get started", onClick = {})
            EnclaveOutlinedButton("Clear all data", onClick = {}, danger = true)
            Spacer(Modifier.height(4.dp))
        }
    }
}
