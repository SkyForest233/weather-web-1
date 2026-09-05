package li.drizz.app.data

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import li.drizz.app.R
import li.drizz.app.data.remote.ApiException
import li.drizz.app.data.remote.OpenMeteoApi
import li.drizz.app.data.settings.UnitPrefs
import li.drizz.app.domain.model.ClimateNormals
import li.drizz.app.domain.model.CompareForecast
import li.drizz.app.domain.model.DayForecast
import li.drizz.app.domain.model.DaylightBand
import li.drizz.app.domain.model.EnsembleForecast
import li.drizz.app.domain.model.EnsembleVariable
import li.drizz.app.domain.model.FriendlyError
import li.drizz.app.domain.model.GeoLocation
import li.drizz.app.domain.model.HistoricalDaily
import li.drizz.app.domain.model.HistoricalForecast
import li.drizz.app.domain.model.HourlySeries
import li.drizz.app.domain.model.ModelSeries
import li.drizz.app.domain.model.NearbyDaily
import li.drizz.app.domain.model.SeasonalForecast
import li.drizz.app.domain.model.SeasonalVariable
import li.drizz.app.domain.model.WeekForecast
import li.drizz.app.util.Dates
import java.time.LocalDate
import kotlin.math.ceil

/**
 * Every Open-Meteo request flows through here — the port of
 * src/lib/services/weather.ts. Uses the JSON API (timeformat=unixtime) rather
 * than the website's FlatBuffers transport; the data is identical.
 */
class WeatherRepository(private val api: OpenMeteoApi) {

    companion object {
        val WEEK_HOURLY_VARS = listOf(
            "temperature_2m", "precipitation", "precipitation_probability", "weather_code",
            "wind_speed_10m", "wind_direction_10m", "cloud_cover", "relative_humidity_2m",
            "apparent_temperature", "dew_point_2m"
        )
        val WEEK_DAILY_VARS = listOf(
            "weather_code", "temperature_2m_max", "temperature_2m_min", "sunrise", "sunset",
            "sunshine_duration", "precipitation_sum", "wind_speed_10m_max", "wind_gusts_10m_max",
            "wind_direction_10m_dominant", "daylight_duration", "uv_index_max",
            "precipitation_probability_max", "moonrise", "moonset", "moon_phase"
        )
        val HISTORICAL_DAILY_VARS = listOf(
            "weather_code", "temperature_2m_max", "temperature_2m_min", "temperature_2m_mean",
            "apparent_temperature_max", "apparent_temperature_min", "sunrise", "sunset",
            "sunshine_duration", "precipitation_sum", "rain_sum", "snowfall_sum",
            "precipitation_hours", "wind_speed_10m_max", "wind_gusts_10m_max",
            "wind_direction_10m_dominant"
        )
        val SEASONAL_DAILY_VARS = listOf(
            "temperature_2m_max", "temperature_2m_min", "temperature_2m_mean",
            "precipitation_sum", "wind_speed_10m_mean", "cloud_cover_mean"
        )
        val ENSEMBLE_HOURLY_VARS = listOf("temperature_2m", "precipitation", "wind_speed_10m")
        val COMPARE_VARS = listOf(
            "temperature_2m", "precipitation", "wind_speed_10m", "wind_gusts_10m",
            "relative_humidity_2m", "pressure_msl", "cloud_cover", "weather_code"
        )
        const val SEASONAL_MAX_DAYS = 216
        val unitParamNames = listOf("temperature_unit", "wind_speed_unit", "precipitation_unit")

        private val UNIT_LABELS = mapOf(
            "celsius" to "°C", "fahrenheit" to "°F", "mm" to "mm", "inch" to "in",
            "kmh" to "km/h", "ms" to "m/s", "mph" to "mph", "kn" to "kn",
            "hpa" to "hPa", "deg" to "°", "%x" to "%"
        )

        /** API unit key (`hourly_units` value) → display symbol. */
        fun unitLabel(raw: String?): String {
            if (raw == null) return ""
            UNIT_LABELS[raw]?.let { return it }
            return when {
                raw.startsWith("°") -> raw
                raw == "%" -> "%"
                else -> raw
            }
        }
    }

