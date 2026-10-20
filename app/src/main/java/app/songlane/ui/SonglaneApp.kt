@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.songlane.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.songlane.MainViewModel
import app.songlane.domain.*
import app.songlane.playback.PlaybackState

@Composable
fun SonglaneApp(vm: MainViewModel) {
    val state by vm.library.collectAsStateWithLifecycle()
    val player by vm.playback.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("Listen") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var fullPlayer by rememberSaveable { mutableStateOf(false) }
    var queue by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Track?>(null) }
    var details by remember { mutableStateOf<Track?>(null) }
    var addToPlaylist by remember { mutableStateOf<Track?>(null) }
    var confirmExport by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val files =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            if (uris.isNotEmpty()) vm.importFiles(uris)
        }
    val folder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    .onSuccess { vm.importFolder(uri) }
                    .onFailure {
                        vm.notify("Cannot keep access to this folder. Choose files instead.")
                    }
            }
        }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) vm.scanDevice()
            else
                vm.notify(
                    "Audio permission declined. You can still choose individual files or folders."
                )
        }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) vm.export(uri)
        }
    val passport =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importPassport(uri)
        }
    val onExport = { confirmExport = true }
    val scan = {
        permission.launch(
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }
    BackHandler(settings) { settings = false }
    BackHandler(!settings && tab != "Listen") { tab = "Listen" }
    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            Row(
                Modifier.fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).clickable {
                        tab = "Listen"
                        settings = false
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Glyph(Icons.Rounded.Route, "Songlane", tint = Sage)
                    Text("songlane", style = MaterialTheme.typography.titleLarge)
                }
                if (state.settings.privateSession)
                    Surface(color = Panel, shape = CircleShape) {
                        Row(
                            Modifier.padding(9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Glyph(Icons.Rounded.Shield, "Private session", tint = Sage)
                            Spacer(Modifier.width(4.dp))
                            Text("Private", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                IconAction(
                    Icons.Rounded.Search,
                    "Search music",
                    onClick = {
                        tab = "Library"
                        settings = false
                    },
                )
                IconAction(Icons.Rounded.Settings, "Settings", onClick = { settings = true })
            }
        },
        bottomBar = {
            Column {
                val track = state.tracks.find { it.id == player.currentId }
                if (track != null)
                    MiniPlayer(vm, track, player, { fullPlayer = true }, { queue = true })
                NavigationBar(containerColor = Ink, tonalElevation = 0.dp) {
                    listOf(
                            "Listen" to Icons.Rounded.Headphones,
                            "Compass" to Icons.Rounded.Explore,
                            "Library" to Icons.Rounded.LibraryMusic,
                            "You" to Icons.Rounded.Fingerprint,
                        )
                        .forEach { (label, icon) ->
                            NavigationBarItem(
                                selected = tab == label && !settings,
                                onClick = {
                                    tab = label
                                    settings = false
                                },
                                icon = { Glyph(icon, label) },
                                label = { Text(label) },
                                colors =
                                    NavigationBarItemDefaults.colors(
                                        indicatorColor = Panel,
                                        selectedIconColor = Sage,
                                        selectedTextColor = Sage,
                                        unselectedIconColor = Muted,
                                        unselectedTextColor = Muted,
                                    ),
                            )
                        }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().widthIn(max = 900.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (player.error != null)
                Surface(color = Panel) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            player.error!!,
                            color = Coral,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton({ vm.playback.next() }) { Text("Skip") }
                    }
                }
            if (settings)
                SettingsScreen(
                    vm,
                    state,
                    { settings = false },
                    { files.launch(arrayOf("audio/*")) },
                    { folder.launch(null) },
                    scan,
                    onExport,
                    { passport.launch(arrayOf("application/json", "text/plain")) },
                )
            else
                when (tab) {
                    "Listen" ->
                        ListenScreen(
                            vm,
                            state,
                            player,
                            busy,
                            { importing = true },
                            { tab = "Compass" },
                            { tab = "Library" },
                            { selected = it },
                        )
                    "Library" ->
                        LibraryScreen(
                            vm,
                            state,
                            player,
                            busy,
                            { importing = true },
                            { selected = it },
                        )
                    "Compass" -> CompassScreen(vm, state, busy, { importing = true })
                    else -> ProfileScreen(vm, state, { settings = true }, onExport)
                }
        }
    }
    if (importing)
        AlertDialog(
            onDismissRequest = { importing = false },
            title = { Text("Bring your music.") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Pick the files or folders Songlane can read. Your originals stay in place.",
                        color = Muted,
                    )
                    Button(
                        {
                            importing = false
                            files.launch(arrayOf("audio/*"))
                        },
                        Modifier.fillMaxWidth(),
                    ) {
                        Text("Choose audio files")
                    }
                    OutlinedButton(
                        {
                            importing = false
                            folder.launch(null)
                        },
                        Modifier.fillMaxWidth(),
                    ) {
                        Text("Choose a folder")
                    }
                    OutlinedButton(
                        {
                            importing = false
                            scan()
                        },
                        Modifier.fillMaxWidth(),
                    ) {
                        Text("Scan device music library")
                    }
                }
            },
            confirmButton = { TextButton({ importing = false }) { Text("Close") } },
        )
    if (confirmExport)
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            title = { Text("Export your Taste Passport?") },
            text = {
                Text(
                    "This file contains song metadata, favorites, playlists, taste preferences and listening history. It can reveal your listening habits. Audio, file paths and API keys are excluded."
                )
            },
            confirmButton = {
                TextButton({
                    confirmExport = false
                    export.launch("songlane-taste-passport.json")
                }) {
                    Text("Choose where to save")
                }
            },
            dismissButton = { TextButton({ confirmExport = false }) { Text("Cancel") } },
        )
    if (fullPlayer)
        ModalBottomSheet(
            onDismissRequest = { fullPlayer = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Ink,
        ) {
            PlayerScreen(
                vm,
                state,
                player,
                { fullPlayer = false },
                {
                    fullPlayer = false
                    queue = true
                },
            )
        }
    if (queue)
        ModalBottomSheet(
            onDismissRequest = { queue = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Ink,
        ) {
            QueueScreen(vm, state, player, { queue = false })
        }
    selected?.let { t ->
        ModalBottomSheet(onDismissRequest = { selected = null }, containerColor = Panel) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding()) {
                Text(t.title, style = MaterialTheme.typography.titleLarge)
                Text(t.artist, color = Muted)
                Spacer(Modifier.height(14.dp))
                ActionRow("Play next", Icons.Rounded.PlaylistPlay) {
                    vm.playback.add(t, true)
                    selected = null
                    vm.notify("Added to play next")
                }
                ActionRow("Add to queue", Icons.Rounded.QueueMusic) {
                    vm.playback.add(t, false)
                    selected = null
                    vm.notify("Added to queue")
                }
                ActionRow("Add to playlist", Icons.Rounded.PlaylistAdd) {
                    addToPlaylist = t
                    selected = null
                }
                ActionRow("Song details & tags", Icons.Rounded.Info) {
                    details = t
                    selected = null
                }
                ActionRow(
                    if (t.id in state.blocked) "Allow in discovery" else "Exclude from discovery",
                    Icons.Rounded.Block,
                ) {
                    vm.action { vm.repo.block(t.id) }
                    selected = null
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    details?.let { t ->
        TrackDetails(vm, state.tracks.find { it.id == t.id } ?: t, { details = null })
    }
    addToPlaylist?.let { t ->
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addToPlaylist = null },
            title = { Text("Add to a playlist") },
            text = {
                Column(
                    Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.playlists.forEach { p ->
                        TextButton({
                            vm.action {
                                vm.repo.changePlaylist(p.id) {
                                    it.copy(trackIds = (it.trackIds + t.id).distinct())
                                }
                            }
                            addToPlaylist = null
                        }) {
                            Text(p.name)
                        }
                    }
                    OutlinedTextField(
                        name,
                        { name = it.take(100) },
                        label = { Text("Or create a new playlist") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    {
                        vm.action { vm.repo.createPlaylist(name, listOf(t.id)) }
                        addToPlaylist = null
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text("Create & add")
                }
            },
            dismissButton = { TextButton({ addToPlaylist = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ActionRow(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Glyph(icon, text, tint = Sage)
        Text(text)
    }
}

@Composable
private fun MiniPlayer(
    vm: MainViewModel,
    track: Track,
    p: PlaybackState,
    onOpen: () -> Unit,
    onQueue: () -> Unit,
) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(track, Modifier.size(46.dp), 9.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        track.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        track.artist,
                        color = Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconAction(
                    if (p.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (p.playing) "Pause" else "Play",
                    tint = Sage,
                    onClick = vm.playback::toggle,
                )
                IconAction(Icons.Rounded.QueueMusic, "Open queue", onClick = onQueue)
            }
            LinearProgressIndicator(
                progress = {
                    if (p.durationMs > 0) (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f)
                    else 0f
                },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Sage,
                trackColor = Panel,
            )
        }
    }
}

@Composable
private fun PlayerScreen(
    vm: MainViewModel,
    state: LibraryState,
    p: PlaybackState,
    onDismiss: () -> Unit,
    onQueue: () -> Unit,
) {
    val t = state.tracks.find { it.id == p.currentId }
    var sleep by remember { mutableStateOf(false) }
    var audio by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf(false) }
    val sleepDeadline by vm.sleepDeadline.collectAsState()
    var lyricsTarget by remember { mutableStateOf<String?>(null) }
    val lyricFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val id = lyricsTarget
            if (uri != null && id != null) vm.importLyrics(id, uri)
        }
    Column(
        Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            IconAction(Icons.Rounded.KeyboardArrowDown, "Close player", onClick = onDismiss)
            Eyebrow(
                if (state.settings.privateSession) "Private listening"
                else "A moment for the music",
                Sage,
            )
            IconAction(Icons.Rounded.Info, "Audio information", onClick = { audio = true })
        }
        if (t != null) {
            Cover(t, Modifier.fillMaxWidth(.86f).widthIn(max = 360.dp), 22.dp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.headlineMedium)
                    Text(t.artist, color = Muted, style = MaterialTheme.typography.bodyLarge)
                }
                IconAction(
                    if (t.id in state.favorites) Icons.Rounded.Favorite
                    else Icons.Rounded.FavoriteBorder,
                    "Favorite current song",
                    Sage,
                    { vm.favorite(t.id) },
                )
            }
            var dragging by remember { mutableStateOf(false) }
            var seek by remember { mutableFloatStateOf(0f) }
            Column {
                Slider(
                    if (dragging) seek else p.positionMs.toFloat(),
                    {
                        dragging = true
                        seek = it
                    },
                    onValueChangeFinished = {
                        vm.playback.seek(seek.toLong())
                        dragging = false
                    },
                    valueRange = 0f..p.durationMs.coerceAtLeast(1).toFloat(),
                )
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(duration(p.positionMs), color = Muted)
                    Text(duration(p.durationMs), color = Muted)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(Icons.Rounded.Shuffle, "Shuffle all music", onClick = { vm.shuffle() })
                IconAction(
                    Icons.Rounded.SkipPrevious,
                    "Previous song",
                    onClick = vm.playback::previous,
                )
                FilledIconButton(vm.playback::toggle, Modifier.size(76.dp), shape = CircleShape) {
                    Icon(
                        if (p.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (p.playing) "Pause current song" else "Play current song",
                        Modifier.size(38.dp),
                    )
                }
                IconAction(Icons.Rounded.SkipNext, "Next song", onClick = vm.playback::next)
                IconAction(
                    if (p.repeat == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Repeat mode: ${when(p.repeat){1->"one"
2->"all"
else->"off"}}",
                    if (p.repeat == 0) Muted else Sage,
                    vm.playback::repeat,
                )
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                TextButton({ sleep = true }) {
                    Glyph(Icons.Rounded.Bedtime, "")
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (sleepDeadline > 0)
                            "Sleep · ${((sleepDeadline-android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0)+59999)/60000}m"
                        else "Sleep timer"
                    )
                }
                TextButton(onQueue) {
                    Glyph(Icons.Rounded.QueueMusic, "")
                    Spacer(Modifier.width(6.dp))
                    Text("Queue · ${p.ids.size}")
                }
            }
            TextButton({ lyrics = true }) {
                Glyph(Icons.Rounded.Notes, "")
                Spacer(Modifier.width(6.dp))
                Text("Lyrics")
            }
            Eyebrow(
                if (t.source.startsWith("Original")) "Original generated sound sketch"
                else "Playing from your local library"
            )
        } else {
            Text("The queue is empty.", style = MaterialTheme.typography.headlineMedium)
            Text("Choose a song from your library.", color = Muted)
        }
        Spacer(Modifier.height(20.dp))
    }
    if (sleep)
        AlertDialog(
            onDismissRequest = { sleep = false },
            title = { Text("Drift off in your own time.") },
            text = {
                Column {
                    listOf(15, 30, 45, 60, 90).forEach { minutes ->
                        TextButton({
                            vm.sleep(minutes)
                            sleep = false
                        }) {
                            Text("$minutes minutes")
                        }
                    }
                    TextButton({
                        vm.sleep(0)
                        sleep = false
                    }) {
                        Text("Cancel timer")
                    }
                }
            },
            confirmButton = { TextButton({ sleep = false }) { Text("Close") } },
        )
    if (audio && t != null)
        AlertDialog(
            onDismissRequest = { audio = false },
            title = { Text("What you're hearing") },
            text = {
                Text(
                    "Source: ${t.source}\nFile type: ${t.mime.ifBlank{"Unknown"}}\nDuration: ${duration(t.durationMs)}\n\nFile metadata is reported locally. Bitrate, bit depth, sample rate, mastering and output path have not been verified. No lossless or bit-perfect output claim is made.\n\nCover: original procedural artwork, not embedded album artwork."
                )
            },
            confirmButton = { TextButton({ audio = false }) { Text("Done") } },
        )
    if (lyrics && t != null) {
        val lines = remember(t.lyrics) { Lyrics.parse(t.lyrics) }
        val listState = rememberLazyListState()
        val active = lines.indexOfLast { it.atMs != null && it.atMs <= p.positionMs }
        LaunchedEffect(active) {
            if (active >= 0) listState.scrollToItem((active - 2).coerceAtLeast(0))
        }
        AlertDialog(
            onDismissRequest = { lyrics = false },
            title = { Text(t.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (t.lyrics.isBlank())
                            "No local lyrics yet. Add a plain text or timestamped LRC file you have permission to use. No external lyrics service is contacted."
                        else "Local lyrics · embedded ID3 or a file you selected",
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (lines.isNotEmpty())
                        LazyColumn(
                            Modifier.heightIn(max = 330.dp),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            itemsIndexed(lines) { index, line ->
                                Text(
                                    line.text,
                                    color = if (index == active) Sage else Cream,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier =
                                        Modifier.clickable {
                                            line.atMs?.let { vm.playback.seek(it) }
                                        },
                                )
                            }
                        }
                }
            },
            confirmButton = {
                TextButton({
                    lyricsTarget = t.id
                    lyricFile.launch(arrayOf("text/*", "application/octet-stream"))
                }) {
                    Text(if (lines.isEmpty()) "Add local lyrics" else "Replace lyrics")
                }
            },
            dismissButton = { TextButton({ lyrics = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun QueueScreen(
    vm: MainViewModel,
    state: LibraryState,
    p: PlaybackState,
    onDismiss: () -> Unit,
) {
    var clear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            Column {
                Text("The road ahead.", style = MaterialTheme.typography.headlineMedium)
                Text("${p.ids.size} songs in your queue", color = Muted)
            }
            TextButton({ clear = true }, enabled = p.ids.isNotEmpty()) { Text("Clear") }
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            itemsIndexed(p.ids, key = { index, id -> "$index:$id" }) { index, id ->
                val t = state.tracks.find { it.id == id }
                if (t != null) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (index == p.index) Panel else Ink)
                            .padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index+1}",
                            color = if (index == p.index) Sage else Muted,
                            modifier = Modifier.width(26.dp),
                        )
                        Column(
                            Modifier.weight(1f)
                                .clickable { vm.playback.jump(index) }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                t.title,
                                color = if (index == p.index) Sage else Cream,
                                maxLines = 2,
                            )
                            Text(
                                t.artist,
                                color = Muted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        IconAction(
                            Icons.Rounded.KeyboardArrowUp,
                            "Move queue item ${index+1} up",
                            onClick = { vm.playback.move(index, index - 1) },
                        )
                        IconAction(
                            Icons.Rounded.KeyboardArrowDown,
                            "Move queue item ${index+1} down",
                            onClick = { vm.playback.move(index, index + 1) },
                        )
                        IconAction(
                            Icons.Rounded.Close,
                            "Remove queue item ${index+1}",
                            onClick = { vm.playback.remove(index) },
                        )
                    }
                }
            }
        }
    }
    if (clear)
        AlertDialog(
            onDismissRequest = { clear = false },
            title = { Text("Clear the queue?") },
            text = { Text("Playback will stop. Your library and playlists stay.") },
            confirmButton = {
                TextButton({
                    vm.playback.clear()
                    clear = false
                    onDismiss()
                }) {
                    Text("Clear queue")
                }
            },
            dismissButton = { TextButton({ clear = false }) { Text("Cancel") } },
        )
}

@Composable
private fun TrackDetails(vm: MainViewModel, t: Track, onDismiss: () -> Unit) {
    var genre by remember(t.id) { mutableStateOf(t.genre) }
    var language by remember(t.id) { mutableStateOf(t.language) }
    var tags by remember(t.id) { mutableStateOf(t.tags.joinToString(", ")) }
    var year by remember(t.id) { mutableStateOf(t.year?.toString().orEmpty()) }
    var rating by remember(t.id) { mutableStateOf(t.explicit) }
    var remove by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.title) },
        text = {
            Column(
                Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "${t.artist}\n${t.album}\n${duration(t.durationMs)} · ${t.mime.ifBlank{"Unknown format"}}\n${t.source}",
                    color = Muted,
                )
                Text(
                    "Your edits stay in Songlane and do not rewrite the audio file.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(genre, { genre = it.take(100) }, label = { Text("Genre") })
                OutlinedTextField(
                    language,
                    { language = it.take(100) },
                    label = { Text("Language") },
                )
                OutlinedTextField(
                    tags,
                    { tags = it.take(500) },
                    label = { Text("Your tags, comma separated") },
                )
                OutlinedTextField(
                    year,
                    { year = it.filter(Char::isDigit).take(4) },
                    label = { Text("Year") },
                )
                Text("Content rating (your label)")
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(null to "Unknown", false to "Clean", true to "Explicit").forEach {
                        (value, label) ->
                        FilterChip(rating == value, { rating = value }, label = { Text(label) })
                    }
                }
                TextButton({ remove = true }) { Text("Remove from library", color = Coral) }
            }
        },
        confirmButton = {
            TextButton({
                vm.action {
                    vm.repo.editTrack(
                        t.copy(
                            genre = genre.trim(),
                            language = language.trim(),
                            tags =
                                tags
                                    .split(',')
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .toSet(),
                            year = year.toIntOrNull(),
                            explicit = rating,
                        )
                    )
                }
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
    if (remove)
        AlertDialog(
            onDismissRequest = { remove = false },
            title = { Text("Remove ${t.title}?") },
            text = {
                Text(
                    "The original audio file stays. The song is removed from Songlane playlists and queue."
                )
            },
            confirmButton = {
                TextButton({
                    val indexes =
                        vm.playback.state.value.ids
                            .withIndex()
                            .filter { it.value == t.id }
                            .map { it.index }
                            .reversed()
                    indexes.forEach { vm.playback.remove(it) }
                    vm.action { vm.repo.removeTrack(t.id) }
                    remove = false
                    onDismiss()
                }) {
                    Text("Remove")
                }
            },
            dismissButton = { TextButton({ remove = false }) { Text("Cancel") } },
        )
}
