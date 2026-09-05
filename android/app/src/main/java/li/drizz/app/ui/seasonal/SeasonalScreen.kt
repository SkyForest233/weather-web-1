package li.drizz.app.ui.seasonal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import li.drizz.app.R
import li.drizz.app.charts.ChartBand
import li.drizz.app.charts.ChartConfig
import li.drizz.app.charts.ChartSeries
import li.drizz.app.charts.ChartWindow
import li.drizz.app.charts.ReferenceSeries
import li.drizz.app.charts.TimeChart
import li.drizz.app.data.WeatherRepository
import li.drizz.app.data.settings.Settings
import li.drizz.app.di.AppGraph
import li.drizz.app.domain.model.ClimateNormals
import li.drizz.app.domain.model.FriendlyError
import li.drizz.app.domain.model.SeasonalForecast
import li.drizz.app.data.humanizeError
import li.drizz.app.ui.components.ErrorPanel
import li.drizz.app.ui.components.LoadingPanel
import li.drizz.app.ui.components.SectionCard
import li.drizz.app.ui.components.SectionHeader
import li.drizz.app.ui.stringRes
import li.drizz.app.util.Dates
import li.drizz.app.util.Fmt

/**
 * Monthly seasonal outlook against the 1991–2020 climate normal — the port of
 * outlook.ts (buildMonthOutlooks) + the seasonal page.
 */
data class MonthOutlook(
    val key: String,
    val label: String,
    val days: Int,
    val partial: Boolean,
    val tMean: Double,
    val tMax: Double,
    val tMin: Double,
    val anomaly: Double?,
    val precip: Double,
    val precipNormal: Double?,
    val precipShare: Double?,
    val wetDays: Int,
    val warmerShare: Double
)

data class SeasonalUiState(
    val loading: Boolean = true,
    val error: FriendlyError? = null,
    val seasonal: SeasonalForecast? = null,
    val normals: ClimateNormals? = null,
    val normalsLoading: Boolean = false,
    val months: List<MonthOutlook> = emptyList()
)

class SeasonalViewModel(private val repo: WeatherRepository) : ViewModel() {
    private val _state = MutableStateFlow(SeasonalUiState())
    val state = _state.asStateFlow()
    private var signature: String? = null

    fun load(settings: Settings, force: Boolean = false) {
        val sig = listOf(settings.location.id, settings.units, settings.seasonalModel).toString()
        if (!force && sig == signature) return
        signature = sig
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val seasonal = repo.fetchSeasonal(settings.location, settings.units, settings.seasonalModel)
                _state.value = _state.value.copy(loading = false, seasonal = seasonal, months = buildOutlooks(seasonal, null, settings))
                // Normals arrive separately (a big archive request).
                _state.value = _state.value.copy(normalsLoading = true)
                try {
                    val normals = repo.fetchClimateNormals(settings.location, settings.units)
                    _state.value = _state.value.copy(
                        normals = normals,
                        normalsLoading = false,
                        months = buildOutlooks(seasonal, normals, settings)
                    )
                } catch (_: Throwable) {
                    _state.value = _state.value.copy(normalsLoading = false)
                }
            } catch (err: Throwable) {
                _state.value = _state.value.copy(loading = false, error = humanizeError(err))
            }
        }
    }

    private fun buildOutlooks(
        result: SeasonalForecast,
        normals: ClimateNormals?,
        settings: Settings
    ): List<MonthOutlook> {
        val tMeanVar = result.variables["temperature_2m_mean"] ?: return emptyList()
        val tMaxVar = result.variables["temperature_2m_max"]
        val tMinVar = result.variables["temperature_2m_min"]
        val precipVar = result.variables["precipitation_sum"]
        val wetThreshold = if (settings.units.precipitation.label == "mm") 1.0 else 0.04

        val groups = LinkedHashMap<String, MutableList<Int>>()
        val ordinalsOrNull = if (normals != null) {
            result.dateKeys.map { key ->
                normals.ordinal(key.substring(5, 7).toInt(), key.substring(8, 10).toInt())
            }
        } else null

        result.dateKeys.forEachIndexed { i, key ->
            groups.getOrPut(key.substring(0, 7)) { mutableListOf() }.add(i)
        }

        fun meanOf(xs: List<Double>): Double = if (xs.isEmpty()) Double.NaN else xs.average()

        return groups.map { (key, indices) ->
            val year = key.substring(0, 4).toInt()
            val month = key.substring(5, 7).toInt()
            val pick = { arr: List<Double?>? -> if (arr == null) Double.NaN else meanOf(indices.mapNotNull { arr.getOrNull(it) }.filter { it.isFinite() }) }

            val tMean = pick(tMeanVar.mean)
            var anomaly: Double? = null
            var precipNormal: Double? = null
            var precipShare: Double? = null
            if (normals != null && ordinalsOrNull != null) {
                val normalMean = meanOf(indices.mapNotNull { i -> normals.tmean.getOrNull(ordinalsOrNull[i])?.takeIf { it.isFinite() } })
                if (normalMean.isFinite() && tMean.isFinite()) anomaly = tMean - normalMean
                val normalPrecip = meanOf(indices.mapNotNull { i -> normals.precip.getOrNull(ordinalsOrNull[i])?.takeIf { it.isFinite() } })
                val precip = pick(precipVar?.mean)
                if (normalPrecip.isFinite() && normalPrecip > 0 && precip.isFinite()) {
                    precipNormal = normalPrecip
                    precipShare = precip / normalPrecip
                }
            }
            val precip = pick(precipVar?.mean)
            val wetDays = precipVar?.let { pv ->
                indices.count { i -> (pv.mean.getOrNull(i) ?: 0.0) > wetThreshold }
            } ?: 0

            // Share of members whose monthly mean sits above the normal.
            var warmerShare = 0.0
            if (normals != null && ordinalsOrNull != null && tMeanVar.members.isNotEmpty()) {
                val memberMeans = tMeanVar.members.map { m ->
                    meanOf(indices.mapNotNull { i -> m.getOrNull(i)?.takeIf { it.isFinite() } })
                }
                val normalMean = meanOf(indices.mapNotNull { i -> normals.tmean.getOrNull(ordinalsOrNull[i])?.takeIf { it.isFinite() } })
                if (normalMean.isFinite() && memberMeans.all { it.isFinite() }) {
                    warmerShare = memberMeans.count { it > normalMean }.toDouble() / memberMeans.size
                }
            }

            MonthOutlook(
                key = key,
                label = Dates.monthYearLabel(year, month),
                days = indices.size,
                partial = daysInMonth(year, month) != indices.size,
                tMean = tMean,
                tMax = pick(tMaxVar?.mean),
                tMin = pick(tMinVar?.mean),
                anomaly = anomaly,
                precip = precip,
                precipNormal = precipNormal,
                precipShare = precipShare,
                wetDays = wetDays,
                warmerShare = warmerShare
            )
        }
    }

    private fun daysInMonth(year: Int, month: Int): Int =
        java.time.YearMonth.of(year, month).lengthOfMonth()
}