    private fun commonParams(location: GeoLocation, units: UnitPrefs): MutableList<Pair<String, String?>> = mutableListOf(
        "latitude" to location.latitude.toString(),
        "longitude" to location.longitude.toString(),
        "timezone" to "auto",
        "timeformat" to "unixtime",
        "temperature_unit" to units.temperature.api,
        "wind_speed_unit" to units.windSpeed.api,
        "precipitation_unit" to units.precipitation.api
    )

    // ─── Week forecast ──────────────────────────────────────────────────────

    suspend fun fetchWeek(
        location: GeoLocation,
        units: UnitPrefs,
        model: String,
        forecastDays: Int = 7,
        pastDays: Int = 0,
        hourlyVars: List<String> = WEEK_HOURLY_VARS
    ): WeekForecast {
        val params = commonParams(location, units) + listOf(
            "hourly" to (hourlyVars.distinct().joinToString(",")),
            "daily" to WEEK_DAILY_VARS.joinToString(","),
            "forecast_days" to forecastDays.toString(),
            "past_days" to pastDays.toString(),
            "models" to if (model == "best_match") null else model
        )
        val element = api.get(OpenMeteoApi.FORECAST_URL, params)
        val obj = OpenMeteoApi.asObjectList(element).first()
        return parseWeek(obj, location)
    }

    private fun parseWeek(obj: kotlinx.serialization.json.JsonObject, location: GeoLocation): WeekForecast {
        val timezone = OpenMeteoApi.string(obj, "timezone") ?: "UTC"
        val offset = OpenMeteoApi.int(obj, "utc_offset_seconds") ?: 0
        val hourlyBlock = obj["hourly"]?.let { it as? kotlinx.serialization.json.JsonObject } ?: emptyBlock()
        val dailyBlock = obj["daily"]?.let { it as? kotlinx.serialization.json.JsonObject } ?: emptyBlock()

        val (time, vars) = OpenMeteoApi.parseTimeSeries(hourlyBlock)
        val units = (hourlyBlock.keys - "time").associateWith { unitLabel(hourlyUnits(hourlyBlock, it)) }
        val hourly = HourlySeries(time, vars, units)

        val dayTimes = OpenMeteoApi.longs(dailyBlock, "time")
        val d = { name: String -> OpenMeteoApi.doubles(dailyBlock, name) }
        val sunrise = OpenMeteoApi.longs(dailyBlock, "sunrise")
        val sunset = OpenMeteoApi.longs(dailyBlock, "sunset")
        val moonrise = OpenMeteoApi.longs(dailyBlock, "moonrise")
        val moonset = OpenMeteoApi.longs(dailyBlock, "moonset")

        val days = dayTimes.mapIndexed { i, t ->
            DayForecast(
                dateKey = Dates.dayKey(t, timezone),
                weatherCode = d("weather_code").getOrNull(i),
                tempMax = d("temperature_2m_max").getOrNull(i),
                tempMin = d("temperature_2m_min").getOrNull(i),
                sunriseSec = sunrise.getOrNull(i) ?: 0,
                sunsetSec = sunset.getOrNull(i) ?: 0,
                sunshineDurationSec = d("sunshine_duration").getOrNull(i),
                precipitationSum = d("precipitation_sum").getOrNull(i),
                windSpeedMax = d("wind_speed_10m_max").getOrNull(i),
                windGustsMax = d("wind_gusts_10m_max").getOrNull(i),
                windDirectionDominant = d("wind_direction_10m_dominant").getOrNull(i),
                daylightDurationSec = d("daylight_duration").getOrNull(i),
                uvIndexMax = d("uv_index_max").getOrNull(i),
                precipitationProbabilityMax = d("precipitation_probability_max").getOrNull(i),
                moonriseSec = moonrise.getOrNull(i) ?: 0,
                moonsetSec = moonset.getOrNull(i) ?: 0,
                moonPhase = d("moon_phase").getOrNull(i)
            )
        }
        return WeekForecast(
            location = location,
            hourly = hourly,
            days = days,
            timezone = timezone,
            utcOffsetSeconds = offset,
            daylightBands = buildDaylightBands(sunrise, sunset)
        )
    }

