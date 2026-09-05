package li.drizz.app.di

import android.content.Context
import li.drizz.app.data.CitiesRepository
import li.drizz.app.data.WeatherRepository
import li.drizz.app.data.remote.OpenMeteoApi
import li.drizz.app.data.settings.SettingsRepository

/**
 * Tiny hand-rolled dependency graph — everything the app needs is created once
 * here and reached from composables via `AppGraph.get(context)`.
 */
object AppGraph {

    private lateinit var appContext: Context

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val api: OpenMeteoApi by lazy { OpenMeteoApi(appContext) }
    val weather: WeatherRepository by lazy { WeatherRepository(api) }
    val cities: CitiesRepository by lazy { CitiesRepository(appContext) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun context(): Context = appContext
}
