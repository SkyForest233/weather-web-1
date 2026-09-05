package li.drizz.app.charts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import li.drizz.app.domain.WeatherGlyph

/**
 * Minimal geometric weather glyphs for the meteogram pictogram rows — drawn
 * directly into the chart canvas, so they pan/zoom with the data like the
 * website's icon font does.
 */
object WeatherGlyphs {

    // Material-style cloud outline in a 24×24 box.
    private val cloudPath = Path().apply {
        moveTo(19.35f, 10.04f)
        cubicTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
        cubicTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
        cubicTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
        cubicTo(0f, 17.31f, 2.69f, 20f, 6f, 20f)
        lineTo(19f, 20f)
        cubicTo(21.76f, 20f, 24f, 17.76f, 24f, 15f)
        cubicTo(24f, 12.36f, 21.95f, 10.22f, 19.35f, 10.04f)
        close()
    }

    private val dropPath = Path().apply {
        moveTo(0f, -3.2f)
        cubicTo(1.6f, -1.0f, 2.1f, 0f, 2.1f, 1.0f)
        cubicTo(2.1f, 2.4f, 1.2f, 3.3f, 0f, 3.3f)
        cubicTo(-1.2f, 3.3f, -2.1f, 2.4f, -2.1f, 1.0f)
        cubicTo(-2.1f, 0f, -1.6f, -1.0f, 0f, -3.2f)
        close()
    }

    private val boltPath = Path().apply {
        moveTo(1f, -4f)
        lineTo(-2.4f, 0.4f)
        lineTo(-0.4f, 0.4f)
        lineTo(-1f, 4f)
        lineTo(2.4f, -0.6f)
        lineTo(0.4f, -0.6f)
        close()
    }

    /**
     * Draws `glyph` centred at (cx, cy) inside a box of `size` px.
     * `backdrop` is the flat color behind the strip (used to cut the moon's
     * inner circle) — pass the chart card's container color.
     */
    fun DrawScope.drawGlyph(
        glyph: WeatherGlyph,
        cx: Float,
        cy: Float,
        size: Float,
        iconColor: Color,
        backdrop: Color,
        dropColor: Color,
        boltColor: Color,
        sunColor: Color
    ) {
        val half = size / 2f
        when (glyph) {
            WeatherGlyph.CLEAR_DAY -> {
                drawSun(cx, cy, half * 0.62f, sunColor)
            }
            WeatherGlyph.CLEAR_NIGHT -> {
                // Crescent: full disc minus an offset disc of the backdrop.
                drawCircle(iconColor, radius = half * 0.6f, center = Offset(cx, cy))
                drawCircle(backdrop, radius = half * 0.5f, center = Offset(cx + half * 0.34f, cy - half * 0.22f))
            }
            WeatherGlyph.CLOUDY_DAY -> {
                drawSun(cx + half * 0.42f, cy - half * 0.42f, half * 0.34f, sunColor)
                drawCloud(cx, cy + half * 0.14f, size * 0.92f, iconColor)
            }
            WeatherGlyph.CLOUDY_NIGHT -> {
                drawCircle(iconColor, radius = half * 0.34f, center = Offset(cx + half * 0.46f, cy - half * 0.5f))
                drawCircle(backdrop, radius = half * 0.27f, center = Offset(cx + half * 0.66f, cy - half * 0.6f))
                drawCloud(cx, cy + half * 0.14f, size * 0.92f, iconColor)
            }
            WeatherGlyph.OVERCAST -> drawCloud(cx, cy, size, iconColor)
            WeatherGlyph.FOG -> {
                drawCloud(cx, cy - half * 0.28f, size * 0.9f, iconColor)
                val lineY = cy + half * 0.42f
                repeat(2) { i ->
                    drawLine(
                        iconColor, alpha = 0.8f,
                        start = Offset(cx - half * 0.7f, lineY + i * size * 0.16f),
                        end = Offset(cx + half * (0.7f - i * 0.5f), lineY + i * size * 0.16f),
                        strokeWidth = size * 0.07f
                    )
                }
            }
            WeatherGlyph.SPRINKLE -> {
                drawCloud(cx, cy - half * 0.3f, size * 0.9f, iconColor)
                drawDrop(cx - half * 0.28f, cy + half * 0.42f, size * 0.2f, dropColor)
                drawDrop(cx + half * 0.3f, cy + half * 0.42f, size * 0.2f, dropColor)
            }
            WeatherGlyph.RAIN -> {
                drawCloud(cx, cy - half * 0.3f, size * 0.9f, iconColor)
                drawDrop(cx - half * 0.42f, cy + half * 0.45f, size * 0.2f, dropColor)
                drawDrop(cx, cy + half * 0.52f, size * 0.2f, dropColor)
                drawDrop(cx + half * 0.42f, cy + half * 0.45f, size * 0.2f, dropColor)
            }
            WeatherGlyph.RAIN_MIX -> {
                drawCloud(cx, cy - half * 0.3f, size * 0.9f, iconColor)
                drawDrop(cx - half * 0.36f, cy + half * 0.45f, size * 0.2f, dropColor)
                drawDrop(cx + half * 0.3f, cy + half * 0.45f, size * 0.2f, dropColor)
                drawCircle(SnowAccent, 1.6f * size / 24f, Offset(cx - half * 0.02f, cy + half * 0.58f))
            }
            WeatherGlyph.SNOW -> {
                drawCloud(cx, cy - half * 0.3f, size * 0.9f, iconColor)
                drawFlake(cx - half * 0.36f, cy + half * 0.48f, size * 0.11f, SnowAccent)
                drawFlake(cx, cy + half * 0.56f, size * 0.11f, SnowAccent)
                drawFlake(cx + half * 0.36f, cy + half * 0.48f, size * 0.11f, SnowAccent)
            }
            WeatherGlyph.SHOWERS -> {
                drawCloud(cx, cy - half * 0.3f, size * 0.9f, iconColor)
                drawDrop(cx - half * 0.3f, cy + half * 0.42f, size * 0.2f, dropColor, slant = 0.5f)
                drawDrop(cx + half * 0.22f, cy + half * 0.5f, size * 0.2f, dropColor, slant = 0.5f)
            }
            WeatherGlyph.THUNDERSTORM -> {
                drawCloud(cx, cy - half * 0.3f, size * 0.9f, iconColor)
                drawBolt(cx, cy + half * 0.4f, size * 0.42f, boltColor)
            }
            WeatherGlyph.STORM_SHOWERS -> {
                drawCloud(cx, cy - half * 0.3f, size * 0.9f, iconColor)
                drawBolt(cx - half * 0.1f, cy + half * 0.38f, size * 0.4f, boltColor)
                drawDrop(cx + half * 0.4f, cy + half * 0.45f, size * 0.18f, dropColor)
            }
        }
    }

