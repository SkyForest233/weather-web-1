package li.drizz.app.domain

import android.content.Context
import li.drizz.app.R
import li.drizz.app.data.settings.UnitPrefs
import li.drizz.app.domain.model.DayForecast
import li.drizz.app.domain.model.HourlySeries
import li.drizz.app.util.Dates

/**
 * The day-in-words summary — a port of forecast-text.ts. Everything derives
 * from the same arrays the charts plot, so wording can never disagree with
 * them. Each locale owns whole sentences (placeholders only), and a stable
 * per-day seed varies the phrasing without churning on re-render.
 */
object Narrative {

    private enum class Category(val rank: Int, val res: Int) {
        CLEAR(0, R.string.cond_clear), FAIR(1, R.string.cond_fair), CLOUDY(2, R.string.cond_cloudy),
        FOG(3, R.string.cond_fog), DRIZZLE(4, R.string.cond_drizzle), RAIN(5, R.string.cond_rain),
        SNOW(6, R.string.cond_snow), THUNDER(7, R.string.cond_thunder)
    }

    private class Period(val res: Int, val from: Int, val to: Int)

    private val PERIODS = listOf(
        Period(R.string.period_overnight, 0, 6),
        Period(R.string.period_morning, 6, 12),
        Period(R.string.period_afternoon, 12, 18),
        Period(R.string.period_evening, 18, 24)
    )

    private fun categoryOf(code: Int): Category = when {
        code >= 95 -> Category.THUNDER
        code >= 85 -> Category.SNOW
        code >= 80 -> Category.RAIN
        code >= 71 -> Category.SNOW
        code >= 66 -> Category.SNOW
        code >= 61 -> Category.RAIN
        code >= 51 -> Category.DRIZZLE
        code >= 45 -> Category.FOG
        code == 3 -> Category.CLOUDY
        code == 1 || code == 2 -> Category.FAIR
        else -> Category.CLEAR
    }

    private fun seedFrom(key: String): Int {
        var h = 2166136261L
        for (c in key) { h = h xor c.code.toLong(); h *= 16777619L }
        return (h and Long.MAX_VALUE).toInt()
    }

    private fun <T> pick(variants: List<T>, seed: Int, salt: Int): T = variants[(seed + salt) % variants.size]

    private fun capitalise(s: String) = s.replaceFirstChar { it.uppercase() }

