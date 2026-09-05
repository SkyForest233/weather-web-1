package li.drizz.app.domain

import androidx.annotation.StringRes
import li.drizz.app.R
import li.drizz.app.data.settings.UnitPrefs

/**
 * Registry of every variable that can be plotted on the customizable
 * meteograms — the port of src/routes/weather/week/[location]/variables.ts.
 */
enum class UnitKind { TEMP, PRECIP, SNOW, WIND, PERCENT, PRESSURE, UV, DISTANCE, ENERGY }

data class ChartVariableDef(
    val key: String,
    @StringRes val labelRes: Int,
    @StringRes val shortRes: Int,
    val api: String,
    val isLine: Boolean,
    val kind: UnitKind,
    /** Fixed series color from the site's palette (theme-independent data ink). */
    val color: Long,
    val dashed: Boolean = false,
    val fill: Boolean = false,
    val fillOpacity: Float = 0f,
    val gradientFill: Boolean = false,
    val width: Float = 3f,
    val colorScale: Boolean = false,
    val foregroundLine: Boolean = false,
    val outline: Boolean = false,
    val extrema: Boolean = false,
    val pictograms: Boolean = false,
    val windArrows: Boolean = false,
    val marker: Boolean = false,
    val cloudBand: Boolean = false,
    val cloudLayer: String? = null,
    /** Transform raw values before plotting (m → km). */
    val scaleBy: Double = 1.0,
    val rightAxis: Boolean = false,
    val rightPresetInverted: Boolean = false
)

object ChartVariables {

    val ALL: List<ChartVariableDef> = listOf(
        ChartVariableDef("temperature", R.string.var_temperature, R.string.var_temperature_short, "temperature_2m",
            true, UnitKind.TEMP, 0xFFEF6C00, width = 5.6f, colorScale = true, foregroundLine = true, gradientFill = true, extrema = true),
        ChartVariableDef("weather_icons", R.string.var_icons, R.string.var_icons_short, "weather_code",
            true, UnitKind.TEMP, 0xFF94A3B8, pictograms = true, marker = true),
        ChartVariableDef("apparent_temperature", R.string.var_apparent, R.string.var_apparent_short, "apparent_temperature",
            true, UnitKind.TEMP, 0xFFC2410C, width = 2f, dashed = true),
        ChartVariableDef("dew_point", R.string.var_dew_point, R.string.var_dew_point_short, "dew_point_2m",
            true, UnitKind.TEMP, 0xFF0E7490, width = 2f),
        ChartVariableDef("cloud_cover", R.string.var_cloud, R.string.var_cloud_short, "cloud_cover",
            true, UnitKind.PERCENT, 0xFF969BA5, cloudBand = true),
        ChartVariableDef("cloud_cover_low", R.string.var_cloud_low, R.string.var_cloud_low_short, "cloud_cover_low",
            true, UnitKind.PERCENT, 0xFF6E7684, cloudBand = true, cloudLayer = "low"),
        ChartVariableDef("cloud_cover_mid", R.string.var_cloud_mid, R.string.var_cloud_mid_short, "cloud_cover_mid",
            true, UnitKind.PERCENT, 0xFF949CAA, cloudBand = true, cloudLayer = "mid"),
        ChartVariableDef("cloud_cover_high", R.string.var_cloud_high, R.string.var_cloud_high_short, "cloud_cover_high",
            true, UnitKind.PERCENT, 0xFFBAC2D0, cloudBand = true, cloudLayer = "high"),
        ChartVariableDef("precipitation", R.string.var_precipitation, R.string.var_precipitation_short, "precipitation",
            false, UnitKind.PRECIP, 0xCC1E88E5),
        ChartVariableDef("precipitation_probability", R.string.var_pop, R.string.var_pop_short, "precipitation_probability",
            true, UnitKind.PERCENT, 0xFF5C6BC0, width = 2f, dashed = true, rightAxis = true),
        ChartVariableDef("rain", R.string.var_rain, R.string.var_rain_short, "rain",
            false, UnitKind.PRECIP, 0xBF2563EB),
        ChartVariableDef("showers", R.string.var_showers, R.string.var_showers_short, "showers",
            false, UnitKind.PRECIP, 0xBF06B6D4),
        ChartVariableDef("snowfall", R.string.var_snowfall, R.string.var_snowfall_short, "snowfall",
            false, UnitKind.SNOW, 0xE693C5FD),
        ChartVariableDef("wind", R.string.var_wind, R.string.var_wind_short, "wind_speed_10m",
            true, UnitKind.WIND, 0xFF26A69A, width = 2f, fill = true, fillOpacity = 0.15f),
        ChartVariableDef("wind_direction", R.string.var_wind_dir, R.string.var_wind_dir_short, "wind_direction_10m",
            true, UnitKind.WIND, 0xFF14B8A6, windArrows = true, marker = true),
        ChartVariableDef("wind_gusts", R.string.var_gusts, R.string.var_gusts_short, "wind_gusts_10m",
            true, UnitKind.WIND, 0xFF0D9488, width = 2f, dashed = true),
        ChartVariableDef("humidity", R.string.var_humidity, R.string.var_humidity_short, "relative_humidity_2m",
            true, UnitKind.PERCENT, 0xFF8D6E63, width = 2f, dashed = true),
        ChartVariableDef("pressure_msl", R.string.var_pressure, R.string.var_pressure_short, "pressure_msl",
            true, UnitKind.PRESSURE, 0xFF7C3AED, width = 2f),
        ChartVariableDef("surface_pressure", R.string.var_surface_pressure, R.string.var_surface_pressure_short, "surface_pressure",
            true, UnitKind.PRESSURE, 0xFFA855F7, width = 2f, dashed = true),
        ChartVariableDef("uv_index", R.string.var_uv, R.string.var_uv_short, "uv_index",
            true, UnitKind.UV, 0xFFEAB308, width = 2f, fill = true, fillOpacity = 0.15f),
        ChartVariableDef("visibility", R.string.var_visibility, R.string.var_visibility_short, "visibility",
            true, UnitKind.DISTANCE, 0xFF0891B2, width = 2f, scaleBy = 0.001),
        ChartVariableDef("cape", R.string.var_cape, R.string.var_cape_short, "cape",
            true, UnitKind.ENERGY, 0xFFDC2626, width = 2f, fill = true, fillOpacity = 0.12f)
    )

