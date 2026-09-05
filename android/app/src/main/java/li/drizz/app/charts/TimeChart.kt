package li.drizz.app.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.drizz.app.util.Dates
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The shared time-series chart engine: daylight bands, day gridlines, line /
 * bar / cloud-band series, weather pictograms, wind arrows, extrema labels,
 * pan + pinch zoom with double-tap reset, and a press crosshair — the native
 * port of drizz.li's CanvasChart.
 */
@Composable
fun TimeChart(
    config: ChartConfig,
    series: List<ChartSeries>,
    window: ChartWindow,
    modifier: Modifier = Modifier,
    bands: List<ChartBand> = emptyList(),
    references: List<ReferenceSeries> = emptyList(),
    showDaylight: Boolean = true,
    onDoubleTapReset: (() -> Unit)? = null
) {
    val textMeasurer = rememberTextMeasurer()
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current

    // Any window mutation bumps this; reading it inside the canvas keeps the
    // draw scope observing gesture updates.
    var tick by remember { mutableIntStateOf(0) }
    var pressX by remember { mutableIntStateOf(-1) }

    val pictogramStrip = 26.dp
    val arrowStrip = 22.dp
    val hasPictograms = series.any { it.kind == SeriesKind.PICTOGRAMS }
    val hasArrows = series.any { it.kind == SeriesKind.WIND_ARROWS }
    val stripHeightDp = (if (hasPictograms) pictogramStrip else 0.dp) + (if (hasArrows) arrowStrip else 0.dp)

    val gridColor = colorScheme.outlineVariant
    val labelColor = colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val extremalStyle = TextStyle(fontSize = 11.sp, color = colorScheme.onSurface)

    val canvasModifier = modifier
        .fillMaxWidth()
        .height(220.dp + stripHeightDp)
        .pointerInput(config.timeSec, series) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                window.zoom(zoom, centroid.x, size.width.toFloat())
                window.panByPixels(pan.x, size.width.toFloat())
                tick++
            }
        }
        .pointerInput(config.timeSec, onDoubleTapReset) {
            detectTapGestures(
                onDoubleTap = { onDoubleTapReset?.invoke() },
                onPress = { offset ->
                    pressX = offset.x.toInt()
                    tick++
                    tryAwaitRelease()
                    pressX = -1
                    tick++
                }
            )
        }

    Canvas(canvasModifier) {
        tick // observed
        val pressXf = if (pressX >= 0) pressX.toFloat() else null

        val stripPx = with(density) { stripHeightDp.toPx() }
        val plotTop = stripPx + 6f
        val plotBottom = size.height - 20f
        val plotLeft = 8f
        val plotRight = size.width - (if (series.any { it.rightAxis }) 46f else 8f)
        val plotW = plotRight - plotLeft
        val plotH = plotBottom - plotTop
        if (plotW <= 0 || plotH <= 0 || config.timeSec.size < 2) return@Canvas

        val tStart = window.start.toFloat()
        val tEnd = window.end.toFloat()
        val tSpan = max(1f, tEnd - tStart)
        fun xOf(tSec: Long): Float = plotLeft + ((tSec - window.start) / tSpan.toDouble() * plotW).toFloat()

        val zone: ZoneId = Dates.zone(config.timezone)
        val firstT = config.timeSec.first()
        val lastT = config.timeSec.last()

        // ── Daylight bands ──
        if (showDaylight) {
            val daylight = colorScheme.primary.copy(alpha = 0.06f)
            for (band in config.daylightBands) {
                val from = maxOf(band.startSec, window.start)
                val to = minOf(band.endSec, window.end)
                if (to <= from) continue
                val x0 = xOf(from); val x1 = xOf(to)
                drawRect(daylight, topLeft = Offset(x0, plotTop), size = androidx.compose.ui.geometry.Size(x1 - x0, plotH))
            }
        }

        // ── Day boundaries + labels ──
        var day = java.time.Instant.ofEpochSecond(window.start).atZone(zone).toLocalDate()
        val endDay = java.time.Instant.ofEpochSecond(window.end).atZone(zone).toLocalDate()
        val dateStyle = labelStyle.copy(fontSize = 10.sp)
        while (!day.isAfter(endDay)) {
            val midnight = day.atStartOfDay(zone).toEpochSecond()
            if (midnight in window.start..window.end && midnight > firstT) {
                val x = xOf(midnight)
                drawLine(gridColor.copy(alpha = 0.7f), Offset(x, plotTop), Offset(x, plotBottom), strokeWidth = 1f)
                val label = day.let { d ->
                    Dates.weekday(d).replaceFirstChar { it.uppercase() } + " " + d.dayOfMonth
                }
                val measured = textMeasurer.measure(AnnotatedString(label), dateStyle)
                val lx = (x + 6f).coerceAtMost(plotRight - measured.size.width)
                drawText(measured, topLeft = Offset(lx, plotTop + 2f))
            }
            day = day.plusDays(1)
        }

        // ── Y scales ──
        fun collect(values: List<Double?>): List<Double> =
            values.filterNotNull().filter { it.isFinite() }

        fun boundsOf(list: List<ChartSeries>, right: Boolean): Pair<Double, Double>? {
            val vals = list.filter { it.rightAxis == right && it.kind != SeriesKind.PICTOGRAMS && it.kind != SeriesKind.WIND_ARROWS }
                .flatMap { s -> collect(s.values).map { it * s.scaleBy } }
            if (vals.isEmpty()) return null
            val preset = list.firstOrNull { it.rightAxis == right && it.rightPreset != null }?.rightPreset
            return if (preset != null) preset.start to preset.endInclusive
            else niceBounds(vals.min(), vals.max())
        }

        val leftBounds = boundsOf(series, false) ?: (0.0 to 1.0)
        val rightSeries = series.filter { it.rightAxis }
        val rightBounds = if (rightSeries.isNotEmpty()) boundsOf(series, true) ?: (0.0 to 100.0) else null
        val bandVals = bands.flatMap { b -> collect(b.lower) + collect(b.upper) }
        val refVals = references.filter { !it.rightAxis }.flatMap { collect(it.values) }
        val leftWithExtras = if (bandVals.isNotEmpty() || refVals.isNotEmpty()) {
            val all = listOf(leftBounds.first, leftBounds.second) + bandVals + refVals
            niceBounds(all.min(), all.max())
        } else leftBounds

        val bandColors = bands.map { it.color.copy(alpha = 0.22f) }
        val yAxisLeft = YAxis(leftWithExtras.first, leftWithExtras.second, plotTop, plotBottom)
        val yAxisRight = rightBounds?.let { YAxis(it.first, it.second, plotTop, plotBottom) }

        // Gridlines + labels
        for (tv in yAxisLeft.ticks) {
            val y = yAxisLeft.yOf(tv)
            drawLine(gridColor.copy(alpha = 0.35f), Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1f)
        }
        for (tv in yAxisLeft.ticks) {
            val y = yAxisLeft.yOf(tv)
            val text = formatAxis(tv, config.leftUnit)
            val measured = textMeasurer.measure(AnnotatedString(text), labelStyle)
            drawText(measured, topLeft = Offset(plotLeft, (y - measured.size.height / 2).coerceIn(plotTop, plotBottom - measured.size.height)))
        }
        yAxisRight?.let { ax ->
            for (tv in ax.ticks) {
                val y = ax.yOf(tv)
                val measured = textMeasurer.measure(AnnotatedString(formatAxis(tv, config.rightUnit)), labelStyle)
                drawText(measured, topLeft = Offset(plotRight + 4f, (y - measured.size.height / 2).coerceIn(plotTop, plotBottom - measured.size.height)))
            }
        }

        fun yOf(value: Double, right: Boolean): Float =
            if (right) yAxisRight?.yOf(value) ?: plotBottom else yAxisLeft.yOf(value)

        // ── Bands (spread areas) ──
        bands.forEachIndexed { bi, band ->
            val n = minOf(config.timeSec.size, band.lower.size, band.upper.size)
            val upperPts = mutableListOf<Offset>()
            val lowerPts = mutableListOf<Offset>()
            for (i in 0 until n) {
                val t = config.timeSec[i]
                if (t < window.start || t > window.end) continue
                val lo = band.lower.getOrNull(i)?.takeIf { it.isFinite() } ?: continue
                val hi = band.upper.getOrNull(i)?.takeIf { it.isFinite() } ?: continue
                upperPts += Offset(xOf(t), yOf(hi, band.rightAxis))
                lowerPts += Offset(xOf(t), yOf(lo, band.rightAxis))
            }
            if (upperPts.size > 1) {
                val path = Path()
                upperPts.forEachIndexed { idx, p -> if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                for (p in lowerPts.reversed()) path.lineTo(p.x, p.y)
                path.close()
                drawPath(path, bandColors[bi])
            }
        }

        // ── Cloud bands ──
        val cloudBands = series.filter { it.kind == SeriesKind.CLOUD_BAND }
        val totalBand = cloudBands.firstOrNull { it.cloudLayerRank == 0 }
        if (totalBand != null) {
            drawCloudBand(config, window, totalBand, plotTop, plotH, plotRight, xOf)
        }
        val layers = cloudBands.filter { it.cloudLayerRank > 0 }.sortedBy { it.cloudLayerRank }
        if (layers.isNotEmpty()) {
            val segH = plotH / 3f
            layers.forEach { layer ->
                drawLayeredCloudBand(config, window, layer, plotTop, segH, plotRight, (layer.cloudLayerRank - 1), xOf)
            }
        }

        // ── Bars ──
        val slotPx = plotW / max(1, countVisible(config.timeSec, window))
        series.filter { it.kind == SeriesKind.BAR }.forEach { s ->
            val barW = max(2f, slotPx * 0.62f)
            val baseY = plotBottom
            for (i in config.timeSec.indices) {
                val t = config.timeSec[i]
                if (t < window.start || t > window.end) continue
                val v = s.values.getOrNull(i)?.takeIf { it.isFinite() } ?: continue
                val x = xOf(t)
                val y = yOf(v * s.scaleBy, false)
                drawRect(
                    s.color,
                    topLeft = Offset(x - barW / 2, y),
                    size = androidx.compose.ui.geometry.Size(barW, max(1f, baseY - y))
                )
            }
        }

        // ── Lines ──
        val px = with(density) { 1.dp.toPx() }
        series.filter { it.kind == SeriesKind.LINE }.forEach { s ->
            drawLineSeries(
                config, window, s,
                xOf = { t -> xOf(t) },
                yOf = { v -> yOf(v * s.scaleBy, s.rightAxis) },
                plotLeft, plotRight, plotTop, plotBottom,
                px, colorScheme.surface, extremalStyle, textMeasurer,
                tempFahrenheit = config.tempInFahrenheit
            )
        }

        // Reference series (model mean, climate normal)
        references.forEach { ref ->
            val points = config.timeSec.mapIndexedNotNull { i, t ->
                if (t < window.start || t > window.end) return@mapIndexedNotNull null
                val v = ref.values.getOrNull(i)?.takeIf { it.isFinite() } ?: return@mapIndexedNotNull null
                Offset(xOf(t), yOf(v, ref.rightAxis))
            }
            for (i in 1 until points.size) {
                drawLine(
                    ref.color, points[i - 1], points[i],
                    strokeWidth = 2f * px,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * px, 5f * px))
                )
            }
        }

        // ── Pictogram strip ──
        if (hasPictograms) {
            val glyphSize = with(density) { pictogramStrip.toPx() * 0.8f }
            val backdrop = colorScheme.surfaceContainerLow
            val picto = series.first { it.kind == SeriesKind.PICTOGRAMS }
            val step = max(1, ceil(24f * density.density / max(1f, slotPx)).toInt())
            var i = 0
            while (i < config.timeSec.size) {
                val t = config.timeSec[i]
                val code = picto.values.getOrNull(i)?.takeIf { it.isFinite() }
                if (t in window.start..window.end && code != null) {
                    val x = xOf(t)
                    if (x in plotLeft..plotRight) {
                        val daytime = picto.daytime?.getOrNull(i) ?: true
                        val glyph = li.drizz.app.domain.weatherGlyph(code, daytime)
                        with(li.drizz.app.charts.WeatherGlyphs) {
                            drawGlyph(
                                glyph, x, pictogramStrip.toPx() / 2f, glyphSize,
                                iconColor = labelColor, backdrop = backdrop,
                                dropColor = RainAccent, boltColor = BoltAccent, sunColor = SunAccent
                            )
                        }
                    }
                }
                i += step
            }
        }

        // ── Wind arrow strip ──
        if (hasArrows) {
            val arrowTop = (if (hasPictograms) pictogramStrip else 0.dp).toPx() + arrowStrip.toPx() / 2f
            val arrowSize = with(density) { arrowStrip.toPx() * 0.75f }
            val arrows = series.first { it.kind == SeriesKind.WIND_ARROWS }
            val dirs = arrows.arrowDirections ?: arrows.values
            val step = max(1, ceil(26f * density.density / max(1f, slotPx)).toInt())
            var i = 0
            while (i < config.timeSec.size) {
                val t = config.timeSec[i]
                val dir = dirs.getOrNull(i)?.takeIf { it.isFinite() }
                if (t in window.start..window.end && dir != null) {
                    val x = xOf(t)
                    if (x in plotLeft..plotRight) {
                        with(li.drizz.app.charts.WeatherGlyphs) {
                            drawWindArrow(x, arrowTop, arrowSize, dir, labelColor)
                        }
                    }
                }
                i += step
            }
        }

        // ── Now marker ──
        config.nowSec?.let { now ->
            if (now in window.start..window.end) {
                val x = xOf(now)
                drawLine(
                    colorScheme.primary.copy(alpha = 0.65f),
                    Offset(x, plotTop), Offset(x, plotBottom),
                    strokeWidth = 1.5f * px,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * px, 4f * px))
                )
            }
        }

        // ── Crosshair + tooltip ──
        pressXf?.let { pxX ->
            if (pxX in plotLeft..plotRight) {
                val t = window.start + ((pxX - plotLeft) / plotW * tSpan).toLong()
                var bestI = -1
                var bestDist = Long.MAX_VALUE
                config.timeSec.forEachIndexed { i, tt ->
                    val d = abs(tt - t)
                    if (d < bestDist) { bestDist = d; bestI = i }
                }
                if (bestI >= 0) {
                    val x = xOf(config.timeSec[bestI])
                    drawLine(colorScheme.onSurfaceVariant.copy(alpha = 0.6f), Offset(x, plotTop), Offset(x, plotBottom), strokeWidth = 1f)
                    series.filter { it.kind != SeriesKind.WIND_ARROWS }.forEach { s ->
                        val v = s.values.getOrNull(bestI)?.takeIf { it.isFinite() } ?: return@forEach
                        val y = when (s.kind) {
                            SeriesKind.LINE -> yOf(v * s.scaleBy, s.rightAxis)
                            SeriesKind.BAR -> yOf(v * s.scaleBy, false)
                            else -> plotTop + pictogramStrip.toPx() / 2f
                        }
                        drawCircle(s.color, 3.5f * px, Offset(x, y))
                        val unit = if (s.rightAxis) config.rightUnit else config.leftUnit
                        val text = formatValue(v * s.scaleBy) + (if (unit.isNotEmpty()) " $unit" else "")
                        val measured = textMeasurer.measure(AnnotatedString(text), labelStyle)
                        val tx = (x + 6f).coerceAtMost(plotRight - measured.size.width)
                        val ty = (y - measured.size.height - 4f).coerceAtLeast(plotTop)
                        drawRect(
                            colorScheme.surface.copy(alpha = 0.85f),
                            topLeft = Offset(tx - 2f, ty - 1f),
                            size = androidx.compose.ui.geometry.Size(measured.size.width + 4f, measured.size.height + 2f)
                        )
                        drawText(measured, topLeft = Offset(tx, ty))
                    }
                }
            }
        }
    }
}

