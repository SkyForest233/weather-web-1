package li.drizz.app.domain

import li.drizz.app.R

/**
 * WMO weather codes → glyph family + localized name, restricted to the codes
 * Open-Meteo actually emits (the port of src/routes/weather/utils/weather-codes.ts).
 */
enum class WeatherGlyph {
    CLEAR_DAY, CLEAR_NIGHT, CLOUDY_DAY, CLOUDY_NIGHT, OVERCAST,
    FOG, SPRINKLE, RAIN, RAIN_MIX, SNOW, SHOWERS, THUNDERSTORM, STORM_SHOWERS
}

private val codeToFamily: Map<Int, String> = mapOf(
    0 to "clear", 1 to "clear", 2 to "cloudy", 3 to "overcast",
    45 to "fog", 48 to "fog",
    51 to "sprinkle", 53 to "sprinkle", 55 to "rain", 56 to "rain-mix", 57 to "rain-mix",
    61 to "sprinkle", 63 to "rain", 65 to "rain", 66 to "rain-mix", 67 to "rain-mix",
    71 to "snow", 73 to "snow", 75 to "snow", 77 to "snow",
    80 to "showers", 81 to "showers", 82 to "rain", 85 to "snow", 86 to "snow",
    95 to "thunderstorm", 96 to "thunderstorm", 97 to "thunderstorm", 99 to "storm-showers"
)

/** Localized plain-language name for each emitted code. */
fun weatherDescriptionRes(code: Double?): Int = when (code?.toInt()) {
    0 -> R.string.wmo_0
    1 -> R.string.wmo_1
    2 -> R.string.wmo_2
    3 -> R.string.wmo_3
    45 -> R.string.wmo_45
    48 -> R.string.wmo_48
    51 -> R.string.wmo_51
    53 -> R.string.wmo_53
    55 -> R.string.wmo_55
    56 -> R.string.wmo_56
    57 -> R.string.wmo_57
    61 -> R.string.wmo_61
    63 -> R.string.wmo_63
    65 -> R.string.wmo_65
    66 -> R.string.wmo_66
    67 -> R.string.wmo_67
    71 -> R.string.wmo_71
    73 -> R.string.wmo_73
    75 -> R.string.wmo_75
    77 -> R.string.wmo_77
    80 -> R.string.wmo_80
    81 -> R.string.wmo_81
    82 -> R.string.wmo_82
    85 -> R.string.wmo_85
    86 -> R.string.wmo_86
    95 -> R.string.wmo_95
    96 -> R.string.wmo_96
    97 -> R.string.wmo_97
    99 -> R.string.wmo_99
    else -> R.string.wmo_0
}

fun hasWeatherIcon(code: Double?): Boolean =
    code != null && code.isFinite() && codeToFamily.containsKey(code.toInt())

/** Glyph (with day/night variant) for a code; overcast stays neutral. */
fun weatherGlyph(code: Double?, daytime: Boolean): WeatherGlyph {
    val family = codeToFamily[code?.toInt()] ?: "clear"
    return when (family) {
        "clear" -> if (daytime) WeatherGlyph.CLEAR_DAY else WeatherGlyph.CLEAR_NIGHT
        "cloudy" -> if (daytime) WeatherGlyph.CLOUDY_DAY else WeatherGlyph.CLOUDY_NIGHT
        "overcast" -> WeatherGlyph.OVERCAST
        "fog" -> WeatherGlyph.FOG
        "sprinkle" -> WeatherGlyph.SPRINKLE
        "rain" -> WeatherGlyph.RAIN
        "rain-mix" -> WeatherGlyph.RAIN_MIX
        "snow" -> WeatherGlyph.SNOW
        "showers" -> WeatherGlyph.SHOWERS
        "thunderstorm" -> WeatherGlyph.THUNDERSTORM
        else -> WeatherGlyph.STORM_SHOWERS
    }
}

/**
 * Daily weather_code from the API is a plain max over all 24 hourly codes;
 * the site derives better day/night split icons from the hourly codes
 * (computeDayNightWeatherCodes). Returns the dominant code for the day-time
 * hours and the night-time hours of each day.
 */
fun computeDayNightCodes(
    hourlyTime: List<Long>,
    hourlyCodes: List<Double?>,
    sunriseSec: List<Long>,
    sunsetSec: List<Long>,
    timezone: String
): List<Pair<Double?, Double?>> {
    val daySlots = mutableListOf<MutableList<Double?>>()
    val nightSlots = mutableListOf<MutableList<Double?>>()
    val days = maxOf(sunriseSec.size, sunsetSec.size)

    fun slotIndex(t: Long): Int? {
        for (d in 0 until days) {
            val sr = sunriseSec.getOrNull(d) ?: 0
            val ss = sunsetSec.getOrNull(d) ?: 0
            if (sr in 1 until ss && t in sr until ss) return d
        }
        return null
    }

    // Fallback split (no reliable sunrise data): 06–18 local is day.
    val fallbackDay = hourlyTime.map { t ->
        val h = li.drizz.app.util.Dates.hourOf(t, timezone)
        h in 6..17
    }

    hourlyTime.forEachIndexed { i, t ->
        val code = hourlyCodes.getOrNull(i) ?: return@forEachIndexed
        val dayIdx = slotIndex(t)
        if (dayIdx != null) {
            while (daySlots.size <= dayIdx) { daySlots += mutableListOf(); nightSlots += mutableListOf() }
            daySlots[dayIdx] += code
        } else {
            // Attribute to the nearest known day bucket, or the fallback split.
            var nearest = -1
            var bestDist = Long.MAX_VALUE
            for (d in 0 until days) {
                val sr = sunriseSec.getOrNull(d) ?: 0
                val ss = sunsetSec.getOrNull(d) ?: 0
                if (sr <= 0) continue
                val dist = if (t < sr) sr - t else if (t > ss) t - ss else 0
                if (dist < bestDist) { bestDist = dist; nearest = d }
            }
            if (nearest >= 0) {
                while (nightSlots.size <= nearest) { daySlots += mutableListOf(); nightSlots += mutableListOf() }
                nightSlots[nearest] += code
            } else if (i < fallbackDay.size) {
                if (fallbackDay[i]) {
                    (daySlots.lastOrNull() ?: mutableListOf<Double?>().also { daySlots += it }).add(code)
                } else {
                    (nightSlots.lastOrNull() ?: mutableListOf<Double?>().also { nightSlots += it }).add(code)
                }
            }
        }
    }

    return (0 until days).map { i ->
        dominant(daySlots.getOrNull(i).orEmpty()) to dominant(nightSlots.getOrNull(i).orEmpty())
    }
}

private fun dominant(codes: List<Double?>): Double? {
    if (codes.isEmpty()) return null
    // The site ranks severity: a third of the window under a disruptive sky
    // defines the period. Rank order mirrors categoryOf() + code value.
    return codes.filterNotNull().groupBy { it }.maxByOrNull { (_, v) -> v.size.toLong() * 1000 + (v.maxOrNull() ?: 0.0) }?.key
}
