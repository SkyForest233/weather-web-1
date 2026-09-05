package li.drizz.app.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.ui.graphics.vector.ImageVector
import li.drizz.app.R

/** The six primary destinations, mirroring drizz.li's weather routes. */
enum class Page(
    val route: String,
    @StringRes val labelRes: Int,
    @StringRes val shortLabelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    WEEK("week", R.string.nav_week, R.string.nav_week, Icons.Outlined.WbCloudy, Icons.Filled.WbCloudy),
    COMPARE("compare", R.string.nav_compare, R.string.nav_compare_short, Icons.Outlined.QueryStats, Icons.Filled.QueryStats),
    ENSEMBLE("ensemble", R.string.nav_14day, R.string.nav_14day_short, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    SEASONAL("seasonal", R.string.nav_seasonal, R.string.nav_seasonal, Icons.Outlined.Eco, Icons.Filled.Eco),
    HISTORICAL("historical", R.string.nav_historical, R.string.nav_historical, Icons.Outlined.Map, Icons.Filled.Map),
    MAPS("maps", R.string.nav_maps, R.string.nav_maps, Icons.Outlined.Map, Icons.Filled.Map);

    companion object {
        fun of(route: String?): Page? = entries.firstOrNull { it.route == route }
    }
}