    private fun hourlyUnits(block: kotlinx.serialization.json.JsonObject, key: String): String? =
        (block["units"] as? kotlinx.serialization.json.JsonObject)?.get(key)?.let {
            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
        }

    private fun emptyBlock() = kotlinx.serialization.json.JsonObject(emptyMap())

    private fun buildDaylightBands(sunrise: List<Long>, sunset: List<Long>): List<DaylightBand> =
        sunrise.mapIndexedNotNull { i, s ->
            val e = sunset.getOrNull(i) ?: return@mapIndexedNotNull null
            if (s <= 0 || e <= 0 || e <= s) null else DaylightBand(s, e)
        }

    // ─── Model comparison ───────────────────────────────────────────────────

    /** One request per model, in parallel — the JSON API answers one object each. */
    suspend fun fetchComparison(
        location: GeoLocation,
        units: UnitPrefs,
        models: List<String>,
        hourlyVars: List<String>
    ): CompareForecast = coroutineScope {
        val hourlyJoined = hourlyVars.joinToString(",")
        val requests = models.map { model ->
            async {
                val params = commonParams(location, units) + listOf(
                    "hourly" to hourlyJoined,
                    "daily" to "sunrise,sunset",
                    "models" to if (model == "best_match") null else model
                )
                model to OpenMeteoApi.asObjectList(api.get(OpenMeteoApi.FORECAST_URL, params)).first()
            }
        }
        val results = requests.map { it.await() }
        val first = results.firstOrNull()?.second ?: throw ApiException(0, "no model data")
        val timezone = OpenMeteoApi.string(first, "timezone") ?: "UTC"
        val offset = OpenMeteoApi.int(first, "utc_offset_seconds") ?: 0

        // The common time grid: all models answer the same hourly axis for a
        // location (local midnight to midnight), so take the first response's.
        val (time, _) = first["hourly"]?.let { b ->
            OpenMeteoApi.parseTimeSeries(b as kotlinx.serialization.json.JsonObject)
        } ?: (emptyList<Long>() to emptyMap())

        val series = results.mapNotNull { (model, obj) ->
            val hourly = obj["hourly"] as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val (_, vars) = OpenMeteoApi.parseTimeSeries(hourly)
            ModelSeries(modelId = model, variables = vars)
        }

        val dailyBlock = first["daily"] as? kotlinx.serialization.json.JsonObject
        val sunrise = dailyBlock?.let { OpenMeteoApi.longs(it, "sunrise") } ?: emptyList()
        val sunset = dailyBlock?.let { OpenMeteoApi.longs(it, "sunset") } ?: emptyList()

        val unitMap = (first["hourly_units"] as? kotlinx.serialization.json.JsonObject)
            ?.mapValues { unitLabel((it.value as? kotlinx.serialization.json.JsonPrimitive)?.content) }
            ?: emptyMap()

        CompareForecast(
            models = series,
            time = time,
            timezone = timezone,
            utcOffsetSeconds = offset,
            daylightBands = buildDaylightBands(sunrise, sunset),
            sunriseSec = sunrise,
            sunsetSec = sunset,
            units = unitMap
        )
    }

    // ─── 14-day ensemble ────────────────────────────────────────────────────