    val BY_KEY: Map<String, ChartVariableDef> = ALL.associateBy { it.key }

    fun apiNameOf(def: ChartVariableDef): String = def.api

    /** Unit label for a variable family, honouring user unit settings. */
    fun unitForKind(kind: UnitKind, units: UnitPrefs): String = when (kind) {
        UnitKind.TEMP -> units.temperature.symbol
        UnitKind.PRECIP -> units.precipitation.label
        UnitKind.SNOW -> "cm"
        UnitKind.WIND -> units.windSpeed.label
        UnitKind.PERCENT -> "%"
        UnitKind.PRESSURE -> "hPa"
        UnitKind.UV -> ""
        UnitKind.DISTANCE -> "km"
        UnitKind.ENERGY -> "J/kg"
    }

    /**
     * The set of API hourly variables needed to render the current table rows
     * and chart layout (port of neededHourlyApiVars) — only what is shown
     * gets fetched.
     */
    fun neededHourlyApiVars(tableVisible: Set<String>, layoutKeys: List<String>): Set<String> {
        val s = mutableSetOf<String>()
        fun on(key: String) = key in tableVisible
        if (on("icons")) s += "weather_code"
        if (on("temperature")) s += "temperature_2m"
        if (on("feels")) s += "apparent_temperature"
        if (on("dew_point")) s += "dew_point_2m"
        if (on("wind")) { s += "wind_speed_10m"; s += "wind_direction_10m" }
        if (on("gusts")) s += "wind_gusts_10m"
        if (on("humidity")) s += "relative_humidity_2m"
        if (on("clouds")) s += "cloud_cover"
        if (on("pressure")) s += "pressure_msl"
        if (on("uv")) s += "uv_index"
        if (on("visibility")) s += "visibility"
        if (on("precipitation")) { s += "precipitation"; s += "precipitation_probability" }
        if (on("snowfall")) s += "snowfall"

        for (key in layoutKeys) {
            val def = BY_KEY[key] ?: continue
            s += apiNameOf(def)
            if (def.pictograms) s += "weather_code"
            if (def.windArrows || def.key == "wind") s += "wind_direction_10m"
        }
        return s
    }
}