private fun countVisible(time: List<Long>, window: ChartWindow): Int =
    time.count { it in window.start..window.end }

private fun formatAxis(v: Double, unit: String): String {
    val rounded = if (abs(v) >= 100 || (abs(v) >= 10 && v == floor(v))) v.roundToInt() else Math.round(v * 10.0) / 10.0
    return if (unit.isNotEmpty() && rounded.roundToInt().toDouble() == rounded && unit != "%") "${rounded.roundToInt()} $unit"
    else if (unit == "%") "${rounded.roundToInt()}%" else rounded.toString()
}

private fun formatValue(v: Double): String =
    if (abs(v) >= 100) v.roundToInt().toString()
    else if (abs(v - v.roundToInt()) < 0.05) v.roundToInt().toString()
    else String.format(java.util.Locale.getDefault(), "%.1f", v)

/** Nice round bounds with headroom, like the site's axis helper. */
fun niceBounds(min: Double, max: Double, paddingFraction: Double = 0.08): Pair<Double, Double> {
    if (!min.isFinite() || !max.isFinite()) return 0.0 to 1.0
    if (min == max) {
        val pad = if (min == 0.0) 1.0 else abs(min) * 0.1
        return niceBounds(min - pad, max + pad, 0.0)
    }
    val range = max - min
    val pad = range * paddingFraction
    val lo = min - pad
    val hi = max + pad
    val step = niceStep((hi - lo) / 4)
    val niceLo = floor(lo / step) * step
    val niceHi = ceil(hi / step) * step
    return niceLo to niceHi
}