    suspend fun fetchEnsemble(
        location: GeoLocation,
        units: UnitPrefs,
        model: String,
        hourlyVars: List<String> = ENSEMBLE_HOURLY_VARS,
        forecastDays: Int = 14
    ): EnsembleForecast = coroutineScope {
        val ensembleJob = async {
            val params = commonParams(location, units) + listOf(
                "hourly" to hourlyVars.joinToString(","),
                "models" to if (model == "best_match") null else model,
                "forecast_days" to forecastDays.toString()
            )
            OpenMeteoApi.asObjectList(api.get(OpenMeteoApi.ENSEMBLE_URL, params)).first()
        }
        val dailyJob = async {
            val params = listOf(
                "latitude" to location.latitude.toString(),
                "longitude" to location.longitude.toString(),
                "daily" to "sunrise,sunset",
                "forecast_days" to forecastDays.toString(),
                "timeformat" to "unixtime",
                "temperature_unit" to units.temperature.api
            )
            OpenMeteoApi.asObjectList(api.get(OpenMeteoApi.FORECAST_URL, params)).first()
        }
        val ensemble = ensembleJob.await()
        val timezone = OpenMeteoApi.string(ensemble, "timezone") ?: "UTC"
        val offset = OpenMeteoApi.int(ensemble, "utc_offset_seconds") ?: 0
        val hourlyBlock = ensemble["hourly"]?.let { it as? kotlinx.serialization.json.JsonObject } ?: emptyBlock()
        val (time, allVars) = OpenMeteoApi.parseTimeSeries(hourlyBlock)
        val memberKeys = allVars.keys.toList()

        val variables = hourlyVars.mapNotNull { name ->
            // Keys are `name`, `name_member01`, `name_member02`, …
            val keys = memberKeys.filter { it == name || it.startsWith("${name}_member") }
                .sortedWith(compareBy({ k -> !k.startsWith("${name}_member") }, { k -> k }))
            if (keys.isEmpty()) return@mapNotNull null
            val members = keys.map { allVars[it]!! }
            val n = time.size
            val avg = DoubleArray(n) { t ->
                var sum = 0.0; var c = 0
                members.forEach { m -> m.getOrNull(t)?.let { if (it.isFinite()) { sum += it; c++ } } }
                if (c > 0) Math.round(sum / c * 10.0) / 10.0 else Double.NaN
            }.toList()
            val mins = DoubleArray(n) { t ->
                members.mapNotNull { it.getOrNull(t) }.filter { it.isFinite() }.minOrNull() ?: Double.NaN
            }.toList()
            val maxs = DoubleArray(n) { t ->
                members.mapNotNull { it.getOrNull(t) }.filter { it.isFinite() }.maxOrNull() ?: Double.NaN
            }.toList()
            name to EnsembleVariable(members, avg, mins, maxs, unit = "")
        }.toMap()

        val daily = dailyJob.await()
        val bands = (daily["daily"] as? kotlinx.serialization.json.JsonObject)?.let {
            buildDaylightBands(OpenMeteoApi.longs(it, "sunrise"), OpenMeteoApi.longs(it, "sunset"))
        } ?: emptyList()

        EnsembleForecast(
            variables = variables,
            time = time,
            timezone = timezone,
            utcOffsetSeconds = offset,
            daylightBands = bands,
            memberCount = variables.values.firstOrNull()?.members?.size ?: 0
        )
    }

    // ─── Historical archive ─────────────────────────────────────────────────

