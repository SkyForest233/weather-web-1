package li.drizz.app.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import li.drizz.app.data.CitiesRepository
import li.drizz.app.data.WeatherRepository
import li.drizz.app.data.settings.Settings
import li.drizz.app.data.settings.SettingsRepository
import li.drizz.app.domain.ChartVariables
import li.drizz.app.domain.computeDayNightCodes
import li.drizz.app.domain.model.FriendlyError
import li.drizz.app.domain.model.NearbyCity
import li.drizz.app.domain.model.NearbyDaily
import li.drizz.app.domain.model.WeekForecast
import li.drizz.app.data.humanizeError
import li.drizz.app.util.Dates

data class NearbyEntry(val city: NearbyCity, val daily: NearbyDaily?)

data class WeekUiState(
    val loading: Boolean = true,
    val error: FriendlyError? = null,
    val forecast: WeekForecast? = null,
    val forecastDays: Int = 7,
    val pastDays: Int = 0,
    val selectedDayKey: String? = null,
    val dayNightCodes: List<Pair<Double?, Double?>> = emptyList(),
    val nearby: List<NearbyEntry> = emptyList(),
    val nearbyLoading: Boolean = false
)

/**
 * Loads the 7/15-day forecast and the nearby-cities snapshot, mirroring the
 * week page's load function. Refetches only when inputs actually change.
 */
class WeekViewModel(
    private val repo: WeatherRepository,
    private val cities: CitiesRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WeekUiState())
    val state = _state.asStateFlow()

    private var loadedSignature: String? = null

    fun load(settings: Settings, force: Boolean = false) {
        val vars = ChartVariables.neededHourlyApiVars(
            settings.tableVisible,
            settings.chartLayout.flatMap { it.variables }
        ) + "weather_code"
        val signature = listOf(
            settings.location.id, settings.location.latitude, settings.location.longitude,
            settings.model, settings.units, _state.value.forecastDays, _state.value.pastDays,
            vars.sorted()
        ).toString()
        if (!force && signature == loadedSignature) return
        loadedSignature = signature

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = _state.value.forecast == null, error = null)
            try {
                val forecast = repo.fetchWeek(
                    location = settings.location,
                    units = settings.units,
                    model = settings.model,
                    forecastDays = _state.value.forecastDays,
                    pastDays = _state.value.pastDays,
                    hourlyVars = vars.toList()
                )
                val selected = _state.value.selectedDayKey
                    ?.takeIf { key -> forecast.days.any { it.dateKey == key } }
                    ?: forecast.days.firstOrNull { it.dateKey == Dates.dayKey(System.currentTimeMillis() / 1000, forecast.timezone) }?.dateKey
                    ?: forecast.days.firstOrNull()?.dateKey

                val dayNight = computeDayNightCodes(
                    forecast.hourly.time,
                    forecast.hourly["weather_code"],
                    forecast.days.map { it.sunriseSec },
                    forecast.days.map { it.sunsetSec },
                    forecast.timezone
                )
                _state.value = _state.value.copy(
                    loading = false,
                    forecast = forecast,
                    selectedDayKey = selected,
                    dayNightCodes = dayNight,
                    error = null
                )
                if (settings.nearbyOpen) loadNearby(settings)
            } catch (err: Throwable) {
                _state.value = _state.value.copy(loading = false, error = humanizeError(err))
            }
        }
    }

    private fun loadNearby(settings: Settings) {
        val forecast = _state.value.forecast ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(nearbyLoading = true)
            try {
                val found = cities.findNearby(
                    settings.location.latitude, settings.location.longitude,
                    count = 10, population = settings.location.population ?: 0
                )
                val dailies = repo.fetchNearbyDaily(
                    found.map { it.latitude to it.longitude }, settings.units
                )
                _state.value = _state.value.copy(
                    nearby = found.zip(dailies) { city, daily -> NearbyEntry(city, daily) },
                    nearbyLoading = false
                )
            } catch (err: Throwable) {
                // The nearby panel is auxiliary; a failure just empties it.
                _state.value = _state.value.copy(nearby = emptyList(), nearbyLoading = false)
            }
        }
    }

    fun selectDay(dateKey: String) {
        _state.value = _state.value.copy(selectedDayKey = dateKey)
    }

    /** Extend the strip to the full 15-day horizon. */
    fun extendTo15(settings: Settings) {
        if (_state.value.forecastDays >= 15) return
        _state.value = _state.value.copy(forecastDays = 15)
        loadedSignature = null
        load(settings, force = true)
    }

    /** Load the past three days as well. */
    fun loadPast(settings: Settings) {
        if (_state.value.pastDays >= 3) return
        _state.value = _state.value.copy(pastDays = 3)
        loadedSignature = null
        load(settings, force = true)
    }

    fun retry(settings: Settings) = load(settings, force = true)

    /** Back to the model that always has data (the ModelSelector escape hatch). */
    fun resetToBestMatch(settings: Settings) {
        viewModelScope.launch {
            settingsRepo.setModel("best_match")
            loadedSignature = null
        }
    }

    /** Jump to a city inside the chosen regional model's domain. */
    fun jumpToCity(settings: Settings, slug: String, label: String) {
        viewModelScope.launch {
            try {
                val hit = repo.geocodeSearch(label.replace('-', ' '), count = 1).firstOrNull()
                    ?: return@launch
                settingsRepo.setLocation(hit)
                loadedSignature = null
            } catch (_: Throwable) {
            }
        }
    }
}
