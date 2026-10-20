package app.songlane.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.songlane.MainViewModel
import app.songlane.domain.RecordingMetadata
import app.songlane.domain.Track
import app.songlane.domain.duration

@Composable
fun RecordingMetadataDialog(vm: MainViewModel, track: Track, onDismiss: () -> Unit) {
    val state by vm.metadataSearch.collectAsState()
    val library by vm.library.collectAsState()
    var title by rememberSaveable(track.id) { mutableStateOf(track.title.take(200)) }
    var artist by rememberSaveable(track.id) { mutableStateOf(track.artist.take(200)) }
    var contact by rememberSaveable { mutableStateOf(library.settings.metadataContact) }
    var consent by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val current = state.takeIf { it.trackId == track.id }
    fun dismiss() {
        vm.closeMetadata()
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text("Find the recording") },
        text = {
            Column(
                Modifier.heightIn(max = 510.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "MusicBrainz supplies recording details, not audio. Your title and artist search are sent only when you choose Search. No history, audio, keys or private-session state is sent.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    title,
                    { title = it.take(200) },
                    label = { Text("Recording title") },
                    singleLine = true,
                )
                OutlinedTextField(
                    artist,
                    { artist = it.take(200) },
                    label = { Text("Artist") },
                    singleLine = true,
                )
                OutlinedTextField(
                    contact,
                    { contact = it.take(200) },
                    label = { Text("Contact email or HTTPS project URL") },
                    singleLine = true,
                )
                Text(
                    "MusicBrainz requires a contact in the app's User-Agent. This contact is stored locally and sent with these metadata requests. Non-commercial API use; core data is CC0.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                SettingToggle(
                    "Allow this lookup session",
                    "Send this search and any recording IDs you choose to musicbrainz.org.",
                    consent,
                    { consent = it },
                )
                Button(
                    {
                        searched = true
                        vm.searchMetadata(
                            track.id,
                            title.trim(),
                            artist.trim(),
                            contact.trim(),
                            consent,
                        )
                    },
                    enabled = consent && title.isNotBlank() && current?.loading != true,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Search recordings")
                }
                if (current?.loading == true) LinearProgressIndicator(Modifier.fillMaxWidth())
                current?.error?.let { Text(it, color = Coral) }
                val detail = current?.detail
                if (detail != null) {
                    HorizontalDivider()
                    RecordingMetadataCard(detail)
                    Text(
                        "Confirm only if this is the same performance/version as your file. Similar titles can identify different live takes, remasters or covers. This links metadata; it does not fingerprint or merge your audio.",
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        {
                            vm.confirmMetadata(track.id)
                            onDismiss()
                        },
                        Modifier.fillMaxWidth(),
                        enabled = consent && !current.loading,
                    ) {
                        Text("Link this recording")
                    }
                } else {
                    if (
                        searched &&
                            current?.loading == false &&
                            current.error == null &&
                            current.results.isEmpty()
                    )
                        Text(
                            "No matching recordings. Edit the search, or keep your local tags.",
                            color = Muted,
                        )
                    current?.results?.forEach { candidate ->
                        OutlinedCard(
                            onClick = {
                                vm.lookupMetadata(
                                    track.id,
                                    candidate.recordingId,
                                    contact.trim(),
                                    consent,
                                )
                            },
                            enabled = consent && !current.loading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(candidate.title, style = MaterialTheme.typography.titleMedium)
                                Text(candidate.artistCredit, color = Muted)
                                Text(
                                    candidate.durationMs?.let(::duration) ?: "Duration unknown",
                                    color = Muted,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (candidate.disambiguation.isNotBlank())
                                    Text(
                                        candidate.disambiguation,
                                        color = Coral,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                Text(
                                    "Review this recording",
                                    color = Sage,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(::dismiss) { Text("Close") } },
    )
}

@Composable
fun RecordingMetadataCard(metadata: RecordingMetadata) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow("MusicBrainz · CC0", Sage)
        Text(metadata.title, style = MaterialTheme.typography.titleMedium)
        Text(metadata.artistCredit, color = Muted)
        if (metadata.disambiguation.isNotBlank()) Text(metadata.disambiguation, color = Coral)
        Text(
            "Recording duration: ${metadata.durationMs?.let(::duration) ?: "Unknown"}",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        metadata.contributors.take(16).forEach {
            Text("${it.role}: ${it.name}", style = MaterialTheme.typography.bodyMedium)
        }
        if (metadata.contributors.size > 16)
            Text("More credits are available on MusicBrainz.", color = Muted)
        Text(
            if (metadata.confirmedAt != null)
                "Recording identity linked by you. Community metadata can contain errors."
            else "Candidate recording · not linked to your file yet",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton({
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(metadata.sourceUrl)))
        }) {
            Text("View source on MusicBrainz")
        }
    }
}
