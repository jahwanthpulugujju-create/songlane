package app.songlane.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.songlane.domain.Track
import app.songlane.domain.duration
import kotlin.math.*

@Composable
fun Glyph(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(icon, label, modifier.size(22.dp), tint)
}

@Composable
fun IconAction(
    icon: ImageVector,
    label: String,
    tint: Color = LocalContentColor.current,
    onClick: () -> Unit,
) {
    IconButton(onClick) { Glyph(icon, label, tint = tint) }
}

@Composable
fun Eyebrow(text: String, color: Color = Muted) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onAction) { Text(action, color = Sage) }
    }
}

@Composable
fun Cover(track: Track?, modifier: Modifier = Modifier, corner: Dp = 14.dp) {
    val seed = (track?.id?.hashCode() ?: 42).toUInt().toLong()
    val palettes =
        listOf(
            listOf(Color(0xFFB6BC83), Color(0xFF444E36), Color(0xFFE4DFC0)),
            listOf(Color(0xFFD69B75), Color(0xFF603E31), Color(0xFFF2D7AD)),
            listOf(Color(0xFF819FAD), Color(0xFF2C4651), Color(0xFFC5D4CA)),
            listOf(Color(0xFF9F94B2), Color(0xFF493F58), Color(0xFFD4CBB8)),
        )
    val palette = palettes[(seed % palettes.size).toInt()]
    Canvas(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(corner)).background(palette[0]).semantics {
            contentDescription = "Procedural artwork for ${track?.title ?: "Songlane"}"
        }
    ) {
        val w = size.width
        val h = size.height
        drawCircle(palette[1], w * 0.38f, Offset(w * 0.65f, h * 0.42f))
        for (i in 0..10) {
            val y = h * (0.24f + i * 0.055f)
            val path =
                Path().apply {
                    moveTo(0f, y)
                    cubicTo(w * 0.25f, y - w * 0.3f, w * 0.7f, y + w * 0.33f, w, y - w * 0.1f)
                }
            drawPath(path, palette[2].copy(alpha = 0.64f), style = Stroke(w * 0.008f))
        }
        drawCircle(palette[0], w * 0.06f, Offset(w * 0.65f, h * 0.42f))
    }
}

@Composable
fun CompassArt(modifier: Modifier = Modifier, light: Boolean = false) {
    Canvas(
        modifier.aspectRatio(1f).semantics {
            contentDescription = "Songlane compass: a path from familiar to new"
        }
    ) {
        val w = size.width
        val center = Offset(w / 2, size.height / 2)
        val line = if (light) Ink else Sage
        listOf(0.22f, 0.34f, 0.46f).forEach { r ->
            drawCircle(line.copy(alpha = 0.23f), w * r, center, style = Stroke(1.dp.toPx()))
        }
        for (i in 0..11) {
            val a = i * PI / 6
            drawLine(
                line.copy(alpha = .25f),
                center + Offset((cos(a) * w * .43).toFloat(), (sin(a) * w * .43).toFloat()),
                center + Offset((cos(a) * w * .46).toFloat(), (sin(a) * w * .46).toFloat()),
                1.dp.toPx(),
            )
        }
        val points =
            listOf(
                Offset(w * .17f, w * .69f),
                Offset(w * .35f, w * .61f),
                Offset(w * .46f, w * .38f),
                Offset(w * .66f, w * .46f),
                Offset(w * .80f, w * .23f),
            )
        val path =
            Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
        drawPath(path, line, style = Stroke(2.dp.toPx()))
        points.forEachIndexed { i, p ->
            drawCircle(
                if (i == points.lastIndex) line else if (light) Sage else Panel,
                if (i == points.lastIndex) 5.dp.toPx() else 4.dp.toPx(),
                p,
            )
            if (i != points.lastIndex)
                drawCircle(line, 4.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    favorite: Boolean,
    active: Boolean = false,
    subtitle: String? = null,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onMore: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onPlay)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track, Modifier.size(54.dp), 9.dp)
        Column(Modifier.weight(1f).padding(start = 13.dp)) {
            Text(
                track.title,
                color = if (active) Sage else Cream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                subtitle ?: "${track.artist} · ${duration(track.durationMs)}",
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconAction(
            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (favorite) "Unlike ${track.title}" else "Like ${track.title}",
            if (favorite) Sage else Muted,
            onFavorite,
        )
        if (onMore != null)
            IconAction(Icons.Rounded.MoreVert, "More options for ${track.title}", onClick = onMore)
    }
}

@Composable
fun EmptyLibrary(onImport: () -> Unit, onDemo: () -> Unit, busy: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CompassArt(Modifier.size(90.dp))
        Text("Your music. A new direction.", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Add the audio files you own, or try eight original sound sketches. No account needed.",
            color = Muted,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Button(
            onImport,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
        ) {
            Glyph(Icons.Rounded.Add, "")
            Spacer(Modifier.width(8.dp))
            Text("Add your music")
        }
        OutlinedButton(
            onDemo,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
        ) {
            Text("Try the sound sketches")
        }
        Eyebrow("Private by default · Works offline")
    }
}

@Composable
fun SettingToggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
fun InfoCard(title: String, body: String, icon: ImageVector = Icons.Rounded.Info) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Glyph(icon, title, tint = Sage)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(body, color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