    /** Builds the summary sentences for `day`. Empty when there is no data. */
    fun buildDayNarrative(
        context: Context,
        hourly: HourlySeries,
        days: List<DayForecast>,
        timezone: String,
        dayDateKey: String,
        units: UnitPrefs
    ): List<String> {
        val idx = hourly.time.indices.filter { Dates.dayKey(hourly.time[it], timezone) == dayDateKey }
        if (idx.isEmpty()) return emptyList()

        val dayIndex = days.indexOfFirst { it.dateKey == dayDateKey }
        val tempUnit = Units.tempUnit(units)
        val windUnit = Units.windUnit(units)
        val precipUnit = Units.precipUnit(units)
        val hourOf = { i: Int -> Dates.hourOf(hourly.time[i], timezone) }
        fun temp(v: Double) = "${v.toInt()}$tempUnit"
        fun speed(v: Double) = "${v.toInt()} $windUnit"

        val seed = seedFrom(dayDateKey + timezone)
        val finite: (Double?) -> Boolean = { it != null && it.isFinite() }

        // ── Sky through the day ──
        data class Segment(val period: Period, val category: Category)
        val segments = mutableListOf<Segment>()
        for (period in PERIODS) {
            val inPeriod = idx.filter { hourOf(it) >= period.from && hourOf(it) < period.to }
            if (inPeriod.size < 2) continue
            val codes = inPeriod.mapNotNull { hourly["weather_code"].getOrNull(it)?.takeIf { c -> finite(c) }?.toInt() }
            val cat = dominantCategory(codes) ?: continue
            segments += Segment(period, cat)
        }

        val sky: String? = if (segments.isEmpty()) null else {
            val runs = mutableListOf<Segment>()
            for (seg in segments) {
                val last = runs.lastOrNull()
                if (last == null || last.category != seg.category) runs += seg
            }
            fun phrase(r: Segment) = context.getString(r.category.res)
            fun p(r: Segment) = context.getString(r.period.res)
            when {
                runs.size == 1 -> capitalise(
                    context.getString(pick(listOf(R.string.sky_all_1, R.string.sky_all_2, R.string.sky_all_3), seed, 0), phrase(runs[0]))
                )
                runs.size == 2 -> capitalise(
                    context.getString(
                        pick(listOf(R.string.sky_two_1, R.string.sky_two_2, R.string.sky_two_3), seed, 1),
                        phrase(runs[0]), p(runs[0]), phrase(runs[1]), p(runs[1])
                    )
                )
                else -> {
                    val kept = listOf(runs[0], runs[1], runs.last())
                    capitalise(
                        context.getString(
                            pick(listOf(R.string.sky_three_1, R.string.sky_three_2, R.string.sky_three_3), seed, 2),
                            phrase(kept[0]), p(kept[0]), phrase(kept[1]), p(kept[1]), phrase(kept[2]), p(kept[2])
                        )
                    )
                }
            }
        }

        // ── Temperature ──
        val temps = idx.mapNotNull { hourly["temperature_2m"].getOrNull(it) }.filter(finite)
        val temperature: String? = if (temps.isEmpty()) null else {
            val high = temps.max(); val low = temps.min()
            val feels = idx.mapNotNull { hourly["apparent_temperature"].getOrNull(it) }.filter(finite)
            val feelsHigh = feels.maxOrNull()
            if (feelsHigh != null && kotlin.math.abs(feelsHigh - high) >= 3) {
                context.getString(pick(listOf(R.string.temp_feels_1, R.string.temp_feels_2, R.string.temp_feels_3), seed, 3), temp(high), temp(low), temp(feelsHigh))
            } else {
                context.getString(pick(listOf(R.string.temp_1, R.string.temp_2, R.string.temp_3), seed, 4), temp(high), temp(low))
            }
        }

        // ── Precipitation ──
        val total = idx.mapNotNull { hourly["precipitation"].getOrNull(it) }.filter(finite).sum()
        val probs = idx.mapNotNull { hourly["precipitation_probability"].getOrNull(it) }.filter(finite)
        val peakProb = probs.maxOrNull() ?: 0.0
        val wetThreshold = if (precipUnit == "in") 0.004 else 0.1
        val precipitation: String = when {
            total >= wetThreshold -> {
                var bestPeriod: Period? = null
                var bestAmount = 0.0
                for (period in PERIODS) {
                    val amount = idx.filter { hourOf(it) >= period.from && hourOf(it) < period.to }
                        .mapNotNull { hourly["precipitation"].getOrNull(it) }.filter(finite).sum()
                    if (amount > bestAmount) { bestAmount = amount; bestPeriod = period }
                }
                val amount = "${if (total < 10) String.format(java.util.Locale.getDefault(), "%.1f", total) else total.toInt().toString()} $precipUnit"
                if (bestPeriod != null && bestAmount / total >= 0.5) {
                    context.getString(pick(listOf(R.string.precip_window_1, R.string.precip_window_2, R.string.precip_window_3), seed, 5), amount, context.getString(bestPeriod.res))
                } else {
                    context.getString(pick(listOf(R.string.precip_spread_1, R.string.precip_spread_2, R.string.precip_spread_3), seed, 6), amount)
                }
            }
            peakProb >= 30 -> context.getString(pick(listOf(R.string.precip_chance_1, R.string.precip_chance_2, R.string.precip_chance_3), seed, 7), peakProb.toInt().toString())
            else -> context.getString(pick(listOf(R.string.precip_dry_1, R.string.precip_dry_2, R.string.precip_dry_3), seed, 8))
        }

        // ── Wind ──
        val winds = idx.mapNotNull { hourly["wind_speed_10m"].getOrNull(it) }.filter(finite)
        val wind: String? = if (winds.isEmpty()) null else {
            val maxWind = winds.max()
            val dir = if (dayIndex >= 0) days.getOrNull(dayIndex)?.windDirectionDominant else null
            val gusts = idx.mapNotNull { hourly["wind_gusts_10m"].getOrNull(it) }.filter(finite)
            val maxGust = gusts.maxOrNull() ?: 0.0
            val gusty = maxGust > maxWind * 1.4
            val calm = maxWind < when (windUnit) {
                "m/s" -> 1.5; "kn" -> 3.0; else -> 5.0
            }
            if (calm && !gusty) {
                context.getString(pick(listOf(R.string.calm_1, R.string.calm_2, R.string.calm_3), seed, 9))
            } else if (dir != null && finite(dir)) {
                val direction = Units.windDirectionLabel(dir, context::getString)
                if (gusty) {
                    context.getString(pick(listOf(R.string.wind_dir_gusts_1, R.string.wind_dir_gusts_2, R.string.wind_dir_gusts_3), seed, 10), direction, speed(maxWind), speed(maxGust))
                } else {
                    context.getString(pick(listOf(R.string.wind_dir_1, R.string.wind_dir_2, R.string.wind_dir_3), seed, 11), direction, speed(maxWind))
                }
            } else if (gusty) {
                context.getString(pick(listOf(R.string.wind_gusts_1, R.string.wind_gusts_2, R.string.wind_gusts_3), seed, 12), speed(maxWind), speed(maxGust))
            } else {
                context.getString(pick(listOf(R.string.wind_1, R.string.wind_2, R.string.wind_3), seed, 13), speed(maxWind))
            }
        }

        // ── UV ──
        val uv = if (dayIndex >= 0) days.getOrNull(dayIndex)?.uvIndexMax else null
        val ultraviolet: String? = if (uv != null && finite(uv) && uv >= 6) {
            context.getString(pick(listOf(R.string.uv_1, R.string.uv_2, R.string.uv_3), seed, 14), uv.toInt().toString(), uvLabel(context, uv).lowercase())
        } else null

        val orders: List<List<String?>> = listOf(
            listOf(temperature, precipitation, wind),
            listOf(precipitation, temperature, wind),
            listOf(temperature, wind, precipitation),
            listOf(wind, temperature, precipitation)
        )
        val middle = pick(orders, seed, 15)
        return (listOf(sky) + middle + listOf(ultraviolet)).filterNotNull()
    }