private fun niceStep(rough: Double): Double {
    if (rough <= 0 || !rough.isFinite()) return 1.0
    val exp = floor(kotlin.math.log10(rough))
    val f = rough / 10.0.pow(exp)
    val nice = when {
        f <= 1 -> 1.0
        f <= 2 -> 2.0
        f <= 5 -> 5.0
        else -> 10.0
    }
    return nice * 10.0.pow(exp)
}

private class YAxis(val min: Double, val max: Double, val top: Float, val bottom: Float) {
    val ticks: List<Double> = run {
        val out = mutableListOf<Double>()
        val step = niceStep((max - min) / 4)
        var v = ceil(min / step) * step
        while (v <= max + 1e-9) { out += v; v += step }
        out
    }

    fun yOf(v: Double): Float {
        if (max == min) return bottom
        val frac = ((v - min) / (max - min)).coerceIn(0.0, 1.0)
        return (bottom - frac * (bottom - top)).toFloat()
    }
}

private fun DrawScope.drawCloudBand(
    config: ChartConfig,
    window: ChartWindow,
    s: ChartSeries,
    plotTop: Float,
    plotH: Float,
    plotRight: Float,
    xOf: (Long) -> Float
) {
    val n = minOf(config.timeSec.size, s.values.size)
    val path = Path()
    var firstX = 0f
    var lastX = 0f
    var started = false
    for (i in 0 until n) {
        val t = config.timeSec[i]
        if (t < window.start || t > window.end) continue
        val v = s.values.getOrNull(i)?.takeIf { it.isFinite() } ?: continue
        val frac = (v / 100.0).coerceIn(0.0, 1.0)
        val x = xOf(t)
        val y = plotTop + (frac * plotH).toFloat()
        if (!started) { path.moveTo(x, y); firstX = x; started = true } else path.lineTo(x, y)
        lastX = x
    }
    if (!started) return
    path.lineTo(lastX, plotTop)
    path.lineTo(firstX, plotTop)
    path.close()
    drawPath(path, s.color.copy(alpha = 0.45f))
}

