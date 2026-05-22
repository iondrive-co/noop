package iondrive.nop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.AlphaComposite
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Deterministic, restrained colour derived from the project's absolute path. Two windows on the
 * same project always get the same tint; different projects almost always differ. Restrained =
 * low saturation, mid lightness — meant to gently identify, not shout.
 */
fun projectTint(path: Path, isDark: Boolean): Color {
    val bytes = MessageDigest.getInstance("SHA-1").digest(
        path.toAbsolutePath().normalize().toString().toByteArray(),
    )
    // First byte → hue; second → small jitter in saturation/lightness so neighbouring hues still
    // look different.
    val hue = ((bytes[0].toInt() and 0xff) / 256f) * 360f
    val satJitter = ((bytes[1].toInt() and 0xff) / 256f) * 0.08f
    val lightJitter = ((bytes[2].toInt() and 0xff) / 256f) * 0.08f
    val sat = 0.32f + satJitter
    val light = if (isDark) 0.40f + lightJitter else 0.62f + lightJitter
    return Color.hsl(hue, sat, light)
}

/**
 * A small "n" tile in the project's tint colour. Returns a Compose painter suitable for
 * `Window(icon = …)`. The image is generated in memory — no PNGs on disk.
 */
fun projectWindowIcon(tint: Color, size: Int = 128): Painter =
    BitmapPainter(renderNopTile(tint, size).toComposeImageBitmap())

/**
 * Renders the same tile to a BufferedImage. Used by the gradle task that writes the base
 * icon PNG referenced by the .desktop entry, and by [projectWindowIcon].
 */
fun renderNopTile(tint: Color, size: Int): BufferedImage {
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.composite = AlphaComposite.Src
        g.color = java.awt.Color(0, 0, 0, 0)
        g.fillRect(0, 0, size, size)
        // Rounded square background in the project tint.
        g.color = java.awt.Color(tint.red, tint.green, tint.blue, tint.alpha)
        val arc = size / 5
        g.fillRoundRect(0, 0, size, size, arc, arc)
        // "n" glyph in a contrasting shade — white when the tint is darkish, near-black otherwise.
        val luminance = 0.299f * tint.red + 0.587f * tint.green + 0.114f * tint.blue
        g.color = if (luminance < 0.55f) java.awt.Color(0xF7, 0xF8, 0xFA) else java.awt.Color(0x1F, 0x23, 0x29)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, (size * 0.72f).toInt())
        val fm = g.fontMetrics
        val txt = "n"
        val tx = (size - fm.stringWidth(txt)) / 2
        val ty = (size - fm.height) / 2 + fm.ascent - (size / 24)
        g.drawString(txt, tx, ty)
    } finally {
        g.dispose()
    }
    return img
}
