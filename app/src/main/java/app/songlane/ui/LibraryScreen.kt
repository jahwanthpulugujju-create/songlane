package app.songlane.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.songlane.MainViewModel
import app.songlane.domain.*
import app.songlane.playback.PlaybackState

@Composable
fun LibraryScreen(
    vm: MainViewModel,
    state: LibraryState,
    player: PlaybackState,
    busy: Boolean,
    onImport: () -> Unit,
    onTrack: (Track) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Songs") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var playlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var create by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var newest by rememberSaveable { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val playlist = state.playlists.find { it.id == playlistId }
    val base =
        when {
            playlist != null ->
                playlist.trackIds.mapNotNull { id -> state.tracks.find { it.id == id } }
            filter == "Favorites" -> state.tracks.filter { it.id in state.favorites }
            filter == "Albums" && group != null -> state.tracks.filter { it.album == group }
            filter == "Artists" && group != null -> state.tracks.filter { it.artist == group }
            else -> state.tracks
        }
    val songs =
        remember(base, query, newest, playlistId) {
            search(base, query).let {
                if (playlistId != null) it
                else if (newest) it.sortedByDescending { t -> t.addedAt }
                else it.sortedBy { t -> normalize(t.title) }
            }
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp, 12.dp, 24.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            if (group != null || playlist != null)
                TextButton({
                    group = null
                    playlistId = null
                }) {
                    Glyph(Icons.Rounded.ArrowBack, "")
                    Text("All ${filter.lowercase()}")
                }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        playlist?.name ?: group ?: "Your collection.",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        "${songs.size} songs · stored on your device",
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconAction(Icons.Rounded.Add, "Add music", onClick = onImport)
            }
        }
        item {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth(),
                placeholder = { Text("Songs, artists, albums, tags…") },
                supportingText = {
                    if (query.isNotBlank())
                        Text("Includes close spellings. Choose the recording you want.")
                },
                leadingIcon = { Glyph(Icons.Rounded.Search, "Search library") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
        }
        if (group == null && playlist == null)
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Songs", "Albums", "Artists", "Playlists", "Favorites")) { label ->
                        FilterChip(
                            filter == label,
                            {
                                filter = label
                                group = null
                                playlistId = null
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
        if (playlist != null)
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button({ vm.play(songs) }, enabled = songs.isNotEmpty()) {
                        Glyph(Icons.Rounded.PlayArrow, "")
                        Text("Play")
                    }
                    IconAction(
                        Icons.Rounded.Shuffle,
                        "Shuffle playlist",
                        onClick = { vm.shuffle(songs) },
                    )
                    IconAction(
                        Icons.Rounded.Edit,
                        "Rename playlist",
                        onClick = {
                            name = playlist.name
                            rename = true
                        },
                    )
                    IconAction(
                        Icons.Rounded.DeleteOutline,
                        "Delete playlist",
                        onClick = { delete = true },
                    )
                }
                Text(
                    "Add songs using their menu. Use the arrows to change playlist order.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        else if (filter != "Playlists" && (filter !in listOf("Artists", "Albums") || group != null))
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    TextButton({ vm.shuffle(songs) }, enabled = songs.isNotEmpty()) {
                        Glyph(Icons.Rounded.Shuffle, "")
                        Spacer(Modifier.width(8.dp))
                        Text("Shuffle ${songs.size} songs")
                    }
                    TextButton({ newest = !newest }) { Text(if (newest) "Newest first" else "A–Z") }
                }
            }
        when {
            state.tracks.isEmpty() -> item { EmptyLibrary(onImport, vm::loadDemos, busy) }
            filter == "Playlists" && playlist == null -> {
                item {
                    OutlinedButton(
                        {
                            name = ""
                            create = true
                        },
                        Modifier.fillMaxWidth(),
                    ) {
                        Glyph(Icons.Rounded.Add, "")
                        Text("Create playlist")
                    }
                }
                items(
                    state.playlists.filter { normalize(query) in normalize(it.name) },
                    key = { it.id },
                ) { p ->
                    ListItem(
                        headlineContent = { Text(p.name) },
                        supportingContent = { Text("${p.trackIds.size} songs") },
                        leadingContent = { Glyph(Icons.Rounded.QueueMusic, p.name, tint = Sage) },
                        trailingContent = { Glyph(Icons.Rounded.ChevronRight, "") },
                        modifier = Modifier.clickable { playlistId = p.id },
                        colors = ListItemDefaults.colors(containerColor = Panel),
                    )
                }
                if (state.playlists.isEmpty())
                    item {
                        InfoCard(
                            "Make room for a mood",
                            "Create your first playlist, then add songs from any track menu.",
                            Icons.Rounded.QueueMusic,
                        )
                    }
            }
            filter in listOf("Albums", "Artists") && group == null -> {
                val groups =
                    songs.groupBy { if (filter == "Albums") it.album else it.artist }.toSortedMap()
                items(groups.keys.toList(), key = { it }) { key ->
                    ListItem(
                        headlineContent = { Text(key) },
                        supportingContent = { Text("${groups[key]?.size} songs") },
                        leadingContent = {
                            Cover(groups[key]?.firstOrNull(), Modifier.size(52.dp))
                        },
                        trailingContent = { Glyph(Icons.Rounded.ChevronRight, "") },
                        modifier = Modifier.clickable { group = key },
                        colors = ListItemDefaults.colors(containerColor = Panel),
                    )
                }
            }
            else -> {
                if (songs.isEmpty())
                    item {
                        InfoCard(
                            "No songs here yet",
                            if (query.isNotBlank())
                                "Try another search. Words match across your file metadata and tags."
                            else "Add songs or like a few favorites to fill this collection.",
                        )
                    }
                items(songs, key = { it.id }) { t ->
                    Column {
                        TrackRow(
                            t,
                            t.id in state.favorites,
                            t.id == player.currentId,
                            onPlay = { vm.play(songs, songs.indexOf(t)) },
                            onFavorite = { vm.favorite(t.id) },
                            onMore = { onTrack(t) },
                        )
                        if (playlist != null)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                val index = playlist.trackIds.indexOf(t.id)
                                IconAction(
                                    Icons.Rounded.KeyboardArrowUp,
                                    "Move ${t.title} up",
                                    onClick = {
                                        if (index > 0)
                                            vm.action {
                                                vm.repo.changePlaylist(playlist.id) { p ->
                                                    p.copy(
                                                        trackIds =
                                                            p.trackIds.toMutableList().apply {
                                                                add(index - 1, removeAt(index))
                                                            }
                                                    )
                                                }
                                            }
                                    },
                                )
                                IconAction(
                                    Icons.Rounded.KeyboardArrowDown,
                                    "Move ${t.title} down",
                                    onClick = {
                                        if (index < playlist.trackIds.lastIndex)
                                            vm.action {
                                                vm.repo.changePlaylist(playlist.id) { p ->
                                                    p.copy(
                                                        trackIds =
                                                            p.trackIds.toMutableList().apply {
                                                                add(index + 1, removeAt(index))
                                                            }
                                                    )
                                                }
                                            }
                                    },
                                )
                                TextButton({
                                    vm.action {
                                        vm.repo.changePlaylist(playlist.id) { p ->
                                            p.copy(trackIds = p.trackIds - t.id)
                                        }
                                    }
                                }) {
                                    Text("Remove")
                                }
                            }
                    }
                }
            }
        }
    }
    if (create || rename)
        AlertDialog(
            onDismissRequest = {
                create = false
                rename = false
            },
            title = { Text(if (rename) "Rename playlist" else "A name for this feeling") },
            text = {
                OutlinedTextField(
                    name,
                    { name = it.take(100) },
                    label = { Text("Playlist name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    {
                        vm.action {
                            if (rename && playlist != null)
                                vm.repo.changePlaylist(playlist.id) { it.copy(name = name.trim()) }
                            else vm.repo.createPlaylist(name)
                        }
                        create = false
                        rename = false
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton({
                    create = false
                    rename = false
                }) {
                    Text("Cancel")
                }
            },
        )
    if (delete && playlist != null)
        AlertDialog(
            onDismissRequest = { delete = false },
            title = { Text("Delete ${playlist.name}?") },
            text = { Text("The songs stay in your library.") },
            confirmButton = {
                TextButton({
                    vm.action { vm.repo.deletePlaylist(playlist.id) }
                    playlistId = null
                    delete = false
                }) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton({ delete = false }) { Text("Cancel") } },
        )
}
