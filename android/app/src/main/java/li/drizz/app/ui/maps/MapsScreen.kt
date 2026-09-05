package li.drizz.app.ui.maps

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.viewinterop.AndroidView
import li.drizz.app.R
import li.drizz.app.data.settings.Settings
import li.drizz.app.domain.ModelCatalog
import li.drizz.app.ui.components.SectionCard
import li.drizz.app.ui.stringRes

/**
 * The Open-Meteo map viewer (maps.open-meteo.com) — the native equivalent of
 * the website's embedded iframe. The selected location seeds the hash, and the
 * drizz.li model id is translated through the same domain-candidate table.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapsScreen(settings: Settings) {
    val context = LocalContext.current
    val mapUrl = remember(settings.location, settings.model) {
        val zoom = 6
        // https://maps.open-meteo.com/#<zoom>/<lat>/<lng>
        "https://maps.open-meteo.com/#$zoom/%.4f/%.4f".format(
            java.util.Locale.ROOT, settings.location.latitude, settings.location.longitude
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SectionCard {
            Text(
                stringRes(R.string.maps_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (settings.model != "best_match") {
                Text(
                    "Model → domain: " + (ModelCatalog.mapsDomainCandidates(settings.model).firstOrNull() ?: settings.model),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapUrl)))
            }) { Text(stringRes(R.string.open_in_browser)) }
        }
        Spacer(Modifier.height(12.dp))
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl(mapUrl)
                }
            },
            update = { webView ->
                if (webView.url != mapUrl) webView.loadUrl(mapUrl)
            }
        )
        Spacer(Modifier.height(8.dp))
    }
}
