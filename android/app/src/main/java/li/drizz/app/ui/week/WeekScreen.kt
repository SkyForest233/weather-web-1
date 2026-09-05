package li.drizz.app.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.drizz.app.R
import li.drizz.app.data.settings.Settings
import li.drizz.app.di.AppGraph
import li.drizz.app.domain.Narrative
import li.drizz.app.domain.Units
import li.drizz.app.domain.Warnings
import li.drizz.app.domain.WarnLevel
import li.drizz.app.domain.model.DayForecast
import li.drizz.app.domain.weatherDescriptionRes
import li.drizz.app.ui.components.ErrorPanel
import li.drizz.app.ui.components.LoadingPanel
import li.drizz.app.ui.components.SectionCard
import li.drizz.app.ui.components.SectionHeader
import li.drizz.app.ui.components.StatRow
import li.drizz.app.ui.components.WeatherGlyphIcon
import li.drizz.app.ui.stringRes
import li.drizz.app.util.Dates
import li.drizz.app.util.Fmt
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(settings: Settings) {
    val vm: WeekViewModel = viewModel {
        WeekViewModel(AppGraph.weather, AppGraph.cities, AppGraph.settings)
    }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(
        settings.location, settings.units, settings.model,
        settings.tableVisible, settings.chartLayout, settings.nearbyOpen
    ) {
        vm.load(settings)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val forecast = state.forecast
        when {
            state.loading && forecast == null -> LoadingPanel(label = stringRes(R.string.page_week_subtitle))
            state.error != null && forecast == null -> {
                ErrorPanel(
                    error = state.error,
                    onRetry = { vm.retry(settings) },
                    extraAction = if (settings.model != "best_match") {
                        stringRes(R.string.no_data_best_match) to { vm.resetToBestMatch(settings) }
                    } else null
                )
            }
            forecast != null -> {
                if (state.error != null) {
                    ErrorPanel(error = state.error, onRetry = { vm.retry(settings) })
                }
                HeroCard(state, settings)
                DayStrip(state, settings, onSelect = vm::selectDay, onExtend = { vm.extendTo15(settings) }, onLoadPast = { vm.loadPast(settings) })
                state.selectedDayKey?.let { key ->
                    DaySummaryCard(forecast, key, settings)
                }
                HourlyTableSection(state, settings)
                MeteogramSection(state, settings)
                if (state.nearby.isNotEmpty()) {
                    NearbySection(state, settings)
                }
                if (state.error != null && settings.model != "best_match") {
                    val city = li.drizz.app.domain.ModelCatalog.inDomainCity(settings.model)
                    SectionCard {
                        Text(stringRes(R.string.no_data_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringRes(R.string.no_data_body, settings.location.name),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { vm.resetToBestMatch(settings) }) {
                                Text(stringRes(R.string.no_data_best_match))
                            }
                            if (city != null) {
                                TextButton(onClick = { vm.jumpToCity(settings, city.first, city.second) }) {
                                    Text(stringRes(R.string.no_data_try_city, city.second))
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Current-conditions hero: big temperature, glyph, description, hi/lo. */
@Composable
private fun HeroCard(state: WeekUiState, settings: Settings) {
    val forecast = state.forecast ?: return
    val now = System.currentTimeMillis() / 1000
    val zone = Dates.zone(forecast.timezone)
    val todayKey = Dates.dayKey(now, forecast.timezone)
    val todayIndex = forecast.days.indexOfFirst { it.dateKey == todayKey }.coerceAtLeast(0)

    // Nearest hourly sample to "now"
    val nowIndex = forecast.hourly.time.indices.firstOrNull { forecast.hourly.time[it] >= now }
        ?: forecast.hourly.time.lastIndex
    val currentTemp = forecast.hourly["temperature_2m"].getOrNull(nowIndex)
    val currentCode = forecast.hourly["weather_code"].getOrNull(nowIndex)
    val sunrise = forecast.days.getOrNull(todayIndex)?.sunriseSec ?: 0
    val sunset = forecast.days.getOrNull(todayIndex)?.sunsetSec ?: 0
    val daytime = sunrise in 1 until sunset && now in sunrise..sunset

    val feels = forecast.hourly["apparent_temperature"].getOrNull(nowIndex)

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WeatherGlyphIcon(currentCode, daytime, size = 72.dp)
            Column {
                Text(
                    Fmt.temp(currentTemp, Units.tempUnit(settings.units)),
                    style = MaterialTheme.typography.displayMedium
                )
                Text(
                    stringResource(weatherDescriptionRes(currentCode)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val parts = mutableListOf<String>()
                feels?.let { parts += stringResource(R.string.var_apparent_short) + " " + Fmt.temp(it, Units.tempUnit(settings.units)) }
                val day = forecast.days.getOrNull(todayIndex)
                if (day != null) {
                    parts += "↑ " + Fmt.temp(day.tempMax, Units.tempUnit(settings.units)) +
                        "  ↓ " + Fmt.temp(day.tempMin, Units.tempUnit(settings.units))
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Horizontal day cards with warning badges and load-more affordances. */
@Composable
private fun DayStrip(
    state: WeekUiState,
    settings: Settings,
    onSelect: (String) -> Unit,
    onExtend: () -> Unit,
    onLoadPast: () -> Unit
) {
    val forecast = state.forecast ?: return
    val listState = rememberLazyListState()

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
    ) {
        if (state.pastDays == 0) {
            item {
                PastFutureChip(
                    icon = { Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = null) },
                    label = "3 " + stringRes(R.string.strip_past_label),
                    onClick = onLoadPast
                )
            }
        }
        items(state.forecastDays.coerceAtMost(forecast.days.size)) { i ->
            val day = forecast.days[i]
            val dayNight = state.dayNightCodes.getOrNull(i)
            val selected = day.dateKey == state.selectedDayKey
            DayCard(
                day = day,
                dayCode = dayNight?.first ?: day.weatherCode,
                settings = settings,
                timezone = forecast.timezone,
                selected = selected,
                onClick = { onSelect(day.dateKey) }
            )
        }
        if (state.forecastDays < 15) {
            item {
                PastFutureChip(
                    icon = { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null) },
                    label = "15 " + stringRes(R.string.strip_days_label),
                    onClick = onExtend
                )
            }
        }
    }
}

@Composable
private fun PastFutureChip(icon: @Composable () -> Unit, label: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            Modifier.padding(12.dp).width(56.dp).height(150.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            icon()
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DayCard(
    day: DayForecast,
    dayCode: Double?,
    settings: Settings,
    timezone: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val date = LocalDate.parse(day.dateKey)
    val rel = Dates.relativeDay(date, timezone)
    val dayLabel = when (rel) {
        Dates.RelDay.YESTERDAY -> stringRes(R.string.day_yesterday)
        Dates.RelDay.TODAY -> stringRes(R.string.day_today)
        Dates.RelDay.TOMORROW -> stringRes(R.string.day_tomorrow)
        else -> Dates.weekday(date)
    }
    val warning = Warnings.getDayWarning(
        day.tempMax, day.tempMin, day.precipitationSum, day.windGustsMax,
        settings.units.temperature, settings.units.precipitation, settings.units.windSpeed
    )
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = container),
        shape = MaterialTheme.shapes.medium,
        modifier = if (selected) Modifier.border(1.dp, MaterialTheme.colorScheme.secondary, MaterialTheme.shapes.medium) else Modifier
    ) {
        Column(
            Modifier.padding(12.dp).width(84.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dayLabel, style = MaterialTheme.typography.labelLarge)
                if (warning.level != WarnLevel.NONE) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (warning.level == WarnLevel.SEVERE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text("${date.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WeatherGlyphIcon(dayCode, daytime = true, size = 34.dp)
            Text(
                Fmt.temp(day.tempMax, Units.tempUnit(settings.units)) + " / " +
                    Fmt.temp(day.tempMin, Units.tempUnit(settings.units)),
                style = MaterialTheme.typography.bodyMedium
            )
            if (Warnings.precipIsSignificant(day.precipitationSum, settings.units.precipitation)) {
                Text(
                    "💧 " + Fmt.precip(day.precipitationSum) + " " + Units.precipUnit(settings.units),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (day.windGustsMax != null && day.windGustsMax > 0) {
                Text(
                    "🌬 " + Fmt.wind(day.windSpeedMax, Units.windUnit(settings.units)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The "in words" summary plus sun/moon/UV facts for the selected day. */
@Composable
fun DaySummaryCard(forecast: li.drizz.app.domain.model.WeekForecast, dayKey: String, settings: Settings) {
    val day = forecast.days.firstOrNull { it.dateKey == dayKey } ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val sentences = remember(forecast, dayKey, settings.units) {
        Narrative.buildDayNarrative(context, forecast.hourly, forecast.days, forecast.timezone, dayKey, settings.units)
    }
    val rel = Dates.relativeDay(LocalDate.parse(dayKey), forecast.timezone)
    val dayLabel = when (rel) {
        Dates.RelDay.YESTERDAY -> stringRes(R.string.day_yesterday)
        Dates.RelDay.TODAY -> stringRes(R.string.day_today)
        Dates.RelDay.TOMORROW -> stringRes(R.string.day_tomorrow)
        else -> Dates.monthName(LocalDate.parse(dayKey)) + " " + LocalDate.parse(dayKey).dayOfMonth
    }

    SectionCard {
        SectionHeader(stringRes(R.string.summary_heading) + " · " + dayLabel)
        if (sentences.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(sentences.joinToString(" "), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatRow(stringRes(R.string.label_sunrise), Dates.clock(day.sunriseSec, forecast.timezone))
                StatRow(stringRes(R.string.label_sunset), Dates.clock(day.sunsetSec, forecast.timezone))
                day.daylightDurationSec?.let {
                    StatRow(stringRes(R.string.label_daylight), formatDuration(it))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (day.moonriseSec > 0) StatRow(stringRes(R.string.label_moonrise), Dates.clock(day.moonriseSec, forecast.timezone))
                if (day.moonsetSec > 0) StatRow(stringRes(R.string.label_moonset), Dates.clock(day.moonsetSec, forecast.timezone))
                day.moonPhase?.let {
                    StatRow(
                        stringRes(R.string.label_moon),
                        Narrative.moonPhaseName(context, it) +
                            " " + ((Narrative.moonIllumination(it) * 100).toInt()) + "%"
                    )
                }
                day.uvIndexMax?.let {
                    StatRow(
                        stringRes(R.string.label_uv_index),
                        Fmt.num1(it) + " · " + Narrative.uvLabel(context, it)
                    )
                }
            }
        }
        // Sunshine-of-daylight bar
        val daylight = day.daylightDurationSec
        val sunshine = day.sunshineDurationSec
        if (daylight != null && daylight > 0 && Warnings.sunIsSignificant(sunshine, daylight)) {
            Spacer(Modifier.height(10.dp))
            val pct = Warnings.sunshinePercent(sunshine, daylight)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(pct / 100f)
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val h = (seconds / 3600).toInt()
    val m = ((seconds / 60).toInt()) % 60
    return "${h}h ${m.toString().padStart(2, '0')}m"
}

/** Nearby cities strip for cross-referencing the forecast. */
@Composable
private fun NearbySection(state: WeekUiState, settings: Settings) {
    val forecast = state.forecast ?: return
    SectionCard {
        SectionHeader(stringRes(R.string.nearby_heading))
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.nearby.size) { i ->
                val entry = state.nearby[i]
                val summary = entry.daily?.byDate?.get(state.selectedDayKey)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(
                        Modifier.padding(10.dp).width(96.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(entry.city.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Text(
                            Fmt.km(entry.city.distanceKm) + " km",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (summary != null) {
                            WeatherGlyphIcon(summary.weatherCode, true, 26.dp)
                            Text(
                                Fmt.temp(summary.max, Units.tempUnit(settings.units)) + " / " +
                                    Fmt.temp(summary.min, Units.tempUnit(settings.units)),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
