package li.drizz.app.domain

import li.drizz.app.data.settings.PrecipUnit
import li.drizz.app.data.settings.TempUnit
import li.drizz.app.data.settings.WindUnit

/**
 * "Is this day worth flagging?" — the meteoalarm-flavoured thresholds from
 * significance.ts. A day gets a warning triangle at WARN and a severe one at
 * SEVERE as soon as any of gusts / precipitation / heat / cold crosses its bar.
 */
enum class WarnLevel { NONE, WARN, SEVERE }
enum class WarnCause { GUST, PRECIP, HEAT, COLD }

data class DayWarning(val level: WarnLevel, val causes: List<WarnCause>)

object Warnings {

    fun sunIsSignificant(sunshineSeconds: Double?, daylightSeconds: Double?): Boolean {
        if (daylightSeconds == null || daylightSeconds <= 0) return false
        return (sunshineSeconds ?: 0.0) / daylightSeconds >= 0.1
    }

    fun precipIsSignificant(sum: Double?, unit: PrecipUnit): Boolean {
        val min = if (unit == PrecipUnit.MM) 0.1 else 0.005
        return (sum ?: 0.0) >= min
    }

    /** Sunshine as a share of daylight, 0–100. */
    fun sunshinePercent(sunshineSeconds: Double?, daylightSeconds: Double?): Int {
        if (sunshineSeconds == null || daylightSeconds == null || daylightSeconds <= 0) return 0
        return (minOf(100.0, sunshineSeconds / daylightSeconds * 100)).toInt()
    }

    fun getDayWarning(
        tempMax: Double?, tempMin: Double?, precipSum: Double?, gust: Double?,
        temperatureUnit: TempUnit, precipitationUnit: PrecipUnit, windUnit: WindUnit
    ): DayWarning {
        val celsius = temperatureUnit == TempUnit.CELSIUS
        val mm = precipitationUnit == PrecipUnit.MM

        val (gustWarn, gustSevere) = when (windUnit) {
            WindUnit.MS -> 22.0 to 31.0
            WindUnit.MPH -> 50.0 to 68.0
            WindUnit.KN -> 43.0 to 59.0
            WindUnit.KMH -> 80.0 to 110.0
        }
        val precipWarn = if (mm) 30.0 else 1.2
        val precipSevere = if (mm) 60.0 else 2.4
        val heatWarn = if (celsius) 40.0 else 104.0
        val heatSevere = if (celsius) 45.0 else 113.0
        val coldWarn = if (celsius) -20.0 else -4.0
        val coldSevere = if (celsius) -30.0 else -22.0

        val g = gust ?: -Double.MAX_VALUE
        val p = precipSum ?: -Double.MAX_VALUE
        val tx = tempMax ?: -Double.MAX_VALUE
        val tn = tempMin ?: Double.MAX_VALUE

        val causes = mutableListOf<WarnCause>()
        var severe = false
        if (g >= gustSevere) { causes += WarnCause.GUST; severe = true } else if (g >= gustWarn) causes += WarnCause.GUST
        if (p >= precipSevere) { causes += WarnCause.PRECIP; severe = true } else if (p >= precipWarn) causes += WarnCause.PRECIP
        if (tx >= heatSevere) { causes += WarnCause.HEAT; severe = true } else if (tx >= heatWarn) causes += WarnCause.HEAT
        if (tn <= coldSevere) { causes += WarnCause.COLD; severe = true } else if (tn <= coldWarn) causes += WarnCause.COLD

        return if (causes.isEmpty()) DayWarning(WarnLevel.NONE, emptyList())
        else DayWarning(if (severe) WarnLevel.SEVERE else WarnLevel.WARN, causes)
    }
}