private fun DrawScope.drawLayeredCloudBand(
    config: ChartConfig,
    window: ChartWindow,
    s: ChartSeries,
    plotTop: Float,
    segH: Float,
    plotRight: Float,
    slot: Int,
    xOf: (Long) -> Float
) {
    val n = minOf(config.timeSec.size, s.values.size)
    val path = Path()
    var firstX = 0f
    var lastX = 0f
    var started = false
    val segTop = plotTop + slot * segH
    for (i in 0 until n) {
        val t = config.timeSec[i]
        if (t < window.start || t > window.end) continue
        val v = s.values.getOrNull(i)?.takeIf { it.isFinite() } ?: continue
        val frac = (v / 100.0).coerceIn(0.0, 1.0)
        val x = xOf(t)
        val y = segTop + (frac * segH).toFloat()
        if (!started) { path.moveTo(x, y); firstX = x; started = true } else path.lineTo(x, y)
        lastX = x
    }
    if (!started) return
    path.lineTo(lastX, segTop)
    path.lineTo(firstX, segTop)
    path.close()
    drawPath(path, s.color.copy(alpha = 0.5f))
}

private fun DrawScope.drawLineSeries(
    config: ChartConfig,
    window: ChartWindow,
    s: ChartSeries,
    xOf: (Long) -> Float,
    yOf: (Double) -> Float,
    plotLeft: Float,
    plotRight: Float,
    plotTop: Float,
    plotBottom: Float,
    px: Float,
    outlineColor: Color,
    extremalStyle: TextStyle,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    tempFahrenheit: Boolean
) {
    val n = config.timeSec.size
    if (n < 2) return

    data class Pt(val x: Float, val y: Float, val v: Double, val i: Int)

    val pts = mutableListOf<Pt>()
    for (i in 0 until n) {
        val t = config.timeSec[i]
        if (t < window.start - 3600 || t > window.end + 3600) continue
        val v = s.values.getOrNull(i)?.takeIf { it.isFinite() } ?: continue
        pts += Pt(xOf(t), yOf(v), v, i)
    }
    if (pts.isEmpty()) return

    // Flat area fill
    if (s.fill) {
        val path = Path()
        pts.forEachIndexed { idx, p -> if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        path.lineTo(pts.last().x, plotBottom)
        path.lineTo(pts.first().x, plotBottom)
        path.close()
        drawPath(path, s.color.copy(alpha = s.fillOpacity))
    }

    // Gradient fill coloured by the temperature scale
    if (s.gradientFill) {
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            val midC = (a.v + b.v) / 2
            val celsius = if (tempFahrenheit) (midC - 32) / 1.8 else midC
            val color = Color(li.drizz.app.domain.TemperatureScale.sample(celsius))
            val path = Path()
            moveTo(path, a.x, a.y)
            path.lineTo(b.x, b.y)
            path.lineTo(b.x, plotBottom)
            path.lineTo(a.x, plotBottom)
            path.close()
            drawPath(
                path,
                Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)),
                    startY = minOf(a.y, b.y), endY = plotBottom
                )
            )
        }
    }

    val strokeW = s.strokeWidthDp * px
    // Outline halo for readability over busy fills
    if (s.outline) {
        for (i in 1 until pts.size) {
            drawLine(outlineColor.copy(alpha = 0.85f), pts[i - 1].let { Offset(it.x, it.y) }, Offset(pts[i].x, pts[i].y), strokeWidth = strokeW + 3.5f * px)
        }
    }

    if (s.colorScale) {
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            val midC = (a.v + b.v) / 2
            val celsius = if (tempFahrenheit) (midC - 32) / 1.8 else midC
            drawLine(
                Color(li.drizz.app.domain.TemperatureScale.sample(celsius)),
                Offset(a.x, a.y), Offset(b.x, b.y),
                strokeWidth = strokeW
            )
        }
    } else {
        val effect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(7f * px, 5f * px)) else null
        val path = Path()
        pts.forEachIndexed { idx, p -> if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        drawPath(path, s.color, style = Stroke(width = strokeW, pathEffect = effect))
    }

    // Min/max annotations across the visible range
    if (s.extrema && pts.size > 4) {
        val maxP = pts.maxByOrNull { it.v }!!
        val minP = pts.minByOrNull { it.v }!!
        listOf(maxP to -1f, minP to 1f).forEach { (p, dir) ->
            val text = formatValue(p.v)
            val measured = textMeasurer.measure(AnnotatedString(text), extremalStyle)
            val tx = (p.x - measured.size.width / 2f).coerceIn(plotLeft, plotRight - measured.size.width)
            val ty = (p.y + if (dir < 0) -measured.size.height - 4f * px else 6f * px).coerceIn(plotTop, plotBottom - measured.size.height)
            drawText(measured, topLeft = Offset(tx, ty))
        }
    }
}

private fun moveTo(path: Path, x: Float, y: Float) = path.moveTo(x, y)
