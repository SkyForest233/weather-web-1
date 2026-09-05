package li.drizz.app.charts

import androidx.compose.ui.graphics.Color
import li.drizz.app.domain.WeatherGlyph

/**
 * Declarative series model feeding the shared meteogram engine. One engine
 * renders the week meteograms, the model comparison, the 14-day ensemble and
 * the seasonal/historical charts — mirroring how the website drives its
 * canvas engine (src/lib/charts) from the variable registry.
 */
enum class SeriesKind { LINE, BAR, CLOUD_BAND, PICTOGRAMS, WIND_ARROWS }

data class ChartSeries(
    val id: String,
    val label: String,
    val values: List<Double?>,
    val color: Color,
    val kind: SeriesKind = SeriesKind.LINE,
    val strokeWidthDp: Float = 3f,
    val dashed: Boolean = false,
    /** Stroke the line coloured by the temperature scale. */
    val colorScale: Boolean = false,
    /** Area fill under the line coloured by the temperature scale, fading down. */
    val gradientFill: Boolean = false,
    /** Flat area fill under the line. */
    val fill: Boolean = false,
    val fillOpacity: Float = 0f,
    /** Draw a contrasting halo under the line. */
    val outline: Boolean = false,
    /** Annotate local minima/maxima with their value. */
    val extrema: Boolean = false,
    val scaleBy: Double = 1.0,
    /** Plot against the secondary (right) axis. */
    val rightAxis: Boolean = false,
    /** Fixed right-axis range (e.g. precipitation probability 0–100). */
    val rightPreset: ClosedFloatingPointRange<Double>? = null,
    val rightPresetInverted: Boolean = false,
    /** Cloud band slot: null hangs from the top, else high/mid/low stacking. */
    val cloudLayerRank: Int = 0,
    /** Wind direction values (degrees) for WIND_ARROWS series. */
    val arrowDirections: List<Double?>? = null,
    /** Day/night flag per slot for PICTOGRAMS series. */
    val daytime: List<Boolean>? = null
)

/** A shaded min/max spread behind the lines (ensemble/seasonal bands). */
data class ChartBand(
    val id: String,
    val lower: List<Double?>,
    val upper: List<Double?>,
    val color: Color,
    val rightAxis: Boolean = false
)

/** A dashed horizontal-ish reference series (model mean, climate normal). */
data class ReferenceSeries(
    val id: String,
    val label: String,
    val values: List<Double?>,
    val color: Color,
    val rightAxis: Boolean = false
)

data class ChartConfig(
    val timeSec: List<Long>,
    val timezone: String,
    /** Sunrise/sunset bands, epoch seconds. */
    val daylightBands: List<li.drizz.app.domain.model.DaylightBand> = emptyList(),
    val leftUnit: String = "",
    val rightUnit: String = "",
    /** Vertical "now" marker, epoch seconds; null hides it. */
    val nowSec: Long? = null,
    /** °F values need conversion before sampling the °C temperature scale. */
    val tempInFahrenheit: Boolean = false
)

/** Which entries show up in the tooltip and legend. */
data class TooltipEntry(val label: String, val value: String, val color: Color)

/**
 * Visible-window state with pan/zoom — the port of the canvas engine's
 * drag/scroll zoom ("drag or ⌘/Ctrl + scroll to zoom", reset via double tap).
 */
class ChartWindow(
    domainStart: Long,
    domainEnd: Long,
    initialStart: Long = domainStart,
    initialEnd: Long = domainEnd
) {
    var domainStart: Long = domainStart
        private set
    var domainEnd: Long = domainEnd
        private set
    var start: Long = initialStart.coerceIn(domainStart, domainEnd)
        private set
    var end: Long = initialEnd.coerceIn(start, domainEnd)
        private set

    val width: Long get() = end - start

    fun reset(initialStart: Long, initialEnd: Long) {
        start = initialStart.coerceIn(domainStart, domainEnd)
        end = initialEnd.coerceIn(start, domainEnd)
    }

    fun rebase(newStart: Long, newEnd: Long) {
        val relStart = if (domainEnd > domainStart) (start - domainStart).toDouble() / (domainEnd - domainStart) else 0.0
        val relEnd = if (domainEnd > domainStart) (end - domainStart).toDouble() / (domainEnd - domainStart) else 1.0
        domainStart = newStart
        domainEnd = newEnd
        start = newStart + (relStart * (newEnd - newStart)).toLong()
        end = newStart + (relEnd * (newEnd - newStart)).toLong()
        clamp()
    }

    fun panByPixels(dxPixels: Float, viewportWidthPx: Float) {
        if (viewportWidthPx <= 0) return
        val dx = (dxPixels / viewportWidthPx * width).toLong()
        start += dx
        end += dx
        clamp()
    }

    /** Pinch: scale the window around the focal x position. */
    fun zoom(scaleFactor: Float, focalX: Float, viewportWidthPx: Float) {
        if (viewportWidthPx <= 0 || scaleFactor <= 0f) return
        val focalTime = start + (focalX / viewportWidthPx * width).toLong()
        val newWidth = (width / scaleFactor).toLong().coerceIn(MIN_WINDOW_MS, domainEnd - domainStart)
        val frac = if (domainEnd > domainStart) (focalTime - start).toDouble() / width else 0.5
        start = focalTime - (frac * newWidth).toLong()
        end = start + newWidth
        clamp()
    }

    fun clamp() {
        val windowLen = (end - start).coerceIn(MIN_WINDOW_MS, domainEnd - domainStart)
        var s = start
        if (s < domainStart) s = domainStart
        if (s + windowLen > domainEnd) s = domainEnd - windowLen
        start = s
        end = s + windowLen
    }

    companion object {
        const val MIN_WINDOW_MS: Long = 6 * 3600L
    }
}
