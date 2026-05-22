package iondrive.nop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

@OptIn(ExperimentalJewelApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ThemeToggleButton(onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val isDark = JewelTheme.isDark
    val tint = if (isDark) ProjectIconTintDark else ProjectIconTintLight
    Box(modifier = modifier.padding(8.dp)) {
        Tooltip(tooltip = { Text(if (isDark) "Switch to light theme" else "Switch to dark theme") }) {
            IconButton(onClick = onToggle) {
                Canvas(Modifier.size(16.dp)) {
                    if (isDark) drawSunIcon(tint) else drawMoonIcon(tint)
                }
            }
        }
    }
}

// Sun: shown when the app is dark (clicking switches to light).
internal fun DrawScope.drawSunIcon(tint: Color) {
    drawCircle(color = tint, radius = 2.6f, center = Offset(8f, 8f), style = Stroke(width = 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val rays = listOf(
        0f to -5.4f, 0f to 5.4f, -5.4f to 0f, 5.4f to 0f,
        -3.8f to -3.8f, 3.8f to -3.8f, -3.8f to 3.8f, 3.8f to 3.8f,
    )
    for ((dx, dy) in rays) {
        val inner = 1.4f / kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val outer = 0.6f
        drawLine(
            color = tint,
            start = Offset(8f + dx * (1f - outer * 0.18f), 8f + dy * (1f - outer * 0.18f)),
            end = Offset(8f + dx * (1f - inner * 0.6f), 8f + dy * (1f - inner * 0.6f)),
            strokeWidth = 1.3f,
            cap = StrokeCap.Round,
        )
    }
}

// Moon: shown when the app is light (clicking switches to dark).
internal fun DrawScope.drawMoonIcon(tint: Color) {
    drawPath(
        path = ComposePath().apply {
            moveTo(10.5f, 2.5f)
            cubicTo(7.5f, 3f, 5f, 5.6f, 5f, 8.8f)
            cubicTo(5f, 12.2f, 7.8f, 14.5f, 10.5f, 14f)
            cubicTo(8.4f, 13.1f, 7f, 11.1f, 7f, 8.6f)
            cubicTo(7f, 6.1f, 8.4f, 4.1f, 10.5f, 2.5f)
            close()
        },
        color = tint,
        style = Stroke(width = 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
