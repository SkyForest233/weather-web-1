package li.drizz.app.ui.ensemble

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
import li.drizz.app.charts.TimeChart
import li.drizz.app.data.WeatherRepository
import li.drizz.app.data.settings.Settings
import li.drizz.app.di.AppGraph
import li.drizz.app.domain.ModelCatalog
import li.drizz.app.domain.model.EnsembleForecast
import li.drizz.app.domain.model.FriendlyError
import li.drizz.app.data.humanizeError
import li.drizz.app.ui.components.ErrorPanel
import li.drizz.app.ui.components.LegendDot
import li.drizz.app.ui.components.LoadingPanel
import li.drizz.app.ui.components.SectionCard
import li.drizz.app.ui.components.SectionHeader
import li.drizz.app.ui.stringRes

private data class EnsembleVar(val api: String, val labelRes: Int, val unit: String, val isBar: Boolean)

private val ENSEMBLE_VARS = listOf(
    EnsembleVar("temperature_2m", R.string.var_temperature, "°", false),
    EnsembleVar("precipitation", R.string.var_precipitation, "mm", true),
    EnsembleVar("wind_speed_10m", R.string.var_wind, "km/h", false)
)

data class EnsembleUiState(
    val loading: Boolean = true,
    val error: FriendlyError? = null,
    val data: EnsembleForecast? = null
)

class EnsembleViewModel(private val repo: WeatherRepository) : ViewModel() {
    private val _state = MutableStateFlow(EnsembleUiState())
    val state = _state.asStateFlow()
    private var signature: String? = null

    fun load(settings: Settings, model: String, force: Boolean = false) {
        val sig = listOf(settings.location.id, settings.units, model).toString()
        if (!force && sig == signature) return
        signature = sig
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val data = repo.fetchEnsemble(settings.location, settings.units, model)
                _state.value = _state.value.copy(loading = false, data = data, error = null)
            } catch (err: Throwable) {
                _state.value = _state.value.copy(loading = false, error = humanizeError(err))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnsembleScreen(settings: Settings) {
    val vm: EnsembleViewModel = viewModel { EnsembleViewModel(AppGraph.weather) }
    val state by vm.state.collectAsStateWithLifecycle()
    var model by rememberSaveable { mutableStateOf(settings.ensembleModel) }
    var modelSheet by rememberSaveable { mutableStateOf(false) }
    var showMembers by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settings.location, settings.units, model) { vm.load(settings, model) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(stringRes(R.string.model_ensemble)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ModelCatalog.ensembleLabel(model),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 4.dp)
                )
                IconButton(onClick = { modelSheet = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringRes(R.string.model_ensemble))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringRes(R.string.legend_show), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.padding(2.dp))
            Switch(checked = showMembers, onCheckedChange = { showMembers = it })
        }

        when {
            state.loading && state.data == null -> LoadingPanel()
            state.error != null && state.data == null -> ErrorPanel(state.error, onRetry = { vm.load(settings, model, force = true) })
            state.data != null -> {
                val data = state.data
                ENSEMBLE_VARS.forEach { ev ->
                    val variable = data.variables[ev.api] ?: return@forEach
                    val config = ChartConfig(
                        timeSec = data.time,
                        timezone = data.timezone,
                        daylightBands = data.daylightBands,
                        leftUnit = ev.unit,
                        nowSec = System.currentTimeMillis() / 1000
                    )
                    val memberSeries = if (showMembers) {
                        variable.members.mapIndexed { mi, values ->
                            ChartSeries(
                                id = "m$mi",
                                label = "",
                                values = values,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                strokeWidthDp = 1f
                            )
                        }
                    } else emptyList()
                    val avgSeries = ChartSeries(
                        id = "avg", label = "", values = variable.average,
                        color = MaterialTheme.colorScheme.primary, strokeWidthDp = 3.2f
                    )
                    val window = androidx.compose.runtime.remember(data, ev.api) {
                        ChartWindow(data.time.first(), data.time.last())
                    }
                    SectionCard {
                        SectionHeader(stringResource(ev.labelRes)) {
                            Text(
                                "${variable.members.size} members",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TimeChart(
                            config = config,
                            series = memberSeries + avgSeries,
                            window = window,
                            bands = listOf(
                                ChartBand("minmax", variable.min, variable.max, MaterialTheme.colorScheme.primary)
                            ),
                            references = listOf(
                                ReferenceSeries("avg", "", variable.average, MaterialTheme.colorScheme.primary)
                            )
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LegendDot(MaterialTheme.colorScheme.primary, stringRes(R.string.compare_model_mean))
                            if (showMembers) LegendDot(MaterialTheme.colorScheme.onSurfaceVariant, stringRes(R.string.legend_members))
                        }
                    }
                }
                Text(
                    stringRes(R.string.ensemble_trimmed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.padding(bottom = 24.dp))
    }

    if (modelSheet) {
        ModalBottomSheet(onDismissRequest = { modelSheet = false }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text(stringRes(R.string.model_ensemble), style = MaterialTheme.typography.titleMedium)
                androidx.compose.foundation.lazy.LazyColumn {
                    ModelCatalog.ensembleGroups.forEach { group ->
                        item(key = "g_${group.value}") {
                            Text(
                                group.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(group.models.size, key = { group.models[it].value }) { i ->
                            val m = group.models[i]
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { model = m.value; modelSheet = false }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(selected = model == m.value, onClick = { model = m.value; modelSheet = false })
                                Column {
                                    Text(m.label, style = MaterialTheme.typography.bodyLarge)
                                    val meta = listOfNotNull(m.resolution, m.update).joinToString(" · ")
                                    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = {
                    model = "ncep_gefs_seamless"; modelSheet = false
                }) { Text("GFS Ensemble Seamless") }
            }
        }
    }
    // Persist the chosen ensemble model
    LaunchedEffect(model) { AppGraph.settings.setEnsembleModel(model) }
}
