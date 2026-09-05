package li.drizz.app.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import li.drizz.app.charts.ChartConfig
import li.drizz.app.charts.ChartSeries
import li.drizz.app.charts.ChartWindow
import li.drizz.app.charts.SeriesKind
import li.drizz.app.charts.TimeChart
import li.drizz.app.data.settings.ChartPanel
import li.drizz.app.data.settings.ChartRangePref
import li.drizz.app.data.settings.Settings
import li.drizz.app.data.settings.SettingsRepository
import li.drizz.app.data.settings.UnitPrefs
import li.drizz.app.di.AppGraph
import li.drizz.app.domain.ChartVariableDef
import li.drizz.app.domain.ChartVariables
import li.drizz.app.domain.UnitKind
import li.drizz.app.domain.model.HourlySeries
import li.drizz.app.domain.model.WeekForecast
import li.drizz.app.ui.components.LegendDot
import li.drizz.app.ui.components.SectionCard
import li.drizz.app.ui.components.SectionHeader
import li.drizz.app.ui.components.WeatherGlyphIcon
import li.drizz.app.ui.stringRes
import li.drizz.app.util.Dates
import li.drizz.app.util.Fmt
import li.drizz.app.util.cardinalDirection
import kotlinx.coroutines.launch

// ─── Hourly table ───────────────────────────────────────────────────────────

private class TableRowDef(
    val key: String,
    val labelRes: Int,
    val shortRes: Int,
    val unit: (UnitPrefs) -> String
)

private val TABLE_ROWS = listOf(
    TableRowDef("icons", li.drizz.app.R.string.var_icons, li.drizz.app.R.string.var_icons_short, { "" }),
    TableRowDef("temperature", li.drizz.app.R.string.var_temperature, li.drizz.app.R.string.var_temperature_short, { it.temperature.symbol }),
    TableRowDef("feels", li.drizz.app.R.string.var_apparent, li.drizz.app.R.string.var_apparent_short, { it.temperature.symbol }),
    TableRowDef("dew_point", li.drizz.app.R.string.var_dew_point, li.drizz.app.R.string.var_dew_point_short, { it.temperature.symbol }),
    TableRowDef("wind", li.drizz.app.R.string.var_wind, li.drizz.app.R.string.var_wind_short, { it.windSpeed.label }),
    TableRowDef("gusts", li.drizz.app.R.string.var_gusts, li.drizz.app.R.string.var_gusts_short, { it.windSpeed.label }),
    TableRowDef("humidity", li.drizz.app.R.string.var_humidity, li.drizz.app.R.string.var_humidity_short, { "%" }),
    TableRowDef("clouds", li.drizz.app.R.string.var_cloud, li.drizz.app.R.string.var_cloud_short, { "%" }),
    TableRowDef("pressure", li.drizz.app.R.string.var_pressure, li.drizz.app.R.string.var_pressure_short, { "hPa" }),
    TableRowDef("uv", li.drizz.app.R.string.var_uv, li.drizz.app.R.string.var_uv_short, { "" }),
    TableRowDef("visibility", li.drizz.app.R.string.var_visibility, li.drizz.app.R.string.var_visibility_short, { "km" }),
    TableRowDef("precipitation", li.drizz.app.R.string.var_precipitation, li.drizz.app.R.string.var_precipitation_short, { it.precipitation.label }),
    TableRowDef("snowfall", li.drizz.app.R.string.var_snowfall, li.drizz.app.R.string.var_snowfall_short, { "cm" })
)

private val ROW_HEIGHT = 30.dp