/**
 * Temperature color scale from the open-meteo weather-map-layer project —
 * the same color language as the website's meteogram temperature lines.
 * Sampled in °C; convert °F values before calling.
 */
object TemperatureScale {
    private val breakpoints = doubleArrayOf(
        -80.0, -65.0, -50.0, -40.0, -32.0, -28.0, -24.0, -20.0, -17.5, -15.0, -12.5, -10.0,
        -8.0, -6.0, -4.0, -2.0, 0.0, 2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0,
        22.0, 24.0, 26.0, 28.0, 30.0, 32.0, 34.0, 36.0, 38.0, 40.0, 42.0, 44.0, 46.0, 48.0, 50.0
    )
    private val colors = intArrayOf(
        0xFF4A0D00.toInt(), 0xFF82011D.toInt(), 0xFFB90472.toInt(), 0xFFDD06C1.toInt(),
        0xFFCF06F1.toInt(), 0xFFA305F3.toInt(), 0xFF7604F6.toInt(), 0xFF4703F9.toInt(),
        0xFF2902FA.toInt(), 0xFF0B01FC.toInt(), 0xFF0116FD.toInt(), 0xFF0034FF.toInt(),
        0xFF2363FB.toInt(), 0xFF458BF7.toInt(), 0xFF66ABF5.toInt(), 0xFF86C5F5.toInt(),
        0xFF72E8A5.toInt(), 0xFF4EE885.toInt(), 0xFF28E960.toInt(), 0xFF11DC3D.toInt(),
        0xFF09BF24.toInt(), 0xFF04A00F.toInt(), 0xFF008000.toInt(), 0xFF28A000.toInt(),
        0xFF60C000.toInt(), 0xFFA7DF00.toInt(), 0xFFFFFF00.toInt(), 0xFFFFED00.toInt(),
        0xFFFFDB00.toInt(), 0xFFFFC900.toInt(), 0xFFFFB700.toInt(), 0xFFFFA500.toInt(),
        0xFFFF8A00.toInt(), 0xFFFF6E00.toInt(), 0xFFFF5300.toInt(), 0xFFFF3700.toInt(),
        0xFFFF1B00.toInt(), 0xFFFF0000.toInt(), 0xFFE4000A.toInt(), 0xFFC90012.toInt(),
        0xFFAE0017.toInt(), 0xFF93001A.toInt()
    )

    /** Interpolated ARGB color for a °C value. */
    fun sample(celsius: Double): Int {
        if (!celsius.isFinite() || celsius <= breakpoints[0]) return colors[0]
        val last = breakpoints.size - 1
        if (celsius >= breakpoints[last]) return colors[last]
        var i = 0
        while (celsius > breakpoints[i + 1]) i++
        val t = ((celsius - breakpoints[i]) / (breakpoints[i + 1] - breakpoints[i])).toFloat()
        val c1 = colors[i]; val c2 = colors[i + 1]
        fun ch(color: Int, shift: Int): Float {
            val v = (color ushr shift) and 0xFF
            val v2 = (c2 ushr shift) and 0xFF
            return (v + (v2 - v) * t) / 255f
        }
        val r = ch(c1, 16); val g = ch(c1, 8); val b = ch(c1, 0)
        val rf = (r * 255).toInt(); val gf = (g * 255).toInt(); val bf = (b * 255).toInt()
        return (0xFF shl 24) or (rf shl 16) or (gf shl 8) or bf
    }
}
