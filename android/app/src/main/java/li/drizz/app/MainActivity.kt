package li.drizz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.drizz.app.data.settings.Settings
import li.drizz.app.di.AppGraph
import li.drizz.app.ui.AppRoot
import li.drizz.app.ui.theme.DrizzTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
            DrizzTheme(themeMode = settings.theme, dynamicColor = settings.dynamicColor) {
                AppRoot(settings)
            }
        }
    }
}
