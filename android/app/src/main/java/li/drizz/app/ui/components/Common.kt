package li.drizz.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import li.drizz.app.R
import li.drizz.app.charts.BoltAccent
import li.drizz.app.charts.RainAccent
import li.drizz.app.charts.SunAccent
import li.drizz.app.charts.WeatherGlyphs
import li.drizz.app.domain.FriendlyError
import li.drizz.app.domain.WeatherGlyph
import li.drizz.app.domain.weatherGlyph
import li.drizz.app.ui.stringRes

/** A single weather glyph rendered with the theme palette. */
@Composable
fun WeatherGlyphIcon(
    code: Double?,
    daytime: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val glyph = weatherGlyph(code, daytime)
    val backdrop = MaterialTheme.colorScheme.surfaceContainerLowest
    val snow = Color(0xFF7EB3E8)
    Canvas(modifier.size(size)) {
        with(WeatherGlyphs) {
            drawGlyph(
                glyph, this.size.width / 2, this.size.height / 2,
                minOf(this.size.width, this.size.height) * 0.92f,
                iconColor = tint, backdrop = backdrop,
                dropColor = RainAccent, boltColor = BoltAccent, sunColor = SunAccent
            )
        }
    }
}

/** Card wrapper used by every section, on surfaceContainerLow. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/** Section header row with an optional trailing control. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        trailing()
    }
}

@Composable
fun LoadingPanel(modifier: Modifier = Modifier, label: String? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
        if (label != null) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Friendly error panel: headline, hint, collapsed technical detail, retry. */
@Composable
fun ErrorPanel(
    error: FriendlyError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    extraAction: Pair<String, () -> Unit>? = null
) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Text(stringRes(error.titleRes), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            stringRes(error.hintRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (error.detail != null) {
            Text(
                error.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3
            )
        }
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_try_again)) }
        }
        if (extraAction != null) {
            TextButton(onClick = extraAction.second) { Text(extraAction.first) }
        }
    }
}

/** Small labelled value pair used in day summaries. */
@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Legend dot + label. */
@Composable
fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Tiny wind-direction arrow used inside the hourly table. */
@Composable
fun MiniWindArrow(directionDeg: Double, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Canvas(modifier.size(12.dp)) {
        with(WeatherGlyphs) {
            drawWindArrow(size.width / 2f, size.height / 2f, size.width, directionDeg, tint)
        }
    }
}