    private fun DrawScope.drawSun(cx: Float, cy: Float, r: Float, color: Color) {
        drawCircle(color, r, Offset(cx, cy))
        val rayStart = r * 1.35f
        val rayEnd = r * 1.8f
        for (i in 0 until 8) {
            val a = Math.PI * 2 * i / 8
            drawLine(
                color,
                start = Offset(cx + (rayStart * kotlin.math.cos(a)).toFloat(), cy + (rayStart * kotlin.math.sin(a)).toFloat()),
                end = Offset(cx + (rayEnd * kotlin.math.cos(a)).toFloat(), cy + (rayEnd * kotlin.math.sin(a)).toFloat()),
                strokeWidth = r * 0.28f
            )
        }
    }

    private fun DrawScope.drawCloud(cx: Float, cy: Float, size: Float, color: Color) {
        translate(cx - size / 2f, cy - size / 2f) {
            scale(size / 24f, size / 24f, Offset.Zero) {
                drawPath(cloudPath, color)
            }
        }
    }

    private fun DrawScope.drawDrop(cx: Float, cy: Float, size: Float, color: Color, slant: Float = 0f) {
        translate(cx + slant * size, cy) {
            scale(size / 3.2f, size / 3.2f, Offset.Zero) {
                drawPath(dropPath, color)
            }
        }
    }

    private fun DrawScope.drawBolt(cx: Float, cy: Float, size: Float, color: Color) {
        translate(cx, cy) {
            scale(size / 4f, size / 4f, Offset.Zero) {
                drawPath(boltPath, color)
            }
        }
    }

    private fun DrawScope.drawFlake(cx: Float, cy: Float, r: Float, color: Color) {
        for (i in 0 until 3) {
            val a = Math.PI * i / 3
            drawLine(
                color,
                start = Offset(cx - (r * kotlin.math.cos(a)).toFloat(), cy - (r * kotlin.math.sin(a)).toFloat()),
                end = Offset(cx + (r * kotlin.math.cos(a)).toFloat(), cy + (r * kotlin.math.sin(a)).toFloat()),
                strokeWidth = r * 0.5f
            )
        }
    }

    /** Wind-direction arrow pointing where the wind blows *to* (from `from` degrees). */
    fun DrawScope.drawWindArrow(cx: Float, cy: Float, size: Float, directionDeg: Double, color: Color) {
        val rad = Math.toRadians(directionDeg + 180)
        translate(cx, cy) {
            rotate(Math.toDegrees(rad).toFloat(), Offset.Zero) {
                val path = Path().apply {
                    moveTo(0f, -size / 2f)
                    lineTo(-size * 0.36f, size * 0.3f)
                    lineTo(0f, size * 0.12f)
                    lineTo(size * 0.36f, size * 0.3f)
                    close()
                }
                drawPath(path, color)
            }
        }
    }
}

/** Accent colors shared by glyphs on both themes. */
val RainAccent = Color(0xFF3B82D6)
val SnowAccent = Color(0xFF7EB3E8)
val BoltAccent = Color(0xFFF59E0B)
val SunAccent = Color(0xFFF5A623)
