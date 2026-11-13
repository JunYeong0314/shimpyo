package com.shimpyo.app.core.designsystem

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

private const val WORD_JOINER = '⁠'

/**
 * Korean line breaking like CSS `word-break: keep-all`.
 * Android may break Hangul between any two syllables, which splits words ("부드럽 / 게").
 * Joining the characters of each word keeps line breaks at spaces. Text without Hangul is unchanged.
 */
fun String.keepAll(): String {
    if (none { it in '가'..'힣' }) return this
    val out = StringBuilder(length * 2)
    for (i in indices) {
        val c = this[i]
        out.append(c)
        val next = getOrNull(i + 1) ?: continue
        if (!c.isWhitespace() && !next.isWhitespace()) out.append(WORD_JOINER)
    }
    return out.toString()
}

/** Multi-line text that wraps only between words. */
@Composable
fun ShimpyoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
) {
    Text(
        text = text.keepAll(),
        modifier = modifier,
        color = color,
        style = style,
        textAlign = textAlign,
        maxLines = maxLines,
        minLines = minLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

/**
 * Single-line text that shrinks to fit its width (labels, buttons, big numbers) instead of wrapping
 * or being clipped.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign? = null,
    minFontSize: TextUnit = 14.sp,
) {
    val resolvedColor = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    val maxFontSize = if (style.fontSize.isSpecified) style.fontSize else 24.sp
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(TextStyle(color = resolvedColor, textAlign = textAlign ?: TextAlign.Unspecified)),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = maxFontSize, stepSize = 1.sp),
    )
}