    suspend fun fetchHistorical(
        location: GeoLocation,
        units: UnitPrefs,
        startDate: String,
        endDate: String,
        model: String,
        hourlyVars: List<String> = WEEK_HOURLY_VARS
    ): HistoricalForecast {
        val params = mutableListOf<Pair<String, String?>>(
            "latitude" to location.latitude.toString(),
            "longitude" to location.longitude.toString(),
            "start_date" to startDate,
            "end_date" to endDate,
            "hourly" to hourlyVars.distinct().joinToString(","),
            "daily" to HISTORICAL_DAILY_VARS.joinToString(","),
            "timeformat" to "unixtime",
            "temperature_unit" to units.temperature.api,
            "wind_speed_unit" to units.windSpeed.api,
            "precipitation_unit" to units.precipitation.api
        )
        if (model != "best_match") params += "models" to model
        val obj = OpenMeteoApi.asObjectList(api.get(OpenMeteoApi.ARCHIVE_URL, params)).first()
        val timezone = OpenMeteoApi.string(obj, "timezone") ?: "UTC"
        val offset = OpenMeteoApi.int(obj, "utc_offset_seconds") ?: 0
        val hourlyBlock = obj["hourly"]?.let { it as? kotlinx.serialization.json.JsonObject } ?: emptyBlock()
        val (time, vars) = OpenMeteoApi.parseTimeSeries(hourlyBlock)

        val dailyBlock = obj["daily"]?.let { it as? kotlinx.serialization.json.JsonObject } ?: emptyBlock()
        val dayTimes = OpenMeteoApi.longs(dailyBlock, "time")
        val d = { name: String -> OpenMeteoApi.doubles(dailyBlock, name) }
        val sunrise = OpenMeteoApi.longs(dailyBlock, "sunrise")
        val sunset = OpenMeteoApi.longs(dailyBlock, "sunset")

        val days = dayTimes.mapIndexed { i, t ->
            HistoricalDaily(
                dateKey = Dates.dayKey(t, timezone),
                weatherCode = d("weather_code").getOrNull(i),
                tempMax = d("temperature_2m_max").getOrNull(i),
                tempMin = d("temperature_2m_min").getOrNull(i),
                tempMean = d("temperature_2m_mean").getOrNull(i),
                apparentMax = d("apparent_temperature_max").getOrNull(i),
                apparentMin = d("apparent_temperature_min").getOrNull(i),
                sunriseSec = sunrise.getOrNull(i) ?: 0,
                sunsetSec = sunset.getOrNull(i) ?: 0,
                sunshineDurationSec = d("sunshine_duration").getOrNull(i),
                precipitationSum = d("precipitation_sum").getOrNull(i),
                rainSum = d("rain_sum").getOrNull(i),
                snowfallSum = d("snowfall_sum").getOrNull(i),
                precipitationHours = d("precipitation_hours").getOrNull(i),
                windSpeedMax = d("wind_speed_10m_max").getOrNull(i),
                windGustsMax = d("wind_gusts_10m_max").getOrNull(i),
                windDirectionDominant = d("wind_direction_10m_dominant").getOrNull(i)
            )
        }
        return HistoricalForecast(
            hourly = HourlySeries(time, vars, emptyMap()),
            days = days,
            timezone = timezone,
            utcOffsetSeconds = offset,
            daylightBands = buildDaylightBands(sunrise, sunset)
        )
    }

