package app.songlane.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import app.songlane.domain.Lyrics
import app.songlane.domain.ReplayGainReader
import app.songlane.domain.Track
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportResult(val tracks: List<Track>, val failed: Int, val note: String = "")

class AudioImporter(private val context: Context) {
    suspend fun verifyAvailability(tracks: List<Track>): List<Track> =
        withContext(Dispatchers.IO) {
            tracks.map { t ->
                t.copy(
                    available =
                        runCatching {
                                context.contentResolver
                                    .openAssetFileDescriptor(Uri.parse(t.uri), "r")
                                    ?.use { true } ?: false
                            }
                            .getOrDefault(false)
                )
            }
        }

    private fun id(uri: String) =
        MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)

    private fun read(uri: Uri, name: String? = null): Track? = runCatching {
        MediaMetadataRetriever().let { reader ->
            try {
                reader.setDataSource(context, uri)
                fun tag(key: Int) = reader.extractMetadata(key)?.trim().orEmpty()
                val ms =
                    tag(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull()
                        ?: return@let null
                if (ms <= 0) return@let null
                Track(
                    id(uri.toString()),
                    uri.toString(),
                    tag(MediaMetadataRetriever.METADATA_KEY_TITLE).ifBlank {
                        name?.substringBeforeLast('.') ?: "Untitled audio"
                    },
                    tag(MediaMetadataRetriever.METADATA_KEY_ARTIST).ifBlank { "Unknown artist" },
                    tag(MediaMetadataRetriever.METADATA_KEY_ALBUM).ifBlank { "Unknown album" },
                    ms,
                    tag(MediaMetadataRetriever.METADATA_KEY_GENRE),
                    tag(MediaMetadataRetriever.METADATA_KEY_YEAR).take(4).toIntOrNull(),
                    mime = tag(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
                    replayGain = runCatching {
                            context.contentResolver
                                .openInputStream(uri)
                                ?.use(ReplayGainReader::read)
                        }
                            .getOrNull(),
                    lyrics =
                        runCatching {
                                context.contentResolver
                                    .openInputStream(uri)
                                    ?.use { Lyrics.embedded(it) }
                                    .orEmpty()
                            }
                            .getOrDefault(""),
                )
            } finally {
                reader.release()
            }
        }
    }
        .getOrNull()

    suspend fun files(uris: List<Uri>): ImportResult =
        withContext(Dispatchers.IO) {
            val tracks = uris.mapNotNull { read(it, DocumentFile.fromSingleUri(context, it)?.name) }
            ImportResult(tracks, uris.size - tracks.size)
        }

    suspend fun folder(uri: Uri): ImportResult =
        withContext(Dispatchers.IO) {
            val root =
                DocumentFile.fromTreeUri(context, uri) ?: error("Folder is no longer available")
            val files = mutableListOf<DocumentFile>()
            var visited = 0
            fun walk(dir: DocumentFile, depth: Int) {
                if (depth > 12 || visited >= 10000) return
                dir.listFiles().forEach { file ->
                    if (visited++ >= 10000) return@forEach
                    if (file.isDirectory) walk(file, depth + 1)
                    else if (
                        file.type?.startsWith("audio/") == true ||
                            file.name?.substringAfterLast('.')?.lowercase() in
                                setOf("mp3", "flac", "wav", "ogg", "opus", "m4a", "aac")
                    )
                        files += file
                }
            }
            walk(root, 0)
            val tracks = files.mapNotNull { read(it.uri, it.name) }
            ImportResult(
                tracks,
                files.size - tracks.size,
                if (visited >= 10000) "Folder scan limited to 10,000 entries." else "",
            )
        }

    suspend fun mediaStore(): ImportResult =
        withContext(Dispatchers.IO) {
            val uris = mutableListOf<Pair<Uri, String>>()
            context.contentResolver
                .query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME),
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    "${MediaStore.Audio.Media.TITLE} ASC",
                )
                ?.use { cursor ->
                    while (cursor.moveToNext()) uris +=
                        ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            cursor.getLong(0),
                        ) to cursor.getString(1)
                }
            val tracks = uris.mapNotNull { read(it.first, it.second) }
            ImportResult(tracks, uris.size - tracks.size)
        }

    suspend fun demos(): ImportResult =
        withContext(Dispatchers.IO) {
            val names =
                listOf(
                    "Mosslight",
                    "Soft Geometry",
                    "Small Hours",
                    "Tidal Memory",
                    "Paper Satellites",
                    "Sunroom",
                    "After the Rain",
                    "Velvet Circuit",
                )
            val genres =
                listOf(
                    "Ambient",
                    "Electronic",
                    "Ambient",
                    "Electronic",
                    "Electronic",
                    "Ambient",
                    "Ambient",
                    "Electronic",
                )
            val artists =
                listOf(
                    "Songlane Studio",
                    "Songlane Signals",
                    "Songlane Studio",
                    "Songlane Signals",
                    "Songlane Signals",
                    "Songlane Studio",
                    "Songlane Studio",
                    "Songlane Signals",
                )
            val tracks = names.mapIndexed { i, name ->
                val file = File(context.filesDir, "demo-$i.wav")
                if (!file.exists())
                    context.assets.open("demo/$i.wav").use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                val uri = Uri.fromFile(file)
                Track(
                    id(uri.toString()),
                    uri.toString(),
                    name,
                    artists[i],
                    "A place to begin",
                    45000,
                    genres[i],
                    2026,
                    "Instrumental",
                    false,
                    "audio/wav",
                    setOf(if (i % 2 == 0) "calm" else "bright", "instrumental"),
                    source = "Original generated demo · Songlane",
                )
            }
            ImportResult(tracks, 0)
        }
}
