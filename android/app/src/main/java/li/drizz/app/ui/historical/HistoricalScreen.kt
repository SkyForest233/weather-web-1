package li.drizz.app.ui.historical

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import li.drizz.app.charts.ChartConfig
import li.drizz.app.charts.ChartSeries
import li.drizz.app.charts.ChartWindow
import li.drizz.app.charts.ReferenceSeries
import li.drizz.app.charts.TimeChart
import li.drizz.app.data.WeatherRepository
import li.drizz.app.data.settings.Settings
import li.drizz.app.di.AppGraph
import li.drizz.app.domain.ModelCatalog
import li.drizz.app.domain.model.ClimateNormals
import li.drizz.app.domain.model.FriendlyError
import li.drizz.app.domain.model.HistoricalForecast
import li.drizz.app.data.humanizeError
import li.drizz.app.ui.components.LoadingPanel
import li.drizz.app.ui.components.SectionCard
import li.drizz.app.ui.components.SectionHeader
import li.drizz.app.ui.components.StatRow
import li.drizz.app.ui.stringRes
import li.drizz.app.util.Fmt
import java.time.LocalDate

data class HistoricalUiState(
    val loading: Boolean = false,
    val error: FriendlyError? = null,
    val data: HistoricalForecast? = null,
    val normals: ClimateNormals? = null,
    val startDate: LocalDate = LocalDate.now().minusYears(1).minusDays(7),
    val endDate: LocalDate = LocalDate.now().minusYears(1),
    val model: String = "best_match",
    val selectedDayIndex: Int? = null
)

class HistoricalViewModel(private val repo: WeatherRepository) : ViewModel() {
    private val _state = MutableStateFlow(HistoricalUiState())
    val state = _state.asStateFlow()
    private var signature: String? = null

    fun setRange(start: LocalDate, end: LocalDate) {
        _state.value = _state.value.copy(startDate = start, endDate = end, selectedDayIndex = null)
        signature = null
    }

    fun setModel(model: String) {
        _state.value = _state.value.copy(model = model)
        signature = null
    }

    fun selectDay(index: Int?) {
        _state.value = _state.value.copy(selectedDayIndex = index)
    }

