package li.drizz.app.ui.compare

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import li.drizz.app.R
import li.drizz.app.charts.ChartBand
import li.drizz.app.charts.ChartConfig
import li.drizz.app.charts.ChartSeries
import li.drizz.app.charts.ChartWindow
import li.drizz.app.charts.ReferenceSeries
import li.drizz.app.charts.SeriesKind
import li.drizz.app.charts.TimeChart
import li.drizz.app.data.WeatherRepository
import li.drizz.app.data.settings.Settings
import li.drizz.app.di.AppGraph
import li.drizz.app.domain.ModelCatalog
import li.drizz.app.domain.model.CompareForecast
import li.drizz.app.domain.model.FriendlyError
import li.drizz.app.domain.model.ModelSeries
import li.drizz.app.data.humanizeError
import li.drizz.app.ui.components.ErrorPanel
import li.drizz.app.ui.components.LegendDot
import li.drizz.app.ui.components.LoadingPanel
import li.drizz.app.ui.components.SectionCard
import li.drizz.app.ui.components.SectionHeader
import li.drizz.app.ui.stringRes
import li.drizz.app.util.cardinalDirection

/** One plottable comparison variable. */
private data class CompareVar(
    val api: String,
    val labelRes: Int,
    val isWindDirection: Boolean = false,
    val isWeatherCode: Boolean = false,
    val isPrecipitation: Boolean = false,
    val unit: String = ""
)

private val COMPARE_VARS = listOf(
    CompareVar("temperature_2m", R.string.var_temperature, unit = "°"),
    CompareVar("precipitation", R.string.var_precipitation, isPrecipitation = true, unit = "mm"),
    CompareVar("wind_speed_10m", R.string.var_wind, unit = "km/h"),
    CompareVar("wind_gusts_10m", R.string.var_gusts, unit = "km/h"),
    CompareVar("relative_humidity_2m", R.string.var_humidity, unit = "%"),
    CompareVar("pressure_msl", R.string.var_pressure, unit = "hPa"),
    CompareVar("cloud_cover", R.string.var_cloud, unit = "%"),
    CompareVar("wind_direction_10m", R.string.var_wind_dir, isWindDirection = true),
    CompareVar("weather_code", R.string.var_icons, isWeatherCode = true)
)

data class CompareUiState(
    val loading: Boolean = true,
    val error: FriendlyError? = null,
    val data: CompareForecast? = null,
    val variables: List<String> = listOf("temperature_2m", "precipitation", "wind_speed_10m")
)

class CompareViewModel(private val repo: WeatherRepository) : ViewModel() {
    private val _state = MutableStateFlow(CompareUiState())
    val state = _state.asStateFlow()
    private var signature: String? = null

    fun setVariables(vars: List<String>) {
        _state.value = _state.value.copy(variables = vars)
        signature = null
    }

