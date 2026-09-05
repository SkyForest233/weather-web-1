package li.drizz.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import li.drizz.app.domain.model.DEFAULT_LOCATION
import li.drizz.app.domain.model.GeoLocation

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "drizz_settings")

private val json = Json { ignoreUnknownKeys = true }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class TempUnit(val api: String, val label: String) {
    CELSIUS("celsius", "°C"), FAHRENHEIT("fahrenheit", "°F");

    val symbol: String get() = label
}

enum class WindUnit(val api: String, val label: String) {
    KMH("kmh", "km/h"), MS("ms", "m/s"), MPH("mph", "mph"), KN("kn", "kn")
}

enum class PrecipUnit(val api: String, val label: String) {
    MM("mm", "mm"), INCH("inch", "in")
}

data class UnitPrefs(
    val temperature: TempUnit = TempUnit.CELSIUS,
    val windSpeed: WindUnit = WindUnit.KMH,
    val precipitation: PrecipUnit = PrecipUnit.MM
)

@Serializable
data class ChartPanel(
    val id: String,
    val variables: List<String>
)

/** Which hourly-table rows are visible, and in which order. */
val DEFAULT_TABLE_ROWS: List<String> = listOf(
    "icons", "temperature", "feels", "dew_point", "wind", "gusts", "humidity",
    "clouds", "pressure", "uv", "visibility", "precipitation", "snowfall"
)

val DEFAULT_TABLE_ON: Set<String> =
    setOf("icons", "temperature", "feels", "wind", "humidity", "clouds", "precipitation")

val DEFAULT_CHART_LAYOUT: List<ChartPanel> = listOf(
    ChartPanel("panel-1", listOf("temperature", "weather_icons")),
    ChartPanel("panel-2", listOf("precipitation", "precipitation_probability", "cloud_cover")),
    ChartPanel("panel-3", listOf("wind", "wind_gusts", "wind_direction"))
)

/** Meteogram time range preference, ported from storedChartRange. */
enum class ChartRangePref(val key: String) { AUTO("auto"), TODAY("today"), D3("3d"), D5("5d"), ALL("all") }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val units: UnitPrefs = UnitPrefs(),
    val location: GeoLocation = DEFAULT_LOCATION,
    val locationKnown: Boolean = false,
    val model: String = "best_match",
    val ensembleModel: String = "ncep_gefs_seamless",
    val archiveModel: String = "best_match",
    val seasonalModel: String = "best_match",
    val tableRows: List<String> = DEFAULT_TABLE_ROWS,
    val tableVisible: Set<String> = DEFAULT_TABLE_ON,
    val hourlyInterval: Int = 3,
    val chartLayout: List<ChartPanel> = DEFAULT_CHART_LAYOUT,
    val chartRange: ChartRangePref = ChartRangePref.AUTO,
    val nearbyOpen: Boolean = true,
    val recents: List<GeoLocation> = emptyList(),
    val favorites: List<GeoLocation> = emptyList()
)

/**
 * Persisted user preferences, the port of src/lib/stores/settings.ts.
 * Complex values (location lists, chart layout) are stored as JSON strings.
 */