    fun load(settings: Settings, force: Boolean = false) {
        val s = _state.value
        val sig = listOf(settings.location.id, settings.units, s.model, s.startDate, s.endDate).toString()
        if (!force && sig == signature) return
        signature = sig
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val data = repo.fetchHistorical(
                    settings.location, settings.units,
                    s.startDate.toString(), s.endDate.toString(), s.model
                )
                _state.value = _state.value.copy(loading = false, data = data, error = null)
            } catch (err: Throwable) {
                _state.value = _state.value.copy(loading = false, error = humanizeError(err))
            }
        }
        // Normals overlay (independent; failures are non-fatal)
        if (_state.value.normals == null) {
            viewModelScope.launch {
                try {
                    val normals = repo.fetchClimateNormals(settings.location, settings.units)
                    _state.value = _state.value.copy(normals = normals)
                } catch (_: Throwable) {
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricalScreen(settings: Settings) {
    val vm: HistoricalViewModel = viewModel { HistoricalViewModel(AppGraph.weather) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(settings.location, settings.units, state.startDate, state.endDate, state.model) {
        vm.load(settings)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringRes(R.string.strip_history_title),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Date range + quick ranges
        SectionCard {
            SectionHeader(stringRes(R.string.historical_quick_ranges)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { vm.setRange(LocalDate.now().minusDays(30), LocalDate.now().minusDays(5)) }) {
                        Text(stringRes(R.string.historical_last_days, 30))
                    }
                    TextButton(onClick = { vm.setRange(LocalDate.now().minusYears(1).withDayOfMonth(1), LocalDate.now().minusYears(1)) }) {
                        Text(stringRes(R.string.historical_month_last_year))
                    }
                }
            }
            Spacer(Modifier.padding(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(label = stringRes(R.string.historical_from), date = state.startDate) { d ->
                    if (d < state.endDate) vm.setRange(d, state.endDate)
                }
                DateField(label = stringRes(R.string.historical_to), date = state.endDate) { d ->
                    if (d > state.startDate) vm.setRange(state.startDate, d)
                }
            }
            Spacer(Modifier.padding(4.dp))
            ArchiveModelField(state.model) { vm.setModel(it) }
        }

        when {
            state.loading && state.data == null -> LoadingPanel(label = stringRes(R.string.normals_loading))
            state.error != null && state.data == null -> ErrorPanelRow(state) { vm.load(settings, force = true) }
            state.data != null -> {
                val data = state.data
                val units = settings.units

                // Daily mean temperature vs the climate normal
                val normal = state.normals
                SectionCard {
                    SectionHeader(stringRes(R.string.var_temperature))
                    val window = ChartWindow(data.hourly.time.firstOrNull() ?: 0, data.hourly.time.lastOrNull() ?: 1)
                    TimeChart(
                        config = ChartConfig(
                            timeSec = data.hourly.time,
                            timezone = data.timezone,
                            daylightBands = data.daylightBands,
                            leftUnit = units.temperature.symbol,
                            tempInFahrenheit = units.temperature == li.drizz.app.data.settings.TempUnit.FAHRENHEIT
                        ),
                        series = listOf(
                            ChartSeries(
                                id = "temp", label = "", values = data.hourly["temperature_2m"],
                                color = androidx.compose.ui.graphics.Color(0xFFEF6C00), strokeWidthDp = 3.4f,
                                colorScale = true, gradientFill = true, extrema = true
                            )
                        ),
                        window = window
                    )
                }

                // Daily stats list for the selected range
                SectionCard {
                    SectionHeader(stringRes(R.string.historical_daily_heading))
                    Text(
                        stringRes(R.string.historical_daily_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.padding(4.dp))
                    data.days.take(62).forEachIndexed { i, day ->
                        val selected = state.selectedDayIndex == i
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        ) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    "${day.dateKey}  " + Fmt.temp(day.tempMax, units.temperature.symbol) + " / " +
                                        Fmt.temp(day.tempMin, units.temperature.symbol) +
                                        "  ·  " + Fmt.precip(day.precipitationSum) + " " + units.precipitation.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { vm.selectDay(if (selected) null else i) }) {
                                    Text(if (selected) stringRes(R.string.range_selected_day) else "▸")
                                }
                            }
                            if (selected) {
                                StatRow(stringRes(R.string.var_temperature) + " mean", Fmt.temp(day.tempMean, units.temperature.symbol))
                                StatRow(stringRes(R.string.var_rain), Fmt.precip(day.rainSum) + " " + units.precipitation.label)
                                StatRow(stringRes(R.string.var_snowfall), Fmt.num1(day.snowfallSum) + " cm")
                                StatRow(stringRes(R.string.var_precipitation) + " h", Fmt.num0(day.precipitationHours))
                                StatRow(stringRes(R.string.var_wind), Fmt.wind(day.windSpeedMax, units.windSpeed.label) + " " + units.windSpeed.label)
                                StatRow(stringRes(R.string.var_gusts), Fmt.wind(day.windGustsMax, units.windSpeed.label) + " " + units.windSpeed.label)
                            }
                        }
                    }
                    if (data.days.size > 62) {
                        Text(
                            stringRes(R.string.historical_full_range),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (normal != null) {
                    Text(
                        stringRes(R.string.normal_band_legend),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun ErrorPanelRow(state: HistoricalUiState, retry: () -> Unit) {
    li.drizz.app.ui.components.ErrorPanel(error = state.error, onRetry = retry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, date: LocalDate, onSet: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val pickerState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = date.toEpochDay() * 86_400_000L,
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = LocalDate.ofEpochDay(utcTimeMillis / 86_400_000L)
                // ERA5 lags a few days; the archive starts in 1940.
                return !d.isAfter(LocalDate.now().minusDays(5)) && d.year >= 1940
            }
        }
    )
    OutlinedTextField(
        value = date.toString(),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier.weight(1f),
        trailingIcon = {
            TextButton(onClick = { open = true }) { Text("…") }
        }
    )
    if (open) {
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        onSet(LocalDate.ofEpochDay(ms / 86_400_000L))
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(androidx.compose.ui.res.stringResource(android.R.string.cancel)) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveModelField(current: String, onSet: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = ModelCatalog.archiveLabel(current),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringRes(R.string.model_archive)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        androidx.compose.material3.ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ModelCatalog.archiveGroups.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.label, style = MaterialTheme.typography.labelSmall) },
                    enabled = false,
                    onClick = {}
                )
                group.models.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m.label + (m.resolution?.let { "  ·  $it" } ?: "")) },
                        onClick = { onSet(m.value); expanded = false }
                    )
                }
            }
        }
    }
}