@Composable
fun SeasonalScreen(settings: Settings) {
    val vm: SeasonalViewModel = viewModel { SeasonalViewModel(AppGraph.weather) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(settings.location, settings.units, settings.seasonalModel) { vm.load(settings) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            state.loading && state.seasonal == null -> LoadingPanel()
            state.error != null && state.seasonal == null -> ErrorPanel(state.error, onRetry = { vm.load(settings, force = true) })
            state.seasonal != null -> {
                val seasonal = state.seasonal
                val units = settings.units
                // Monthly outlook cards
                state.months.forEach { month ->
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(month.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${month.days} days" + if (month.partial) " · " + stringRes(R.string.seasonal_no_normal) else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            month.anomaly?.let { anomaly ->
                                val positive = anomaly >= 0
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (positive) MaterialTheme.colorScheme.errorContainer
                                            else MaterialTheme.colorScheme.primaryContainer
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        stringRes(R.string.anomaly_vs_normal, "%+.1f°".format(anomaly)),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (positive) MaterialTheme.colorScheme.onErrorContainer
                                        else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.padding(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column {
                                Text(Fmt.temp(month.tMax, units.temperature.symbol) + " / " + Fmt.temp(month.tMin, units.temperature.symbol), style = MaterialTheme.typography.bodyLarge)
                                Text(stringRes(R.string.var_temperature), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column {
                                Text(
                                    Fmt.precip(month.precip) + " " + units.precipitation.label +
                                        (month.precipShare?.let { " · ${Math.round(it * 100)}%" } ?: ""),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(stringRes(R.string.var_precipitation), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column {
                                Text("${month.wetDays}", style = MaterialTheme.typography.bodyLarge)
                                Text("Wet days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (month.warmerShare > 0) {
                                Column {
                                    Text("${Math.round(month.warmerShare * 100)}%", style = MaterialTheme.typography.bodyLarge)
                                    Text("Warmer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Ensemble spread charts
                val tMeanVar = seasonal.variables["temperature_2m_mean"]
                if (tMeanVar != null) {
                    SectionCard {
                        SectionHeader(stringRes(R.string.var_temperature))
                        val normal = state.normals
                        val normalSeries = if (normal != null) {
                            ReferenceSeries(
                                "normal", "normal",
                                seasonal.dateKeys.map { key ->
                                    val ord = normal.ordinal(key.substring(5, 7).toInt(), key.substring(8, 10).toInt())
                                    normal.tmean.getOrNull(ord)
                                },
                                MaterialTheme.colorScheme.tertiary
                            )
                        } else null
                        val window = ChartWindow(seasonal.time.first(), seasonal.time.last())
                        TimeChart(
                            config = ChartConfig(
                                timeSec = seasonal.time,
                                timezone = "UTC",
                                leftUnit = units.temperature.symbol
                            ),
                            series = listOf(
                                ChartSeries(
                                    id = "mean", label = "", values = tMeanVar.mean,
                                    color = MaterialTheme.colorScheme.primary, strokeWidthDp = 2.4f
                                )
                            ),
                            window = window,
                            bands = listOf(
                                ChartBand("p2575", tMeanVar.p25, tMeanVar.p75, MaterialTheme.colorScheme.primary),
                                ChartBand("minmax", tMeanVar.min, tMeanVar.max, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ),
                            references = listOfNotNull(normalSeries)
                        )
                        Text(
                            stringRes(R.string.normal_band_legend),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val precipVar = seasonal.variables["precipitation_sum"]
                if (precipVar != null) {
                    SectionCard {
                        SectionHeader(stringRes(R.string.var_precipitation))
                        val window = ChartWindow(seasonal.time.first(), seasonal.time.last())
                        TimeChart(
                            config = ChartConfig(timeSec = seasonal.time, timezone = "UTC", leftUnit = units.precipitation.label),
                            series = listOf(
                                ChartSeries(
                                    id = "mean", label = "", values = precipVar.mean,
                                    color = MaterialTheme.colorScheme.primary, strokeWidthDp = 2.4f
                                )
                            ),
                            window = window,
                            bands = listOf(
                                ChartBand("p2575", precipVar.p25, precipVar.p75, MaterialTheme.colorScheme.primary)
                            )
                        )
                    }
                }
                if (state.normalsLoading) {
                    Text(
                        stringRes(R.string.normals_loading_period),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.padding(bottom = 24.dp))
    }
}
