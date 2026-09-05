package li.drizz.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import li.drizz.app.R
import li.drizz.app.data.WeatherRepository
import li.drizz.app.data.settings.Settings
import li.drizz.app.data.settings.SettingsRepository
import li.drizz.app.di.AppGraph
import li.drizz.app.domain.model.GeoLocation
import li.drizz.app.util.countryCodeFlag
import li.drizz.app.ui.stringRes

data class SearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<GeoLocation> = emptyList()
)

class SearchViewModel(private val repo: WeatherRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state = _state.asStateFlow()
    private var lastQueried: String? = null

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.length < 2 || q == lastQueried) {
            if (q.isEmpty()) _state.value = _state.value.copy(results = emptyList(), searching = false)
            return
        }
        lastQueried = q
        viewModelScope.launch {
            _state.value = _state.value.copy(searching = true)
            try {
                val results = repo.geocodeSearch(q, count = 10)
                _state.value = _state.value.copy(results = results, searching = false)
            } catch (_: Throwable) {
                _state.value = _state.value.copy(results = emptyList(), searching = false)
            }
        }
    }
}

/** Location search with recents + favourites, mirroring location-search.svelte. */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun LocationSearchSheet(settings: Settings, onDismiss: () -> Unit) {
    val vm: SearchViewModel = viewModel { SearchViewModel(AppGraph.weather) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settingsRepo = AppGraph.settings
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(state.query) {
        kotlinx.coroutines.delay(250)
        vm.search()
    }

    fun pick(location: GeoLocation) {
        scope.launch {
            settingsRepo.setLocation(location)
            settingsRepo.addRecent(location)
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { vm.setQuery(it) },
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.padding(4.dp))
            if (state.searching) {
                Row(Modifier.padding(16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    CircularProgressIndicator(Modifier.padding(8.dp))
                }
            }

            val favKeys = settings.favorites.map { SettingsRepository.locationKey(it) }.toSet()

            fun locationRow(location: GeoLocation, isRecent: Boolean) {
                val key = SettingsRepository.locationKey(location)
                val isFav = key in favKeys
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { pick(location) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(countryCodeFlag(location.countryCode) + " ", style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.weight(1f)) {
                        Text(location.name, style = MaterialTheme.typography.bodyLarge)
                        val sub = listOfNotNull(location.admin1, location.country).joinToString(", ")
                        if (sub.isNotEmpty()) {
                            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (isRecent) {
                        IconButton(onClick = {
                            scope.launch { settingsRepo.removeRecent(location) }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = {
                        scope.launch { settingsRepo.toggleFavorite(location) }
                    }) {
                        Icon(
                            if (isFav) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint = if (isFav) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                if (state.results.isEmpty()) {
                    if (settings.favorites.isNotEmpty()) {
                        item(key = "fav_header") {
                            Text(
                                stringResource(R.string.search_favorites),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(settings.favorites.size, key = { "f" + settings.favorites[it].let { l -> SettingsRepository.locationKey(l) } }) { i ->
                            locationRow(settings.favorites[i], isRecent = false)
                        }
                    }
                    if (settings.recents.isNotEmpty()) {
                        item(key = "rec_header") {
                            Text(
                                stringResource(R.string.search_recent),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        val visible = settings.recents.filter { SettingsRepository.locationKey(it) !in favKeys }
                        items(visible.size, key = { "r" + SettingsRepository.locationKey(visible[it]) }) { i ->
                            locationRow(visible[i], isRecent = true)
                        }
                    }
                } else {
                    items(state.results.size, key = { "s" + state.results[it].id }) { i ->
                        locationRow(state.results[i], isRecent = false)
                    }
                }
            }
        }
    }
}
