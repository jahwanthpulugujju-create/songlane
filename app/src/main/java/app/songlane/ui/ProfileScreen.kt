package app.songlane.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.songlane.MainViewModel
import app.songlane.domain.*

@Composable
fun ProfileScreen(
    vm: MainViewModel,
    state: LibraryState,
    onSettings: () -> Unit,
    onExport: () -> Unit,
) {
    var genres by
        rememberSaveable(state.settings.preferredGenres) {
            mutableStateOf(state.settings.preferredGenres.joinToString(", "))
        }
    var avoided by
        rememberSaveable(state.settings.avoidedArtists) {
            mutableStateOf(state.settings.avoidedArtists.joinToString(", "))
        }
    val artists =
        state.tracks
            .filter { it.id in state.favorites }
            .groupingBy { it.artist }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp, 14.dp, 24.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Eyebrow("A collection of you", Sage)
            Spacer(Modifier.height(10.dp))
            Text("Your taste,\nyour terms.", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(10.dp))
            Text("A portable profile. An open road.", color = Muted)
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(22.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    listOf(
                            state.tracks.size.toString() to "SONGS",
                            state.favorites.size.toString() to "FAVORITES",
                            state.history.map { it.trackId }.distinct().size.toString() to "HEARD",
                        )
                        .forEach { (count, label) ->
                            Column {
                                Text(
                                    count,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = Sage,
                                )
                                Eyebrow(label)
                            }
                        }
                }
            }
        }
        item {
            SectionTitle("Make it yours")
            Text(
                "These preferences guide local discovery. Edit them whenever your taste changes.",
                color = Muted,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                genres,
                { genres = it.take(500) },
                Modifier.fillMaxWidth(),
                label = { Text("Preferred genres, comma separated") },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                avoided,
                { avoided = it.take(1000) },
                Modifier.fillMaxWidth(),
                label = { Text("Artists to avoid, comma separated") },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton({
                vm.action {
                    vm.repo.settings {
                        it.copy(
                            preferredGenres =
                                genres
                                    .split(',')
                                    .map { g -> g.trim() }
                                    .filter { g -> g.isNotBlank() }
                                    .toSet(),
                            avoidedArtists =
                                avoided
                                    .split(',')
                                    .map { g -> g.trim() }
                                    .filter { g -> g.isNotBlank() }
                                    .toSet(),
                        )
                    }
                    vm.notify("Taste preferences updated")
                }
            }) {
                Text("Save preferences")
            }
        }
        if (artists.isNotEmpty()) {
            item {
                SectionTitle("Artists you come back to")
                Text(
                    "Based on your explicit favorites.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(artists, key = { it.key }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.key) },
                    supportingContent = { Text("${entry.value} liked songs") },
                    leadingContent = { Glyph(Icons.Rounded.Favorite, entry.key, tint = Sage) },
                    colors = ListItemDefaults.colors(containerColor = Panel),
                )
            }
        }
        item {
            InfoCard(
                "Taste Passport",
                "Export your favorites, playlists, metadata, preferences and local listening history. Your audio and API keys are excluded.",
                Icons.Rounded.Public,
            )
            Spacer(Modifier.height(12.dp))
            Button(onExport, Modifier.fillMaxWidth()) { Text("Export Taste Passport") }
        }
        item {
            SettingToggle(
                "Private session",
                "Listening won't update your history or taste. The saved queue is cleared while private.",
                state.settings.privateSession,
                { vm.action { vm.repo.settings { s -> s.copy(privateSession = it) } } },
            )
        }
        item {
            OutlinedButton(onSettings, Modifier.fillMaxWidth()) {
                Glyph(Icons.Rounded.Settings, "")
                Spacer(Modifier.width(8.dp))
                Text("Settings & privacy")
            }
        }
    }
}

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    state: LibraryState,
    onBack: () -> Unit,
    onFiles: () -> Unit,
    onFolder: () -> Unit,
    onScan: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var clearHistory by remember { mutableStateOf(false) }
    var erase by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    val saved by vm.keySaved.collectAsState()
    val context = LocalContext.current
    var speed by
        rememberSaveable(state.settings.speed) { mutableFloatStateOf(state.settings.speed) }
    var pitch by
        rememberSaveable(state.settings.pitch) { mutableFloatStateOf(state.settings.pitch) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp, 8.dp, 24.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextButton(onBack) {
                Glyph(Icons.Rounded.ArrowBack, "")
                Text("Back")
            }
            Text("The way you listen.", style = MaterialTheme.typography.headlineLarge)
        }
        item {
            SectionTitle("Your sources")
            InfoCard(
                "Local files",
                "Audio stays on your device. Pick files or a folder, or grant access to the device music library.",
                Icons.Rounded.FolderOpen,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onFiles) { Text("Add files") }
                TextButton(onFolder) { Text("Add folder") }
                TextButton(onScan) { Text("Scan device") }
            }
        }
        if (state.settings.folderUris.isNotEmpty()) {
            item {
                TextButton(vm::rescan) {
                    Glyph(Icons.Rounded.Refresh, "")
                    Text("Rescan selected folders")
                }
            }
            items(state.settings.folderUris.toList()) { folder ->
                ListItem(
                    headlineContent = {
                        Text(Uri.decode(folder.substringAfterLast('/')), maxLines = 2)
                    },
                    supportingContent = { Text("Selected folder") },
                    trailingContent = {
                        IconAction(
                            Icons.Rounded.Close,
                            "Stop scanning this folder",
                            onClick = {
                                vm.action {
                                    vm.repo.forgetFolder(folder)
                                    runCatching {
                                        context.contentResolver.releasePersistableUriPermission(
                                            Uri.parse(folder),
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                        )
                                    }
                                }
                            },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Panel),
                )
            }
        }
        item {
            SectionTitle("Playback")
            Text("Fair shuffle", style = MaterialTheme.typography.titleMedium)
            state.settings.shuffle.let { selected ->
                ShuffleMode.entries.forEach { mode ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            mode == selected,
                            { vm.action { vm.repo.settings { it.copy(shuffle = mode) } } },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(mode.label)
                            Text(
                                mode.description,
                                color = Muted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            Text("Playback speed · ${"%.2f".format(speed)}×")
            Slider(
                speed,
                { speed = it },
                onValueChangeFinished = {
                    vm.action { vm.repo.settings { it.copy(speed = speed) } }
                },
                valueRange = .5f..2f,
                steps = 5,
            )
            Text("Pitch · ${"%.2f".format(pitch)}×")
            Slider(
                pitch,
                { pitch = it },
                onValueChangeFinished = {
                    vm.action { vm.repo.settings { it.copy(pitch = pitch) } }
                },
                valueRange = .5f..2f,
                steps = 5,
            )
            Text(
                "Changing speed or pitch uses local audio processing and can affect sound quality.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton({
                speed = 1f
                pitch = 1f
                vm.action { vm.repo.settings { it.copy(speed = 1f, pitch = 1f) } }
            }) {
                Text("Reset speed & pitch")
            }
            SettingToggle(
                "Skip silence",
                "Media3 detects silent frames locally. This may change the pacing of music.",
                state.settings.skipSilence,
                { vm.action { vm.repo.settings { s -> s.copy(skipSilence = it) } } },
            )
        }
        item {
            SectionTitle("Privacy & data")
            SettingToggle(
                "Private session",
                "No history or taste learning. No saved queue while private.",
                state.settings.privateSession,
                { vm.action { vm.repo.settings { s -> s.copy(privateSession = it) } } },
            )
            SettingToggle(
                "Record listening history",
                "On-device only. Listens count after 10 seconds of active playback.",
                state.settings.historyEnabled,
                { vm.action { vm.repo.settings { s -> s.copy(historyEnabled = it) } } },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onExport) { Text("Export passport") }
                OutlinedButton(onImport) { Text("Import passport") }
            }
            Text(
                "Export includes listening history and may reveal personal preferences. Import merges matching local songs and playlists; it cannot import audio or grant file access.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton({ clearHistory = true }) { Text("Clear listening history") }
        }
        item {
            SectionTitle("Optional music agent")
            InfoCard(
                "Useful without a key",
                "Local Session Director and Sonic Compass work offline. Cloud mode only parses the text you choose to send. No shared or bundled key.",
                Icons.Rounded.AutoAwesome,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (saved) "An encrypted Anthropic key is stored." else "No cloud key stored.",
                color = if (saved) Sage else Muted,
            )
            OutlinedTextField(
                key,
                { key = it.take(512) },
                Modifier.fillMaxWidth(),
                label = { Text("Anthropic API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                    ),
            )
            Row {
                TextButton(
                    {
                        vm.saveKey(key.trim())
                        key = ""
                    },
                    enabled = key.isNotBlank(),
                ) {
                    Text("Save key")
                }
                if (saved) TextButton(vm::deleteKey) { Text("Delete key") }
            }
            Text(
                "Keys are encrypted with Android Keystore and excluded from backup/export. Cloud requests require per-session consent. Limit: 10 requests/day, 256 output tokens/request. These are usage limits, not a guaranteed currency cap; set billing limits with your provider.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            SectionTitle("About Songlane")
            Text(
                "Version 0.1.0 · An independent, open-source local music player. No ads. No analytics. No streaming catalog or provider subscription included.",
                color = Muted,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Original interface and procedural artwork. Optional sound sketches are generated oscillator compositions. AndroidX/Compose/Media3 are Apache-2.0 components; Kotlin and coroutines are Apache-2.0. Full notices and dependency inventory are included with the source.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            TextButton({ erase = true }) { Text("Delete all Songlane data", color = Coral) }
        }
    }
    if (clearHistory)
        AlertDialog(
            onDismissRequest = { clearHistory = false },
            title = { Text("Clear listening history?") },
            text = { Text("Favorites, playlists and files will stay. This cannot be undone.") },
            confirmButton = {
                TextButton({
                    vm.action { vm.repo.clearHistory() }
                    clearHistory = false
                }) {
                    Text("Clear history")
                }
            },
            dismissButton = { TextButton({ clearHistory = false }) { Text("Cancel") } },
        )
    if (erase)
        AlertDialog(
            onDismissRequest = { erase = false },
            title = { Text("Start fresh?") },
            text = {
                Text(
                    "This deletes Songlane's library index, playlists, favorites, history, preferences, saved queue and local API key, and releases folder access. Your original music files will stay."
                )
            },
            confirmButton = {
                TextButton({
                    vm.eraseAll()
                    erase = false
                }) {
                    Text("Delete data", color = Coral)
                }
            },
            dismissButton = { TextButton({ erase = false }) { Text("Cancel") } },
        )
}
