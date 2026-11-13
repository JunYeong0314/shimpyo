package com.shimpyo.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Screen frame: a gutter that tightens in a split window and one vertical rhythm for every screen. */
@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(screenGutter()),
        verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
        content = content,
    )
}

@Composable
fun screenGutter(): Dp = if (ShimpyoTheme.compact) Dimens.GutterCompact else Dimens.GutterWide

/** Quiet label above a value ("Driving without a break"). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = ShimpyoTheme.colors.textSecondary) {
    FitText(text, modifier = modifier, style = MaterialTheme.typography.labelMedium, color = color)
}

/** The oversized value that carries a screen. Shrinks to fit instead of wrapping. */
@Composable
fun Hero(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ShimpyoTheme.colors.textPrimary,
    style: TextStyle = heroStyle(),
) {
    FitText(text, modifier = modifier, style = style, color = color, minFontSize = 36.sp)
}

/** A place or an option as the headline: one step below a number. */
@Composable
fun headlineStyle(): TextStyle =
    if (ShimpyoTheme.compact) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge

/** displayLarge, grown on a full-width window so one number holds the whole screen. */
@Composable
fun heroStyle(): TextStyle {
    val base = MaterialTheme.typography.displayLarge
    return if (ShimpyoTheme.compact) base else base.copy(fontSize = 144.sp, lineHeight = 152.sp, letterSpacing = (-3).sp)
}

/** The one filled action of a screen (Hyundai Blue). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = ShimpyoTheme.colors
    ActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        container = if (enabled) c.brand else c.surfaceMuted,
        content = if (enabled) c.onBrand else c.textTertiary,
        enabled = enabled,
    )
}

/** Neutral action next to a primary one ("Not now", "5 more minutes"). */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ShimpyoTheme.colors
    ActionButton(text = text, onClick = onClick, modifier = modifier, container = c.surfaceMuted, content = c.textPrimary)
}

/** Car-size button. The label shrinks to stay on one line. */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    container: Color,
    content: Color,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .heightIn(min = Dimens.ActionHeight)
            .clip(RoundedCornerShape(Dimens.Radius))
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        FitText(text, style = MaterialTheme.typography.labelLarge, color = content, textAlign = TextAlign.Center)
    }
}

/** Lays out a screen's actions: a fixed width from the start edge when wide, sharing the row when compact. */
@Composable
fun ActionRow(content: @Composable RowScope.(item: Modifier) -> Unit) {
    val compact = ShimpyoTheme.compact
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
        content(if (compact) Modifier.weight(1f) else Modifier.width(Dimens.ActionWidth))
    }
}

/** Small pill (permission "Allow", nap length, demo controls). */
@Composable
fun Chip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    height: Dp = 72.dp,
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    val c = ShimpyoTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = height)
            .clip(RoundedCornerShape(Dimens.RadiusSmall))
            .background(if (selected) c.brand else c.surfaceMuted)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        FitText(text, style = style, color = if (selected) c.onBrand else c.textPrimary, textAlign = TextAlign.Center)
    }
}

/** White grouped surface (settings groups, rest options). */
@Composable
fun ShimpyoSurface(
    modifier: Modifier = Modifier,
    color: Color = ShimpyoTheme.colors.surface,
    padding: Dp = Dimens.Gap,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.Radius))
            .background(color)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(ShimpyoTheme.colors.outline))
}

@Composable
fun Dot(color: Color, size: Dp = 14.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** What Shimpyo is saying, as a quiet caption line (accessibility and noisy cabins). */
@Composable
fun VoiceCaption(
    text: String?,
    modifier: Modifier = Modifier,
    color: Color = ShimpyoTheme.colors.textSecondary,
    accent: Color = ShimpyoTheme.colors.brand,
) {
    if (text.isNullOrBlank()) return
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(Modifier.width(20.dp))
        ShimpyoText(text, style = MaterialTheme.typography.bodyLarge, color = color, maxLines = 3)
    }
}

/** Pushes what follows to the bottom of a column. */
@Composable
fun ColumnScope.Fill() = Spacer(Modifier.weight(1f))