    fun load(settings: Settings, models: List<String>, force: Boolean = false) {
        if (models.isEmpty()) {
            _state.value = _state.value.copy(loading = false, data = null, error = null)
            return
        }
        val sig = listOf(settings.location.id, settings.model, settings.units, models, _state.value.variables).toString()
        if (!force && sig == signature) return
        signature = sig
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val vars = _state.value.variables
                val data = repo.fetchComparison(settings.location, settings.units, models, vars)
                _state.value = _state.value.copy(loading = false, data = data, error = null)
            } catch (err: Throwable) {
                _state.value = _state.value.copy(loading = false, error = humanizeError(err))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(settings: Settings) {
    val vm: CompareViewModel = viewModel { CompareViewModel(AppGraph.weather) }
    val state by vm.state.collectAsStateWithLifecycle()

    // Pending model selection applies on "Apply", like the website's panel.
    var appliedModels by rememberSaveable { mutableStateOf(listOf("icon_seamless", "gfs_seamless")) }
    var pendingModels by rememberSaveable { mutableStateOf(appliedModels) }
    var modelsSheet by rememberSaveable { mutableStateOf(false) }
    var showMean by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(settings.location, settings.units, appliedModels, state.variables) {
        vm.load(settings, appliedModels)
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(stringRes(R.string.compare_models_heading)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${appliedModels.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = { pendingModels = appliedModels; modelsSheet = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringRes(R.string.compare_edit_models))
                }
            }
        }
        Text(
            stringRes(R.string.compare_models_choose),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Variable chips
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                COMPARE_VARS.chunked(4).forEach { rowVars ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowVars.forEach { v ->
                            FilterChip(
                                selected = v.api in state.variables,
                                onClick = {
                                    val next = if (v.api in state.variables) state.variables - v.api else state.variables + v.api
                                    vm.setVariables(next)
                                },
                                label = { Text(stringResource(v.labelRes), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        when {
            state.loading && state.data == null -> LoadingPanel()
            state.error != null && state.data == null -> ErrorPanel(state.error, onRetry = { vm.load(settings, appliedModels, force = true) })
            state.data != null -> {
                val data = state.data
                val config0 = ChartConfig(
                    timeSec = data.time,
                    timezone = data.timezone,
                    daylightBands = data.daylightBands,
                    nowSec = System.currentTimeMillis() / 1000
                )
                COMPARE_VARS.filter { it.api in state.variables }.forEach { cv ->
                    val seriesDefs = data.models.mapNotNull { ms ->
                        val values = ms.variables[cv.api] ?: return@mapNotNull null
                        ChartSeries(
                            id = ms.modelId,
                            label = ModelCatalog.label(ms.modelId),
                            values = values,
                            color = androidx.compose.ui.graphics.Color(ModelCatalog.modelColor(ms.modelId, appliedModels)),
                            kind = if (cv.isWeatherCode) SeriesKind.PICTOGRAMS else SeriesKind.LINE,
                            strokeWidthDp = 2f,
                            daytime = if (cv.isWeatherCode) data.time.map { true } else null
                        )
                    }
                    if (seriesDefs.isEmpty()) return@forEach

                    val references = mutableListOf<ReferenceSeries>()
                    val bands = mutableListOf<ChartBand>()
                    if (cv.isPrecipitation) {
                        // Precipitation agreement: median line + min/max spread.
                        val points = precipitationAgreement(data.models, cv.api, data.time.size, wetThreshold = 0.1)
                        val median = points.map { it?.median }
                        val min = points.map { it?.min }
                        val max = points.map { it?.max }
                        bands += ChartBand("agree", min, max, MaterialTheme.colorScheme.primary)
                        references += ReferenceSeries("median", "median", median, MaterialTheme.colorScheme.primary)
                    } else if (showMean && !cv.isWeatherCode && !cv.isWindDirection) {
                        val mean = modelMean(data.models, cv.api, data.time.size)
                        references += ReferenceSeries("mean", stringRes(R.string.compare_model_mean), mean, MaterialTheme.colorScheme.onSurface)
                    }

                    val window = rememberWindow(data.time, cv.api)
                    SectionCard {
                        SectionHeader(stringResource(cv.labelRes)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (cv.isPrecipitation) {
                                    Text(
                                        stringRes(R.string.compare_precipitation_agreement),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!cv.isWeatherCode && !cv.isWindDirection && !cv.isPrecipitation) {
                                    Text(stringRes(R.string.compare_model_mean), style = MaterialTheme.typography.labelSmall)
                                    Switch(checked = showMean, onCheckedChange = { showMean = it })
                                }
                            }
                        }
                        if (cv.isWindDirection) {
                            // Direction values are angles; plot arrows per model row instead.
                            Text(
                                stringRes(R.string.compare_direction_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            data.models.forEach { ms ->
                                val dirs = ms.variables[cv.api] ?: return@forEach
                                Text(
                                    ModelCatalog.label(ms.modelId) + ": " +
                                        (dirs.lastOrNull { it != null && it.isFinite() }
                                            ?.let { cardinalDirection(it) } ?: "–"),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            TimeChart(
                                config = config0.copy(leftUnit = cv.unit),
                                series = seriesDefs,
                                window = window,
                                bands = bands,
                                references = references
                            )
                        }
                        // Legend
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            data.models.forEach { ms ->
                                LegendDot(
                                    androidx.compose.ui.graphics.Color(ModelCatalog.modelColor(ms.modelId, appliedModels)),
                                    ModelCatalog.label(ms.modelId)
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.padding(bottom = 24.dp))
    }

    if (modelsSheet) {
        ModelMultiSelectSheet(
            applied = pendingModels,
            onToggle = { id -> pendingModels = if (id in pendingModels) pendingModels - id else pendingModels + id },
            onApply = { appliedModels = pendingModels; modelsSheet = false },
            onDismiss = { modelsSheet = false }
        )
    }
}

@Composable
private fun rememberWindow(time: List<Long>, key: String): ChartWindow {
    val start = time.firstOrNull() ?: 0L
    val end = time.lastOrNull() ?: 1L
    return androidx.compose.runtime.remember(key, start, end) { ChartWindow(start, end) }
}

// ─── Comparison math (comparison.ts) ────────────────────────────────────────

private class AgreementPoint(
    val wetCount: Int,
    val median: Double?,
    val min: Double?,
    val max: Double?
)

/** Per-timestamp precipitation occurrence agreement and amount spread. */
private fun precipitationAgreement(
    models: List<ModelSeries>,
    variable: String,
    timeLength: Int,
    wetThreshold: Double
): List<AgreementPoint?> {
    return List(timeLength) { index ->
        val values = models.mapNotNull { it.variables[variable]?.getOrNull(index) }
            .filter { it.isFinite() && it >= 0 }
        if (values.isEmpty()) return@List null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        val median = if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
        AgreementPoint(
            wetCount = values.count { it > wetThreshold },
            median = median,
            min = sorted.first(),
            max = sorted.last()
        )
    }
}

/** Arithmetic model mean for scalar variables. */
private fun modelMean(models: List<ModelSeries>, variable: String, timeLength: Int): List<Double?> {
    return List(timeLength) { i ->
        val vals = models.mapNotNull { it.variables[variable]?.getOrNull(i) }.filter { it.isFinite() }
        if (vals.isEmpty()) null else Math.round(vals.average() * 10.0) / 10.0
    }
}

/** Grouped multi-model picker with apply/discard semantics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelMultiSelectSheet(
    applied: List<String>,
    onToggle: (String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(stringRes(R.string.compare_models_choose), style = MaterialTheme.typography.titleMedium)
            if (applied.size >= 6) {
                Text(
                    stringRes(R.string.compare_many_models_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(Modifier.padding(4.dp))
            LazyColumnMod(applied, onToggle)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringRes(R.string.compare_discard_changes)) }
                TextButton(onClick = onApply) { Text(stringRes(R.string.compare_apply_selection)) }
            }
        }
    }
}

@Composable
private fun LazyColumnMod(selected: List<String>, onToggle: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn {
        ModelCatalog.forecastGroups.forEach { group ->
            item(key = "g_${group.value}") {
                Text(
                    group.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }
            items(group.models.size, key = { group.models[it].value }) { i ->
                val model = group.models[i]
                val isSel = model.value in selected
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onToggle(model.value) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(selected = isSel, onClick = { onToggle(model.value) })
                    Column(Modifier.weight(1f)) {
                        Text(model.label, style = MaterialTheme.typography.bodyLarge)
                        val meta = listOfNotNull(model.resolution, model.update).joinToString(" · ")
                        if (meta.isNotEmpty()) {
                            Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