    /**
     * Daily climate normals 1991–2020: one archive request, averaged per
     * day-of-year with a circular ±7-day smoothing window (the port of
     * fetchClimateNormals).
     */
    suspend fun fetchClimateNormals(
        location: GeoLocation,
        units: UnitPrefs,
        baseStart: String = "1991-01-01",
        baseEnd: String = "2020-12-31"
    ): ClimateNormals {
        val params = listOf(
            "latitude" to location.latitude.toString(),
            "longitude" to location.longitude.toString(),
            "start_date" to baseStart,
            "end_date" to baseEnd,
            "daily" to "temperature_2m_max,temperature_2m_min,temperature_2m_mean,precipitation_sum",
            "temperature_unit" to units.temperature.api,
            "precipitation_unit" to units.precipitation.api,
            "timezone" to "UTC",
            "timeformat" to "unixtime"
        )
        val obj = OpenMeteoApi.asObjectList(api.get(OpenMeteoApi.ARCHIVE_URL, params)).first()
        val dailyBlock = obj["daily"]?.let { it as? kotlinx.serialization.json.JsonObject } ?: emptyBlock()
        val times = OpenMeteoApi.longs(dailyBlock, "time")
        val tmax = OpenMeteoApi.doubles(dailyBlock, "temperature_2m_max")
        val tmin = OpenMeteoApi.doubles(dailyBlock, "temperature_2m_min")
        val tmean = OpenMeteoApi.doubles(dailyBlock, "temperature_2m_mean")
        val precip = OpenMeteoApi.doubles(dailyBlock, "precipitation_sum")

        val N = 367
        class Acc { val sum = DoubleArray(N); val cnt = IntArray(N) }
        val acc = mapOf("tmax" to Acc(), "tmin" to Acc(), "tmean" to Acc(), "precip" to Acc())
        val normals = ClimateNormals(
            DoubleArray(N), DoubleArray(N), DoubleArray(N), DoubleArray(N), baseStart, baseEnd
        )
        times.forEachIndexed { i, t ->
            val date = java.time.Instant.ofEpochSecond(t).atZone(java.time.ZoneOffset.UTC).toLocalDate()
            val ord = normals.ordinal(date.monthValue, date.dayOfMonth)
            fun add(name: String, v: Double?) {
                if (v != null && v.isFinite()) { acc.getValue(name).sum[ord] += v; acc.getValue(name).cnt[ord]++ }
            }
            add("tmax", tmax.getOrNull(i)); add("tmin", tmin.getOrNull(i))
            add("tmean", tmean.getOrNull(i)); add("precip", precip.getOrNull(i))
        }
        fun smooth(name: String): DoubleArray {
            val src = acc.getValue(name)
            val mean = DoubleArray(N) { o -> if (src.cnt[o] > 0) src.sum[o] / src.cnt[o] else Double.NaN }
            val out = DoubleArray(N) { Double.NaN }
            for (o in 1..366) {
                var s = 0.0; var c = 0
                for (k in -7..7) {
                    val idx = ((o - 1 + k + 366) % 366) + 1
                    val v = mean[idx]
                    if (v.isFinite()) { s += v; c++ }
                }
                if (c > 0) out[o] = s / c
            }
            return out
        }
        return ClimateNormals(
            smooth("tmax"), smooth("tmin"), smooth("tmean"), smooth("precip"), baseStart, baseEnd
        )
    }

    // ─── Seasonal outlook ───────────────────────────────────────────────────

