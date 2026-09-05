package li.drizz.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import li.drizz.app.R
import li.drizz.app.data.settings.PrecipUnit
import li.drizz.app.data.settings.Settings
import li.drizz.app.data.settings.SettingsRepository
import li.drizz.app.data.settings.TempUnit
import li.drizz.app.data.settings.ThemeMode
import li.drizz.app.data.settings.WindUnit
import li.drizz.app.di.AppGraph
import li.drizz.app.ui.stringRes

/** Theme, dynamic color, units and about — the port of the settings menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(settings: Settings, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val repo = AppGraph.settings
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringRes(R.string.settings_title), style = MaterialTheme.typography.titleMedium)

            // Theme
            Text(stringRes(R.string.theme_label), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val modes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.theme == mode,
                        onClick = { scope.launch { repo.setTheme(mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> stringRes(R.string.theme_system)
                                    ThemeMode.LIGHT -> stringRes(R.string.theme_light)
                                    ThemeMode.DARK -> stringRes(R.string.theme_dark)
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }

            // Dynamic color
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.setting_dynamic_color), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = settings.dynamicColor,
                    onCheckedChange = { on -> scope.launch { repo.setDynamicColor(on) } }
                )
            }

            // Units
            Text(stringRes(R.string.units_title), style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringRes(R.string.unit_temperature), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                UnitRow(
                    options = TempUnit.entries.map { it.symbol },
                    selectedIndex = TempUnit.entries.indexOf(settings.units.temperature)
                ) { index -> scope.launch { repo.setUnits(settings.units.copy(temperature = TempUnit.entries[index])) } }

                Text(stringRes(R.string.unit_wind_speed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                UnitRow(
                    options = WindUnit.entries.map { it.label },
                    selectedIndex = WindUnit.entries.indexOf(settings.units.windSpeed)
                ) { index -> scope.launch { repo.setUnits(settings.units.copy(windSpeed = WindUnit.entries[index])) } }

                Text(stringRes(R.string.unit_precipitation), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                UnitRow(
                    options = PrecipUnit.entries.map { it.label },
                    selectedIndex = PrecipUnit.entries.indexOf(settings.units.precipitation)
                ) { index -> scope.launch { repo.setUnits(settings.units.copy(precipitation = PrecipUnit.entries[index])) } }
            }

            // Language note
            Text(
                stringResource(R.string.setting_language_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // About
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.about_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { open(context, "https://drizz.li") }) { Text("drizz.li") }
                TextButton(onClick = { open(context, "https://github.com/open-meteo/drizz.li") }) { Text("GitHub") }
                TextButton(onClick = { open(context, "https://open-meteo.com") }) { Text("Open-Meteo") }
            }
            Text(
                "Data: Open-Meteo (CC BY 4.0) · GeoNames · DWD ICON, NOAA GFS/HRRR, ECMWF, " +
                    "Météo-France, UKMO, KNMI, DMI, MET Norway, MeteoSwiss, JMA, CMA, CHMI, GEM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UnitRow(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(option, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }
}

private fun open(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