    private fun dominantCategory(codes: List<Int>): Category? {
        if (codes.isEmpty()) return null
        val counts = codes.groupingBy { categoryOf(it) }.eachCount()
        var best: Category? = null
        for ((cat, n) in counts) {
            if (n.toDouble() / codes.size < 0.34 && cat.rank < Category.DRIZZLE.rank) continue
            best = when (val b = best) {
                null -> cat
                else -> when {
                    cat.rank > b.rank -> cat
                    cat.rank == b.rank && n > counts.getOrDefault(b, 0) -> cat
                    else -> b
                }
            }
        }
        if (best != null) return best
        return counts.maxByOrNull { it.value }?.key ?: Category.CLEAR
    }

    /** WHO exposure category for a UV index value. */
    fun uvLabel(context: Context, uv: Double): String = when {
        uv < 3 -> context.getString(R.string.uv_low)
        uv < 6 -> context.getString(R.string.uv_moderate)
        uv < 8 -> context.getString(R.string.uv_high)
        uv < 11 -> context.getString(R.string.uv_very_high)
        else -> context.getString(R.string.uv_extreme)
    }

    /** WHO band colour for a UV value (site's emerald→amber→orange→red→fuchsia). */
    fun uvColor(uv: Double): Long = when {
        uv < 3 -> 0xFF059669
        uv < 6 -> 0xFFD97706
        uv < 8 -> 0xFFEA580C
        uv < 11 -> 0xFFDC2626
        else -> 0xFFC026D3
    }.toLong()

    /** Moon phase name for a 0–1 fraction (0 and 1 = new moon). */
    fun moonPhaseName(context: Context, phase: Double): String {
        val p = ((phase % 1) + 1) % 1
        return when {
            p < 0.03 || p >= 0.97 -> context.getString(R.string.moon_new)
            p < 0.22 -> context.getString(R.string.moon_waxing_crescent)
            p < 0.28 -> context.getString(R.string.moon_first_quarter)
            p < 0.47 -> context.getString(R.string.moon_waxing_gibbous)
            p < 0.53 -> context.getString(R.string.moon_full)
            p < 0.72 -> context.getString(R.string.moon_waning_gibbous)
            p < 0.78 -> context.getString(R.string.moon_last_quarter)
            else -> context.getString(R.string.moon_waning_crescent)
        }
    }

    /** Illuminated fraction of the disc, 0 new → 1 full. */
    fun moonIllumination(phase: Double): Double {
        val p = ((phase % 1) + 1) % 1
        return (1 - kotlin.math.cos(2 * Math.PI * p)) / 2
    }
}