    suspend fun fetchSeasonal(
        location: GeoLocation,
        units: UnitPrefs,
        model: String,
        dailyVars: List<String> = SEASONAL_DAILY_VARS,
        forecastDays: Int = SEASONAL_MAX_DAYS
    ): SeasonalForecast {
        val params = commonParams(location, units) + listOf(
            "daily" to dailyVars.joinToString(","),
            "forecast_days" to minOf(forecastDays, SEASONAL_MAX_DAYS).toString(),
            "models" to if (model == "best_match") null else model
        )
        val obj = OpenMeteoApi.asObjectList(api.get(OpenMeteoApi.SEASONAL_URL, params)).first()
        val timezone = OpenMeteoApi.string(obj, "timezone") ?: "UTC"
        val offset = OpenMeteoApi.int(obj, "utc_offset_seconds") ?: 0
        val dailyBlock = obj["daily"]?.let { it as? kotlinx.serialization.json.JsonObject } ?: emptyBlock()
        val (time, allVars) = OpenMeteoApi.parseTimeSeries(dailyBlock)
        val memberCount = if (dailyVars.isNotEmpty()) allVars.size / dailyVars.size else 0

        val variables = dailyVars.mapNotNull { name ->
            val keys = allVars.keys.filter { it == name || it.startsWith("${name}_member") }
                .sortedWith(compareBy({ k -> !k.startsWith("${name}_member") }, { k -> k }))
            if (keys.isEmpty()) return@mapNotNull null
            val members = keys.map { allVars[it]!! }
            val n = time.size
            val mean = DoubleArray(n); val min = DoubleArray(n); val max = DoubleArray(n)
            val p25 = DoubleArray(n); val p75 = DoubleArray(n)
            for (t in 0 until n) {
                val vals = members.mapNotNull { it.getOrNull(t) }.filter { it.isFinite() }.sorted()
                if (vals.isEmpty()) { mean[t] = Double.NaN; min[t] = Double.NaN; max[t] = Double.NaN; p25[t] = Double.NaN; p75[t] = Double.NaN; continue }
                mean[t] = vals.average()
                min[t] = vals.first(); max[t] = vals.last()
                p25[t] = percentile(vals, 0.25); p75[t] = percentile(vals, 0.75)
            }
            name to SeasonalVariable(members, mean.toList(), min.toList(), max.toList(), p25.toList(), p75.toList(), "")
        }.toMap()

        // Trim trailing days past the model's own horizon (all-zero spread).
        var validLength = time.size
        variables[dailyVars.first()]?.let { sentinel ->
            var last = 0
            for (t in time.indices) {
                val hasSpread = !(sentinel.min[t] == 0.0 && sentinel.max[t] == 0.0)
                if (sentinel.mean[t].isFinite() && hasSpread) last = t + 1
            }
            if (last > 0) validLength = last
        }

        fun <T> List<T>.trim() = this.take(validLength)
        val trimmedVars = variables.mapValues { (_, v) ->
            SeasonalVariable(
                v.members.map { it.trim() }, v.mean.trim(), v.min.trim(),
                v.max.trim(), v.p25.trim(), v.p75.trim(), v.unit
            )
        }
        val dateKeys = time.take(validLength).map { t ->
            java.time.Instant.ofEpochSecond(t + offset).toLocalDateUtc()
        }
        return SeasonalForecast(
            variables = trimmedVars,
            time = time.trim(),
            dateKeys = dateKeys,
            memberCount = memberCount,
            timezone = timezone,
            utcOffsetSeconds = offset
        )
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return Double.NaN
        if (sorted.size == 1) return sorted[0]
        val pos = (sorted.size - 1) * p
        val lo = kotlin.math.floor(pos).toInt()
        val hi = kotlin.math.ceil(pos).toInt()
        if (lo == hi) return sorted[lo]
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)
    }

    // ─── Nearby cities daily snapshot ───────────────────────────────────────

    /** Multi-point request: the API answers one object per coordinate, in order. */
    suspend fun fetchNearbyDaily(
        points: List<Pair<Double, Double>>,
        units: UnitPrefs,
        pastDays: Int = 3,
        forecastDays: Int = 16
    ): List<NearbyDaily?> {
        if (points.isEmpty()) return emptyList()
        val params = listOf(
            "latitude" to points.joinToString(",") { it.first.toString() },
            "longitude" to points.joinToString(",") { it.second.toString() },
            "daily" to "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum",
            "temperature_unit" to units.temperature.api,
            "wind_speed_unit" to units.windSpeed.api,
            "precipitation_unit" to units.precipitation.api,
            "past_days" to pastDays.toString(),
            "forecast_days" to forecastDays.toString(),
            "timezone" to "auto",
            "timeformat" to "unixtime"
        )
        val element = api.get(OpenMeteoApi.FORECAST_URL, params)
        val objects = OpenMeteoApi.asObjectList(element)
        return points.indices.map { i ->
            val obj = objects.getOrNull(i) ?: return@map null
            val dailyBlock = obj["daily"] as? kotlinx.serialization.json.JsonObject ?: return@map null
            val offset = OpenMeteoApi.int(obj, "utc_offset_seconds") ?: 0
            val times = OpenMeteoApi.longs(dailyBlock, "time")
            val codes = OpenMeteoApi.doubles(dailyBlock, "weather_code")
            val max = OpenMeteoApi.doubles(dailyBlock, "temperature_2m_max")
            val min = OpenMeteoApi.doubles(dailyBlock, "temperature_2m_min")
            val precip = OpenMeteoApi.doubles(dailyBlock, "precipitation_sum")
            val byDate = times.mapIndexedNotNull { d, t ->
                val key = java.time.Instant.ofEpochSecond(t + offset).toLocalDateUtc()
                NearbyDaily.DaySummary(codes.getOrNull(d), max.getOrNull(d), min.getOrNull(d), precip.getOrNull(d))
                    .let { key to it }
            }.toMap()
            NearbyDaily(byDate)
        }
    }

    // ─── Geocoding ──────────────────────────────────────────────────────────

    suspend fun geocodeSearch(query: String, count: Int = 10): List<GeoLocation> {
        val element = api.get(
            OpenMeteoApi.GEOCODING_URL,
            listOf(
                "name" to query, "count" to count.toString(),
                "language" to currentGeocodingLanguage(), "format" to "json"
            )
        )
        val results = (element as? kotlinx.serialization.json.JsonObject)?.get("results") as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return results.mapNotNull { el ->
            val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            GeoLocation(
                id = OpenMeteoApi.int(o, "id") ?: 0,
                name = OpenMeteoApi.string(o, "name") ?: return@mapNotNull null,
                latitude = OpenMeteoApi.double(o, "latitude") ?: return@mapNotNull null,
                longitude = OpenMeteoApi.double(o, "longitude") ?: return@mapNotNull null,
                elevation = OpenMeteoApi.double(o, "elevation"),
                countryCode = OpenMeteoApi.string(o, "country_code"),
                country = OpenMeteoApi.string(o, "country"),
                admin1 = OpenMeteoApi.string(o, "admin1"),
                admin3 = OpenMeteoApi.string(o, "admin3"),
                timezone = OpenMeteoApi.string(o, "timezone") ?: "auto",
                population = OpenMeteoApi.int(o, "population")
            )
        }
    }

    suspend fun geocodeById(id: Long): GeoLocation? {
        val element = api.get(OpenMeteoApi.GEOCODING_GET_URL, listOf("id" to id.toString(), "format" to "json"))
        val o = OpenMeteoApi.asObjectList(element).firstOrNull() ?: return null
        return GeoLocation(
            id = OpenMeteoApi.int(o, "id") ?: id,
            name = OpenMeteoApi.string(o, "name") ?: return null,
            latitude = OpenMeteoApi.double(o, "latitude") ?: return null,
            longitude = OpenMeteoApi.double(o, "longitude") ?: return null,
            elevation = OpenMeteoApi.double(o, "elevation"),
            countryCode = OpenMeteoApi.string(o, "country_code"),
            country = OpenMeteoApi.string(o, "country"),
            admin1 = OpenMeteoApi.string(o, "admin1"),
            admin3 = OpenMeteoApi.string(o, "admin3"),
            timezone = OpenMeteoApi.string(o, "timezone") ?: "auto",
            population = OpenMeteoApi.int(o, "population")
        )
    }

    private fun currentGeocodingLanguage(): String {
        val locale = java.util.Locale.getDefault().language
        return if (locale in setOf("en", "de", "es", "fr", "it")) locale else "en"
    }
}