/** The transposed hourly table: row labels fixed left, hours scroll right. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HourlyTableSection(state: WeekUiState, settings: Settings) {
    val forecast = state.forecast ?: return
    var rowsSheet by rememberSaveable { mutableStateOf(false) }

    SectionCard {
        SectionHeader(stringRes(li.drizz.app.R.string.hourly_heading)) {
            val scope = rememberCoroutineScope()
            Row(verticalAlignment = Alignment.CenterVertically) {
                SingleChoiceSegmentedButtonRow {
                    listOf(1, 3).forEachIndexed { index, hours ->
                        SegmentedButton(
                            selected = settings.hourlyInterval == hours,
                            onClick = { scope.launch { AppGraph.settings.setHourlyInterval(hours) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                        ) { Text("${hours}h") }
                    }
                }
                IconButton(onClick = { rowsSheet = true }) {
                    Icon(Icons.Filled.Tune, contentDescription = stringRes(li.drizz.app.R.string.hourly_variables))
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        val step = settings.hourlyInterval
        val hourIndices = forecast.hourly.time.indices.filter { it % step == 0 }
        val nowSec = System.currentTimeMillis() / 1000
        val visibleRows = settings.tableRows.filter { it in settings.tableVisible }
        val defsByKey = TABLE_ROWS.associateBy { it.key }

        Row {
            // Fixed label column
            Column(Modifier.width(96.dp)) {
                visibleRows.forEach { key ->
                    val def = defsByKey[key]
                    Box(Modifier.height(ROW_HEIGHT), contentAlignment = Alignment.CenterStart) {
                        if (def != null) {
                            Column {
                                Text(
                                    stringResource(def.shortRes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                val unit = def.unit(settings.units)
                                if (unit.isNotEmpty()) {
                                    Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            // Hour columns
            LazyRow {
                items(hourIndices.size) { idx ->
                    val i = hourIndices[idx]
                    val t = forecast.hourly.time[i]
                    val isNow = nowSec / 3600 == t / 3600
                    Column(
                        Modifier
                            .width(52.dp)
                            .background(
                                if (isNow) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                else androidx.compose.ui.graphics.Color.Transparent
                            )
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header: hour
                        Box(Modifier.height(22.dp), contentAlignment = Alignment.Center) {
                            Text(
                                Dates.clock(t, forecast.timezone).removeSuffix(":00") + "h",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        visibleRows.forEach { key ->
                            Box(Modifier.height(ROW_HEIGHT), contentAlignment = Alignment.Center) {
                                TableCell(forecast, key, i, state, settings)
                            }
                        }
                    }
                }
            }
        }
    }

    if (rowsSheet) {
        TableRowsSheet(settings = settings, onDismiss = { rowsSheet = false })
    }
}

@Composable
private fun TableCell(forecast: WeekForecast, key: String, i: Int, state: WeekUiState, settings: Settings) {
    val h = forecast.hourly
    val code = h["weather_code"].getOrNull(i)
    when (key) {
        "icons" -> {
            val dayIdx = forecast.days.indexOfFirst { it.dateKey == Dates.dayKey(h.time[i], forecast.timezone) }
            val sun = forecast.days.getOrNull(dayIdx)
            val daytime = if (sun != null && sun.sunriseSec in 1 until sun.sunsetSec) {
                h.time[i] in sun.sunriseSec..sun.sunsetSec
            } else {
                val hour = Dates.hourOf(h.time[i], forecast.timezone)
                hour in 6..17
            }
            WeatherGlyphIcon(code, daytime, 22.dp)
        }
        "temperature" -> CellText(Fmt.temp(h["temperature_2m"].getOrNull(i), settings.units.temperature.symbol))
        "feels" -> CellText(Fmt.temp(h["apparent_temperature"].getOrNull(i), settings.units.temperature.symbol))
        "dew_point" -> CellText(Fmt.temp(h["dew_point_2m"].getOrNull(i), settings.units.temperature.symbol))
        "wind" -> Row(verticalAlignment = Alignment.CenterVertically) {
            CellText(Fmt.wind(h["wind_speed_10m"].getOrNull(i), settings.units.windSpeed.label))
            val dir = h["wind_direction_10m"].getOrNull(i)
            if (dir != null && dir.isFinite()) {
                Spacer(Modifier.width(2.dp))
                li.drizz.app.ui.components.MiniWindArrow(dir)
            }
        }
        "gusts" -> CellText(Fmt.wind(h["wind_gusts_10m"].getOrNull(i), settings.units.windSpeed.label))
        "humidity" -> CellText(Fmt.percent(h["relative_humidity_2m"].getOrNull(i)))
        "clouds" -> CellText(Fmt.percent(h["cloud_cover"].getOrNull(i)))
        "pressure" -> CellText(Fmt.num0(h["pressure_msl"].getOrNull(i)))
        "uv" -> CellText(Fmt.num1(h["uv_index"].getOrNull(i)))
        "visibility" -> CellText(Fmt.num0(h["visibility"].getOrNull(i)?.let { it / 1000 }))
        "precipitation" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CellText(Fmt.precip(h["precipitation"].getOrNull(i)))
            val prob = h["precipitation_probability"].getOrNull(i)
            if (prob != null && prob.isFinite() && prob >= 5) {
                Text(
                    Fmt.percent(prob),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        "snowfall" -> CellText(Fmt.num1(h["snowfall"].getOrNull(i)))
    }
}

@Composable
private fun CellText(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
}

/** Row visibility + ordering sheet for the hourly table. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableRowsSheet(settings: Settings, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(stringRes(li.drizz.app.R.string.table_customize), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(settings.tableRows.size) { index ->
                    val key = settings.tableRows[index]
                    val def = TABLE_ROWS.firstOrNull { it.key == key } ?: return@items
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(def.labelRes), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(
                            enabled = index > 0,
                            onClick = {
                                val new = settings.tableRows.toMutableList().also { it[index] = it[index - 1]; it[index - 1] = key }
                                scope.launch { AppGraph.settings.setTableOrder(new) }
                            }
                        ) { Icon(Icons.Filled.ArrowUpward, contentDescription = "up") }
                        IconButton(
                            enabled = index < settings.tableRows.size - 1,
                            onClick = {
                                val new = settings.tableRows.toMutableList().also { it[index] = it[index + 1]; it[index + 1] = key }
                                scope.launch { AppGraph.settings.setTableOrder(new) }
                            }
                        ) { Icon(Icons.Filled.ArrowDownward, contentDescription = "down") }
                        Switch(
                            checked = key in settings.tableVisible,
                            onCheckedChange = { on ->
                                val new = settings.tableVisible.toMutableSet()
                                if (on) new += key else new -= key
                                scope.launch { AppGraph.settings.setTableVisible(new) }
                            }
                        )
                    }
                }
            }
        }
    }
}

// ─── Meteograms ─────────────────────────────────────────────────────────────

/** Builds the chart config + declarative series for a set of registry defs. */
fun buildMeteogram(
    defs: List<ChartVariableDef>,
    forecast: WeekForecast,
    settings: Settings
): Pair<ChartConfig, List<ChartSeries>> {
    val h = forecast.hourly
    val time = h.time

    fun daytimeList(): List<Boolean> {
        val dayKeys = forecast.days.associate { it.dateKey to it }
        return time.map { t ->
            val day = dayKeys[Dates.dayKey(t, forecast.timezone)]
            if (day != null && day.sunriseSec in 1 until day.sunsetSec) t in day.sunriseSec..day.sunsetSec
            else Dates.hourOf(t, forecast.timezone) in 6..17
        }
    }

    val series = defs.mapNotNull { def ->
        val values = h[def.api]
        if (values.isEmpty() && !def.marker) return@mapNotNull null
        val color = androidx.compose.ui.graphics.Color(def.color)
        when {
            def.pictograms -> ChartSeries(
                id = def.key, label = "", values = values, color = color,
                kind = SeriesKind.PICTOGRAMS, daytime = daytimeList()
            )
            def.windArrows -> ChartSeries(
                id = def.key, label = "", values = values, color = color,
                kind = SeriesKind.WIND_ARROWS, arrowDirections = values
            )
            def.cloudBand -> ChartSeries(
                id = def.key, label = "", values = values, color = color,
                kind = SeriesKind.CLOUD_BAND, cloudLayerRank = when (def.cloudLayer) {
                    "low" -> 1; "mid" -> 2; "high" -> 3; else -> 0
                }
            )
            def.isLine -> ChartSeries(
                id = def.key, label = "", values = values, color = color,
                kind = SeriesKind.LINE, strokeWidthDp = def.width / 1.6f, dashed = def.dashed,
                colorScale = def.colorScale, gradientFill = def.gradientFill, fill = def.fill,
                fillOpacity = def.fillOpacity, outline = def.outline, extrema = def.extrema,
                scaleBy = def.scaleBy, rightAxis = def.rightAxis,
                rightPreset = if (def.rightAxis) 0.0..100.0 else null
            )
            else -> ChartSeries(
                id = def.key, label = "", values = values, color = color,
                kind = SeriesKind.BAR, scaleBy = def.scaleBy
            )
        }
    }

    val axisKind = defs.firstOrNull { !it.marker }?.kind ?: UnitKind.TEMP
    val config = ChartConfig(
        timeSec = time,
        timezone = forecast.timezone,
        daylightBands = forecast.daylightBands,
        leftUnit = ChartVariables.unitForKind(axisKind, settings.units),
        rightUnit = if (defs.any { it.rightAxis }) "%" else "",
        nowSec = System.currentTimeMillis() / 1000,
        tempInFahrenheit = settings.units.temperature == li.drizz.app.data.settings.TempUnit.FAHRENHEIT
    )
    return config to series
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeteogramSection(state: WeekUiState, settings: Settings) {
    val forecast = state.forecast ?: return
    var layoutSheet by rememberSaveable { mutableStateOf(false) }
    var rangePref by rememberSaveable { mutableStateOf(settings.chartRange) }
    var resetTick by remember { mutableIntStateOf(0) }
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600

    fun initialWindow(time: List<Long>, timezone: String): ChartWindow {
        val domainStart = time.firstOrNull() ?: return ChartWindow(0, 1)
        val domainEnd = time.lastOrNull() ?: return ChartWindow(0, 1)
        val zone = Dates.zone(timezone)
        val todayMidnight = java.time.LocalDate.now(zone).atStartOfDay(zone).toEpochSecond()
        val now = System.currentTimeMillis() / 1000
        val day = 86400L
        return when (rangePref) {
            ChartRangePref.ALL -> ChartWindow(domainStart, domainEnd, domainStart, domainEnd)
            ChartRangePref.TODAY -> ChartWindow(domainStart, domainEnd, todayMidnight, todayMidnight + day)
            ChartRangePref.D3 -> ChartWindow(domainStart, domainEnd, (now - day / 12).coerceAtLeast(domainStart), (now - day / 12 + 3 * day).coerceAtMost(domainEnd))
            ChartRangePref.D5 -> ChartWindow(domainStart, domainEnd, (now - day / 12).coerceAtLeast(domainStart), (now - day / 12 + 5 * day).coerceAtMost(domainEnd))
            ChartRangePref.AUTO ->
                if (compact) ChartWindow(domainStart, domainEnd, (now - day / 12).coerceAtLeast(domainStart), (now - day / 12 + 3 * day).coerceAtMost(domainEnd))
                else ChartWindow(domainStart, domainEnd, domainStart, domainEnd)
        }
    }

    SectionHeader(stringRes(li.drizz.app.R.string.meteograms_heading)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RangeButtons(rangePref, allowAuto = false) { rangePref = it; resetTick++ }
            IconButton(onClick = { layoutSheet = true }) {
                Icon(Icons.Filled.Tune, contentDescription = stringRes(li.drizz.app.R.string.meteograms_customize))
            }
        }
    }

    settings.chartLayout.forEach { panel ->
        val defs = panel.variables.mapNotNull { ChartVariables.BY_KEY[it] }
        if (defs.isEmpty()) return@forEach
        val (config, series) = remember(forecast, panel, settings.units) {
            buildMeteogram(defs, forecast, settings)
        }
        val window = remember(forecast, panel, rangePref, resetTick) {
            initialWindow(config.timeSec, config.timezone)
        }
        SectionCard {
            // Panel legend: variable names, tappable to open the customizer.
            Row(
                Modifier.fillMaxWidth().clickable { layoutSheet = true }.padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                defs.forEach { def ->
                    if (def.marker) return@forEach
                    LegendDot(androidx.compose.ui.graphics.Color(def.color), stringResource(def.shortRes))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    ChartVariables.unitForKind(defs.firstOrNull { !it.marker }?.kind ?: UnitKind.TEMP, settings.units),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TimeChart(
                config = config,
                series = series,
                window = window,
                onDoubleTapReset = { resetTick++ }
            )
            Text(
                stringRes(li.drizz.app.R.string.meteograms_zoom_hint) + " · " +
                    stringRes(li.drizz.app.R.string.reset_zoom),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (layoutSheet) {
        ChartLayoutSheet(settings = settings, onDismiss = { layoutSheet = false })
    }
}

@Composable
fun RangeButtons(current: ChartRangePref, allowAuto: Boolean, onSelect: (ChartRangePref) -> Unit) {
    val options = if (allowAuto) {
        listOf(ChartRangePref.AUTO, ChartRangePref.TODAY, ChartRangePref.D3, ChartRangePref.D5, ChartRangePref.ALL)
    } else {
        listOf(ChartRangePref.TODAY, ChartRangePref.D3, ChartRangePref.D5, ChartRangePref.ALL)
    }
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = current == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = {
                    Text(
                        when (option) {
                            ChartRangePref.AUTO -> stringRes(li.drizz.app.R.string.default_range_auto)
                            ChartRangePref.TODAY -> stringRes(li.drizz.app.R.string.range_today)
                            ChartRangePref.D3 -> stringRes(li.drizz.app.R.string.range_3_days)
                            ChartRangePref.D5 -> stringRes(li.drizz.app.R.string.range_5_days)
                            ChartRangePref.ALL -> stringRes(li.drizz.app.R.string.range_all)
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}

/** Meteogram layout editor: panels + their variables, persisted globally. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartLayoutSheet(settings: Settings, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()

    suspend fun persist(layout: List<ChartPanel>) = AppGraph.settings.setChartLayout(layout)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringRes(li.drizz.app.R.string.meteograms_customize), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    scope.launch { persist(SettingsRepository.DEFAULT_CHART_LAYOUT) }
                }) { Text(stringRes(li.drizz.app.R.string.reset_zoom)) }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(settings.chartLayout.size) { panelIndex ->
                    val panel = settings.chartLayout[panelIndex]
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("#${panelIndex + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.weight(1f))
                            IconButton(enabled = settings.chartLayout.size > 1, onClick = {
                                scope.launch { persist(settings.chartLayout.filterIndexed { i, _ -> i != panelIndex }) }
                            }) { Icon(Icons.Filled.Close, contentDescription = "remove panel") }
                        }
                        // variables in this panel
                        panel.variables.forEach { varKey ->
                            val def = ChartVariables.BY_KEY[varKey] ?: return@forEach
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LegendDot(androidx.compose.ui.graphics.Color(def.color), stringResource(def.labelRes), Modifier.weight(1f))
                                // move to other panels
                                settings.chartLayout.forEachIndexed { targetIdx, target ->
                                    if (target.id != panel.id) {
                                        TextButton(onClick = {
                                            val newLayout = settings.chartLayout.map { p ->
                                                if (p.id == panel.id) p.copy(variables = p.variables - varKey)
                                                else if (p.id == target.id) p.copy(variables = p.variables + varKey)
                                                else p
                                            }.filter { it.variables.isNotEmpty() }
                                            scope.launch { persist(newLayout) }
                                        }) { Text("${targetIdx + 1}", style = MaterialTheme.typography.labelMedium) }
                                    }
                                }
                                IconButton(onClick = {
                                    val newLayout = settings.chartLayout.map { p ->
                                        if (p.id == panel.id) p.copy(variables = p.variables - varKey) else p
                                    }.filter { it.variables.isNotEmpty() }
                                    scope.launch { persist(newLayout) }
                                }) { Icon(Icons.Filled.Close, contentDescription = "remove") }
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    }
                }
                item {
                    Text(stringRes(li.drizz.app.R.string.hourly_variables), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    val used = settings.chartLayout.flatMap { it.variables }.toSet()
                    val available = ChartVariables.ALL.filter { it.key !in used }
                    FlowChips(
                        labels = available.map { stringResource(it.labelRes) },
                        onSelected = { index ->
                            val def = available[index]
                            val target = settings.chartLayout.lastOrNull()?.id ?: return@FlowChips
                            val newLayout = settings.chartLayout.map { p ->
                                if (p.id == target) p.copy(variables = p.variables + def.key) else p
                            }
                            scope.launch { persist(newLayout) }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        scope.launch {
                            persist(settings.chartLayout + ChartPanel("panel-${System.currentTimeMillis()}", listOf("temperature")))
                        }
                    }) { Text("+ " + stringRes(li.drizz.app.R.string.meteograms_heading)) }
                }
            }
        }
    }
}

/** Simple wrapping chip row (avoids the FlowRow experimental dependency). */
@Composable
fun FlowChips(labels: List<String>, onSelected: (Int) -> Unit) {
    val rows = labels.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var offset = 0
        rows.forEach { rowLabels ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowLabels.forEachIndexed { i, label ->
                    val realIndex = offset + i
                    FilterChip(
                        selected = false,
                        onClick = { onSelected(realIndex) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            offset += rowLabels.size
        }
    }
}
