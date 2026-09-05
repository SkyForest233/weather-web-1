package li.drizz.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import li.drizz.app.R
import li.drizz.app.data.settings.Settings
import li.drizz.app.ui.compare.CompareScreen
import li.drizz.app.util.countryCodeFlag
import li.drizz.app.ui.ensemble.EnsembleScreen
import li.drizz.app.ui.historical.HistoricalScreen
import li.drizz.app.ui.maps.MapsScreen
import li.drizz.app.ui.search.LocationSearchSheet
import li.drizz.app.ui.seasonal.SeasonalScreen
import li.drizz.app.ui.settings.SettingsSheet
import li.drizz.app.ui.week.WeekScreen

/**
 * Adaptive app shell, following the MD3 navigation decision tree: 6 primary
 * destinations → a NavigationBar with the three main pages plus a "More"
 * sheet on compact screens, and a NavigationRail exposing all six on medium
 * and expanded widths.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun AppRoot(settings: Settings) {
    val navController = rememberNavController()
    val windowSize = calculateWindowSizeClass(LocalContext.current as androidx.activity.ComponentActivity)
    val compact = windowSize.widthSizeClass == WindowWidthSizeClass.Compact

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var moreOpen by rememberSaveable { mutableStateOf(false) }

    var backStackEntry by remember { mutableStateOf(navController.currentBackStackEntry) }
    navController.addOnDestinationChangedListener { _, entry, _ -> backStackEntry = entry }
    val currentRoute = backStackEntry?.destination?.route
    val currentPage = Page.of(currentRoute) ?: Page.WEEK

    val barPages = listOf(Page.WEEK, Page.COMPARE, Page.ENSEMBLE)
    val railPages = Page.entries.toList()

    fun navigate(page: Page) {
        navController.navigate(page.route) {
            popUpTo(Page.WEEK.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { searchOpen = true }
                    ) {
                        Text(
                            text = countryCodeFlag(settings.location.countryCode) + " ",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Column {
                            Text(settings.location.name, style = MaterialTheme.typography.titleLarge)
                            val subtitle = listOfNotNull(
                                settings.location.admin1,
                                settings.location.country
                            ).joinToString(", ")
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { searchOpen = true }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResourceCompat(R.string.search_placeholder))
                    }
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResourceCompat(R.string.settings_title))
                    }
                    if (compact) {
                        IconButton(onClick = { moreOpen = true }) {
                            Icon(Icons.Filled.MoreHoriz, contentDescription = stringResourceCompat(R.string.nav_more))
                        }
                    }
                },
                scrollBehavior = TopAppBarDefaults.pinnableScrollBehavior()
            )
        },
        bottomBar = {
            if (compact) {
                NavigationBar {
                    barPages.forEach { page ->
                        val selected = currentPage == page
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigate(page) },
                            icon = { Icon(if (selected) page.selectedIcon else page.icon, contentDescription = null) },
                            label = { Text(stringRes(page.shortLabelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(Modifier.padding(innerPadding)) {
            if (!compact) {
                NavigationRail {
                    Spacer(Modifier.size(8.dp))
                    railPages.forEach { page ->
                        val selected = currentPage == page
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigate(page) },
                            icon = { Icon(if (selected) page.selectedIcon else page.icon, contentDescription = null) },
                            label = { Text(stringRes(page.shortLabelRes), maxLines = 1) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            NavHost(
                navController = navController,
                startDestination = Page.WEEK.route,
                modifier = Modifier.weight(1f),
                enterTransition = { fadeIn(androidx.compose.animation.core.tween(220)) + slideInHorizontally { it / 24 } },
                exitTransition = { fadeOut(androidx.compose.animation.core.tween(180)) },
                popEnterTransition = { fadeIn(androidx.compose.animation.core.tween(220)) },
                popExitTransition = { fadeOut(androidx.compose.animation.core.tween(180)) + slideOutHorizontally { it / 24 } }
            ) {
                composable(Page.WEEK.route) { WeekScreen(settings) }
                composable(Page.COMPARE.route) { CompareScreen(settings) }
                composable(Page.ENSEMBLE.route) { EnsembleScreen(settings) }
                composable(Page.SEASONAL.route) { SeasonalScreen(settings) }
                composable(Page.HISTORICAL.route) { HistoricalScreen(settings) }
                composable(Page.MAPS.route) { MapsScreen(settings) }
            }
        }
    }

    if (searchOpen) {
        LocationSearchSheet(settings = settings, onDismiss = { searchOpen = false })
    }
    if (settingsOpen) {
        SettingsSheet(settings = settings, onDismiss = { settingsOpen = false })
    }
    if (moreOpen && compact) {
        ModalBottomSheet(onDismissRequest = { moreOpen = false }) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Text(
                    stringRes(R.string.nav_more),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                listOf(Page.SEASONAL, Page.HISTORICAL, Page.MAPS).forEach { page ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { moreOpen = false; navigate(page) }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringRes(page.labelRes), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun stringRes(id: Int, vararg args: Any): String = androidx.compose.ui.res.stringResource(id, *args)

