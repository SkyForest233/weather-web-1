package li.drizz.app.domain.model

import kotlinx.serialization.Serializable

/** A searchable / selectable place, mirroring drizz.li's GeoLocation store. */
@Serializable
data class GeoLocation(
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val countryCode: String? = null,
    val country: String? = null,
    val admin1: String? = null,
    val admin3: String? = null,
    val timezone: String = "auto",
    val population: Long? = null
) {
    val slug: String by lazy { locationSlug(this) }
}

/** City slug or `52.52N13.41E` coordinate pair, mirroring the website's routes. */
fun locationSlug(location: GeoLocation): String {
    val name = location.name.trim().lowercase()
        .replace("'", "")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return if (name.isNotEmpty()) name
    else coordinateSlug(location.latitude, location.longitude)
}

fun coordinateSlug(lat: Double, lon: Double): String =
    "%.2f%s%.2f%s".format(
        java.util.Locale.ROOT,
        kotlin.math.abs(lat),
        if (lat >= 0) "N" else "S",
        kotlin.math.abs(lon),
        if (lon >= 0) "E" else "W"
    )

/** Berlin, the website's default location. */
val DEFAULT_LOCATION = GeoLocation(
    id = 2950159,
    name = "Berlin",
    latitude = 52.52437,
    longitude = 13.41053,
    elevation = 74.0,
    countryCode = "DE",
    country = "Germany",
    admin1 = "Land Berlin",
    timezone = "Europe/Berlin",
    population = 3_426_354
)

/** A stretch of daylight between sunrise and sunset, in epoch seconds. */
data class DaylightBand(val startSec: Long, val endSec: Long)

/** Parsed hourly block: values indexed by API variable name, aligned to `time`. */
class HourlySeries(
    val time: List<Long>,
    /** API variable name → values, `null` where the variable was not requested. */
    val variables: Map<String, List<Double?>>,
    val units: Map<String, String>
) {
    operator fun get(name: String): List<Double?> = variables[name] ?: emptyList()
}

/** One day of the 7/15-day forecast (the daily block + derived helpers). */
data class DayForecast(
    val dateKey: String,
    val weatherCode: Double?,
    val tempMax: Double?,
    val tempMin: Double?,
    val sunriseSec: Long,
    val sunsetSec: Long,
    val sunshineDurationSec: Double?,
    val precipitationSum: Double?,
    val windSpeedMax: Double?,
    val windGustsMax: Double?,
    val windDirectionDominant: Double?,
    val daylightDurationSec: Double?,
    val uvIndexMax: Double?,
    val precipitationProbabilityMax: Double?,
    val moonriseSec: Long,
    val moonsetSec: Long,
    val moonPhase: Double?
)

/** Full 7/15-day forecast response. */
data class WeekForecast(
    val location: GeoLocation,
    val hourly: HourlySeries,
    val days: List<DayForecast>,
    val timezone: String,
    val utcOffsetSeconds: Long,
    val daylightBands: List<DaylightBand>
)

/** Historical (ERA5 archive) daily statistics. */
data class HistoricalDaily(
    val dateKey: String,
    val weatherCode: Double?,
    val tempMax: Double?,
    val tempMin: Double?,
    val tempMean: Double?,
    val apparentMax: Double?,
    val apparentMin: Double?,
    val sunriseSec: Long,
    val sunsetSec: Long,
    val sunshineDurationSec: Double?,
    val precipitationSum: Double?,
    val rainSum: Double?,
    val snowfallSum: Double?,
    val precipitationHours: Double?,
    val windSpeedMax: Double?,
    val windGustsMax: Double?,
    val windDirectionDominant: Double?
)

data class HistoricalForecast(
    val hourly: HourlySeries,
    val days: List<HistoricalDaily>,
    val timezone: String,
    val utcOffsetSeconds: Long,
    val daylightBands: List<DaylightBand>
)

/** Ensemble data for one variable: raw members plus average/min/max. */
data class EnsembleVariable(
    val members: List<List<Double?>>,
    val average: List<Double?>,
    val min: List<Double?>,
    val max: List<Double?>,
    val unit: String
)

data class EnsembleForecast(
    val variables: Map<String, EnsembleVariable>,
    val time: List<Long>,
    val timezone: String,
    val utcOffsetSeconds: Long,
    val daylightBands: List<DaylightBand>,
    /** Members beyond the model's horizon are trimmed server-side; surfaced for the note. */
    val memberCount: Int
)

/** Seasonal ensemble member statistics for one daily variable. */
data class SeasonalVariable(
    val members: List<List<Double?>>,
    val mean: List<Double?>,
    val min: List<Double?>,
    val max: List<Double?>,
    val p25: List<Double?>,
    val p75: List<Double?>,
    val unit: String
)

data class SeasonalForecast(
    val variables: Map<String, SeasonalVariable>,
    val time: List<Long>,
    /** `YYYY-MM-DD` location-local keys. */
    val dateKeys: List<String>,
    val memberCount: Int,
    val timezone: String,
    val utcOffsetSeconds: Long
)

/** One model's series in the comparison view. */
data class ModelSeries(
    val modelId: String,
    val variables: Map<String, List<Double?>>
)

data class CompareForecast(
    val models: List<ModelSeries>,
    val time: List<Long>,
    val timezone: String,
    val utcOffsetSeconds: Long,
    val daylightBands: List<DaylightBand>,
    val sunriseSec: List<Long>,
    val sunsetSec: List<Long>,
    val units: Map<String, String>
)

/** Daily climate normals (1991–2020 baseline), indexed by day-of-year 1..366. */
class ClimateNormals(
    val tmax: DoubleArray,
    val tmin: DoubleArray,
    val tmean: DoubleArray,
    val precip: DoubleArray,
    val baseStart: String,
    val baseEnd: String
) {
    fun ordinal(month: Int, day: Int): Int {
        val cum = intArrayOf(0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335)
        val m = month.coerceIn(1, 12)
        return cum[m - 1] + day
    }
}

/** A city suggested by the nearby-cities panel. */
data class NearbyCity(
    val id: Long,
    val name: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val population: Long,
    val distanceKm: Double
)

/** Per-city, per-date daily summary used by the nearby panel. */
class NearbyDaily(val byDate: Map<String, DaySummary>) {
    data class DaySummary(val weatherCode: Double?, val max: Double?, val min: Double?, val precipitation: Double?)
}

/** Human-readable fetch error, ported from humanizeWeatherError(). */
data class FriendlyError(
    val titleRes: Int,
    val hintRes: Int,
    val detail: String? = null
)
