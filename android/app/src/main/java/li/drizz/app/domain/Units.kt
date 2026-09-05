package li.drizz.app.domain

import li.drizz.app.data.settings.UnitPrefs
import li.drizz.app.data.settings.WindUnit

/** Unit plumbing shared by charts, tables and the narrative. */
object Units {

    fun tempUnit(prefs: UnitPrefs): String = prefs.temperature.symbol
    fun windUnit(prefs: UnitPrefs): String = prefs.windSpeed.label
    fun precipUnit(prefs: UnitPrefs): String = prefs.precipitation.label

    fun getWindUnit(prefs: UnitPrefs): WindUnit = prefs.windSpeed

    /** Localized 8-wind label for a direction in degrees. */
    fun windDirectionLabel(degrees: Double, res: (Int) -> String): String =
        res(windDirectionRes(degrees))

    fun windDirectionRes(degrees: Double): Int = when {
        degrees < 22.5 || degrees >= 337.5 -> li.drizz.app.R.string.wind_n
        degrees < 67.5 -> li.drizz.app.R.string.wind_ne
        degrees < 112.5 -> li.drizz.app.R.string.wind_e
        degrees < 157.5 -> li.drizz.app.R.string.wind_se
        degrees < 202.5 -> li.drizz.app.R.string.wind_s
        degrees < 247.5 -> li.drizz.app.R.string.wind_sw
        degrees < 292.5 -> li.drizz.app.R.string.wind_w
        else -> li.drizz.app.R.string.wind_nw
    }
}