/** Instant already shifted by the location offset → "yyyy-MM-dd" (UTC read). */
fun java.time.Instant.toLocalDateUtc(): String =
    atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()

/**
 * Maps any fetch failure onto the website's friendly-error buckets.
 * Port of humanizeWeatherError().
 */
fun humanizeError(err: Throwable): FriendlyError = when {
    err is ApiException && (
        err.message?.contains("no data is available", ignoreCase = true) == true ||
            err.message?.contains("not available for this location", ignoreCase = true) == true ||
            err.message?.contains("out of allowed range", ignoreCase = true) == true ||
            err.message?.contains("coordinates", ignoreCase = true) == true
        ) -> FriendlyError(R.string.err_nodata_title, R.string.err_nodata_hint, err.message)
    err is java.io.IOException ->
        FriendlyError(R.string.err_network_title, R.string.err_network_hint, err.message)
    err is ApiException && (
        err.message?.contains("invalid", ignoreCase = true) == true ||
            err.message?.contains("cannot be", ignoreCase = true) == true
        ) -> FriendlyError(R.string.err_rejected_title, R.string.err_rejected_hint, err.message)
    else -> FriendlyError(R.string.err_generic_title, R.string.err_generic_hint, err.message)
}

/** Returns "yesterday"… style support dates used by default ranges. */
fun LocalDate.toApiDate(): String = toString()