class SettingsRepository(private val context: Context) {

    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val tempUnit = stringPreferencesKey("temperature_unit")
        val windUnit = stringPreferencesKey("wind_speed_unit")
        val precipUnit = stringPreferencesKey("precipitation_unit")
        val location = stringPreferencesKey("stored_location")
        val locationKnown = booleanPreferencesKey("location_known")
        val model = stringPreferencesKey("selected_model")
        val ensembleModel = stringPreferencesKey("ensemble_model")
        val archiveModel = stringPreferencesKey("archive_model")
        val seasonalModel = stringPreferencesKey("seasonal_model")
        val tableVisible = stringPreferencesKey("table_visible")
        val tableOrder = stringPreferencesKey("table_row_order_v1")
        val hourlyInterval = intPreferencesKey("hourly_interval")
        val chartLayout = stringPreferencesKey("chart_layout_v1")
        val chartRange = stringPreferencesKey("chart_range_v1")
        val nearbyOpen = booleanPreferencesKey("nearby_open_v1")
        val recents = stringPreferencesKey("recent_locations_v1")
        val favorites = stringPreferencesKey("favorite_locations_v1")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[K.dynamic] ?: true,
            units = UnitPrefs(
                temperature = p[K.tempUnit]?.let { runCatching { TempUnit.valueOf(it.uppercase()) }.getOrNull() } ?: TempUnit.CELSIUS,
                windSpeed = p[K.windUnit]?.let { runCatching { WindUnit.valueOf(it.uppercase()) }.getOrNull() } ?: WindUnit.KMH,
                precipitation = p[K.precipUnit]?.let { runCatching { PrecipUnit.valueOf(it.uppercase()) }.getOrNull() } ?: PrecipUnit.MM
            ),
            location = p[K.location]?.let { runCatching { json.decodeFromString<GeoLocation>(it) }.getOrNull() } ?: DEFAULT_LOCATION,
            locationKnown = p[K.locationKnown] ?: false,
            model = p[K.model] ?: "best_match",
            ensembleModel = p[K.ensembleModel] ?: "ncep_gefs_seamless",
            archiveModel = p[K.archiveModel] ?: "best_match",
            seasonalModel = p[K.seasonalModel] ?: "best_match",
            tableRows = mergeRowOrder(
                p[K.tableOrder]?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() } ?: DEFAULT_TABLE_ROWS
            ),
            tableVisible = p[K.tableVisible]?.let { runCatching { json.decodeFromString<List<String>>(it).toSet() }.getOrNull() } ?: DEFAULT_TABLE_ON,
            hourlyInterval = if (p[K.hourlyInterval] == 1) 1 else 3,
            chartLayout = p[K.chartLayout]?.let { runCatching { json.decodeFromString<List<ChartPanel>>(it) }.getOrNull() } ?: DEFAULT_CHART_LAYOUT,
            chartRange = p[K.chartRange]?.let { v -> ChartRangePref.entries.firstOrNull { it.key == v } } ?: ChartRangePref.AUTO,
            nearbyOpen = p[K.nearbyOpen] ?: true,
            recents = decodeLocations(p[K.recents]),
            favorites = decodeLocations(p[K.favorites])
        )
    }

    suspend fun setTheme(mode: ThemeMode) = edit { it[K.theme] = mode.name }
    suspend fun setDynamicColor(on: Boolean) = edit { it[K.dynamic] = on }
    suspend fun setUnits(units: UnitPrefs) = edit {
        it[K.tempUnit] = units.temperature.name.lowercase()
        it[K.windUnit] = units.windSpeed.name.lowercase()
        it[K.precipUnit] = units.precipitation.name.lowercase()
    }

    suspend fun setLocation(location: GeoLocation) = edit {
        it[K.location] = json.encodeToString(location)
        it[K.locationKnown] = true
    }

    suspend fun setModel(model: String) = edit { it[K.model] = model }
    suspend fun setEnsembleModel(model: String) = edit { it[K.ensembleModel] = model }
    suspend fun setArchiveModel(model: String) = edit { it[K.archiveModel] = model }
    suspend fun setSeasonalModel(model: String) = edit { it[K.seasonalModel] = model }

    suspend fun setTableVisible(visible: Set<String>) = edit { it[K.tableVisible] = json.encodeToString(visible.toList()) }
    suspend fun setTableOrder(order: List<String>) = edit { it[K.tableOrder] = json.encodeToString(order) }
    suspend fun setHourlyInterval(hours: Int) = edit { it[K.hourlyInterval] = if (hours == 1) 1 else 3 }
    suspend fun setChartLayout(layout: List<ChartPanel>) = edit { it[K.chartLayout] = json.encodeToString(layout) }
    suspend fun setChartRange(range: ChartRangePref) = edit { it[K.chartRange] = range.key }
    suspend fun setNearbyOpen(open: Boolean) = edit { it[K.nearbyOpen] = open }

    suspend fun addRecent(location: GeoLocation) = edit {
        val list = decodeLocations(it[K.recents])
        val key = locationKey(location)
        it[K.recents] = json.encodeToString((listOf(location) + list.filter { l -> locationKey(l) != key }).take(8))
    }

    suspend fun toggleFavorite(location: GeoLocation) = edit {
        val list = decodeLocations(it[K.favorites])
        val key = locationKey(location)
        it[K.favorites] = if (list.any { l -> locationKey(l) == key }) {
            json.encodeToString(list.filter { l -> locationKey(l) != key })
        } else {
            json.encodeToString((listOf(location) + list).take(24))
        }
    }

    suspend fun removeRecent(location: GeoLocation) = edit {
        val key = locationKey(location)
        it[K.recents] = json.encodeToString(decodeLocations(it[K.recents]).filter { l -> locationKey(l) != key })
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    companion object {
        /** Stable de-dup key: geocoding id, or rounded coordinates. */
        fun locationKey(l: GeoLocation): String =
            if (l.id != 0L) "id:${l.id}" else "c:%.3f,%.3f".format(java.util.Locale.ROOT, l.latitude, l.longitude)

        /** Survives app updates: drop unknown keys, append new rows at the end. */
        fun mergeRowOrder(stored: List<String>): List<String> =
            stored.filter { it in DEFAULT_TABLE_ROWS } + DEFAULT_TABLE_ROWS.filter { it !in stored }

        private fun decodeLocations(raw: String?): List<GeoLocation> =
            raw?.let { runCatching { json.decodeFromString<List<GeoLocation>>(it) }.getOrNull() } ?: emptyList()
    }
}
