package li.drizz.app.util

import java.util.Locale

/** Number and unit formatting shared by every screen. */
object Fmt {

    fun temp(value: Double?, unit: String): String =
        if (value == null || value.isNaN()) "–" else "${value.round0()}°"

    fun tempWithUnit(value: Double?, unit: String): String =
        if (value == null || value.isNaN()) "–" else "${value.round0()}$unit"

    fun num1(value: Double?): String =
        if (value == null || value.isNaN()) "–" else String.format(Locale.getDefault(), "%.1f", value)

    fun num0(value: Double?): String =
        if (value == null || value.isNaN()) "–" else value.round0().toString()

    fun precip(value: Double?): String =
        if (value == null || value.isNaN()) "–"
        else if (value < 10) String.format(Locale.getDefault(), "%.1f", value)
        else value.round0().toString()

    fun wind(value: Double?, unit: String): String =
        if (value == null || value.isNaN()) "–" else "${value.round0()}"

    fun percent(value: Double?): String =
        if (value == null || value.isNaN()) "–" else "${value.round0()}%"

    /** 52.5200, 13.4050 → "52.52°N 13.41°E" */
    fun coordinates(lat: Double, lon: Double): String =
        "%s %.2f°, %s %.2f°".format(
            Locale.getDefault(),
            if (lat >= 0) "N" else "S",
            kotlin.math.abs(lat),
            if (lon >= 0) "E" else "W",
            kotlin.math.abs(lon)
        )

    fun km(value: Double): String =
        if (value < 10) String.format(Locale.getDefault(), "%.0f", value)
        else value.round0().toString()

    private fun Double.round0(): Long = kotlin.math.round(this).toLong()
}

/** 🇩🇪-style emoji flag for an ISO country code. */
fun countryCodeFlag(code: String?): String {
    if (code.isNullOrBlank() || code.length != 2) return ""
    return code.uppercase().map { Character.toChars(0x1F1E6 + (it.code - 'A'.code)) }
        .joinToString("")
}

/** N/NE/E/… for a wind direction in degrees. */
fun cardinalDirection(value: Double): String {
    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val normalized = ((value % 360) + 360) % 360
    return directions[Math.round(normalized / 45.0).toInt() % 8]
}
