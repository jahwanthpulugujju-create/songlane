package app.songlane.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.songlane.MainViewModel
import app.songlane.domain.*
import app.songlane.playback.PlaybackState

@Composable
fun ListenScreen(
    vm: MainViewModel,
    state: LibraryState,
    player: PlaybackState,
    busy: Boolean,
    onImport: () -> Unit,
    onCompass: () -> Unit,
    onLibrary: () -> Unit,
    onTrack: (Track) -> Unit,
) {
    val recent =
        state.history
            .asReversed()
            .distinctBy { it.trackId }
            .mapNotNull { h -> state.tracks.find { it.id == h.trackId } }
            .take(6)
    val rediscover = DiscoveryEngine.rediscover(state).take(4)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(Modifier.padding(top = 16.dp)) {
                Eyebrow("A little familiar. A little further.", Sage)
                Spacer(Modifier.height(12.dp))
                Text("Find your next\nfavorite.", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Good music takes you places.\nChoose where to begin.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Muted,
                )
            }
        }
        item {
            Surface(onClick = onCompass, color = Sage, shape = RoundedCornerShape(24.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("Sonic Compass", Ink.copy(alpha = .7f))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Follow a\nnew thread.",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Ink,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Build a listening route",
                                color = Ink,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.width(6.dp))
                            Glyph(Icons.Rounded.ArrowOutward, "", tint = Ink)
                        }
                    }
                    CompassArt(Modifier.width(132.dp), light = true)
                }
            }
        }
        if (state.tracks.isEmpty()) item { EmptyLibrary(onImport, vm::loadDemos, busy) }
        else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton({ vm.shuffle() }, Modifier.weight(1f).heightIn(min = 50.dp)) {
                        Glyph(Icons.Rounded.Shuffle, "")
                        Spacer(Modifier.width(6.dp))
                        Text("Fair shuffle")
                    }
                    OutlinedButton(onLibrary, Modifier.weight(1f).heightIn(min = 50.dp)) {
                        Glyph(Icons.Rounded.LibraryMusic, "")
                        Spacer(Modifier.width(6.dp))
                        Text("Your library")
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item { SectionTitle("Back in your orbit", "See all", onLibrary) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(recent, key = { it.id }) { track ->
                            Column(
                                Modifier.width(128.dp).clip(RoundedCornerShape(16.dp)).clickable {
                                    vm.play(recent, recent.indexOf(track))
                                }
                            ) {
                                Cover(track, Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    track.title,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    track.artist,
                                    maxLines = 1,
                                    color = Muted,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
            item {
                SectionTitle(
                    if (state.favorites.isEmpty()) "Waiting to be heard" else "Worth another listen"
                )
                Text(
                    "A fresh look at your own collection.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(rediscover, key = { it.id }) { track ->
                TrackRow(
                    track,
                    track.id in state.favorites,
                    track.id == player.currentId,
                    subtitle =
                        if (state.history.none { it.trackId == track.id })
                            "Unheard in your recorded history"
                        else "Not played in the last 7 days",
                    onPlay = { vm.play(rediscover, rediscover.indexOf(track)) },
                    onFavorite = { vm.favorite(track.id) },
                    onMore = { onTrack(track) },
                )
            }
            item {
                InfoCard(
                    "Your taste belongs to you",
                    "Your library and listening history stay on this device. Take them with you in a Taste Passport.",
                    Icons.Rounded.Fingerprint,
                )
            }
        }
    }
}
