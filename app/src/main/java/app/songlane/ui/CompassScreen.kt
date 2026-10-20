package app.songlane.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.songlane.MainViewModel
import app.songlane.agent.*
import app.songlane.domain.*

@Composable
fun CompassScreen(vm: MainViewModel, state: LibraryState, busy: Boolean, onImport: () -> Unit) {
    var anchorId by rememberSaveable { mutableStateOf<String?>(null) }
    var destination by rememberSaveable { mutableStateOf("") }
    var novelty by rememberSaveable { mutableFloatStateOf(60f) }
    var minutes by rememberSaveable { mutableFloatStateOf(30f) }
    var recentDays by rememberSaveable { mutableIntStateOf(0) }
    var excludeExplicit by rememberSaveable { mutableStateOf(false) }
    var languages by rememberSaveable { mutableStateOf("") }
    var minYear by rememberSaveable { mutableStateOf("") }
    var maxYear by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable { mutableStateOf(false) }
    var pickAnchor by remember { mutableStateOf(false) }
    var agent by remember { mutableStateOf(false) }
    var save by remember { mutableStateOf(false) }
    var routeName by remember { mutableStateOf("") }
    val route by vm.route.collectAsState()
    val intent by vm.parsedIntent.collectAsState()
    val anchor = state.tracks.find { it.id == anchorId }
    LaunchedEffect(intent) {
        intent?.let {
            destination = it.destination
            novelty = it.novelty.toFloat()
            minutes = it.minutes.toFloat()
            recentDays = it.recentDays
            if (it.privateSession)
                vm.action { vm.repo.settings { s -> s.copy(privateSession = true) } }
            vm.parsedIntent.value = null
            agent = false
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp, 14.dp, 24.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Eyebrow("Take the scenic route", Sage)
            Spacer(Modifier.height(10.dp))
            Text("Sonic Compass", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Start with something you love.\nSee where the thread takes you.",
                color = Muted,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Eyebrow("01 / Your starting point")
                    OutlinedButton(
                        { pickAnchor = true },
                        Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    ) {
                        Glyph(Icons.Rounded.MusicNote, "")
                        Spacer(Modifier.width(8.dp))
                        Text(anchor?.title ?: "Choose a song, or surprise me", maxLines = 2)
                    }
                    Eyebrow("02 / A direction to explore")
                    OutlinedTextField(
                        destination,
                        { destination = it.take(200) },
                        Modifier.fillMaxWidth(),
                        placeholder = { Text("A genre, artist, or your own tags") },
                        label = { Text("Destination") },
                        singleLine = true,
                    )
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("How far from familiar?")
                        Text("${novelty.toInt()}%", color = Sage)
                    }
                    Slider(
                        novelty,
                        { novelty = it },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(
                            "Comfort zone",
                            color = Muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "New territory",
                            color = Muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically,
                    ) {
                        Text("Up to ${minutes.toInt()} minutes")
                        IconAction(
                            Icons.Rounded.Tune,
                            "Show route filters",
                            onClick = { filters = !filters },
                        )
                    }
                    Slider(minutes, { minutes = it }, valueRange = 1f..120f, steps = 118)
                    if (filters) {
                        Text(
                            "Exclude recent listening",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0, 7, 30, 90).forEach { days ->
                                FilterChip(
                                    recentDays == days,
                                    { recentDays = days },
                                    label = { Text(if (days == 0) "Off" else "${days}d") },
                                )
                            }
                        }
                        SettingToggle(
                            "Clean content only",
                            "Unknown ratings are excluded too. Set ratings in song details.",
                            excludeExplicit,
                            { excludeExplicit = it },
                        )
                        OutlinedTextField(
                            languages,
                            { languages = it.take(100) },
                            Modifier.fillMaxWidth(),
                            label = { Text("Languages, comma separated") },
                            placeholder = { Text("Leave empty for all") },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                minYear,
                                { minYear = it.filter(Char::isDigit).take(4) },
                                Modifier.weight(1f),
                                label = { Text("From year") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                maxYear,
                                { maxYear = it.filter(Char::isDigit).take(4) },
                                Modifier.weight(1f),
                                label = { Text("To year") },
                                singleLine = true,
                            )
                        }
                        Text(
                            "One song per artist in a row. No duplicate songs. Only your available local files. Strict filters exclude unknown metadata.",
                            color = Muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Button(
                        {
                            val from = minYear.toIntOrNull()
                            val to = maxYear.toIntOrNull()
                            if (from != null && to != null && from > to)
                                vm.notify("The start year must be before the end year.")
                            else
                                vm.buildRoute(
                                    RouteRequest(
                                        anchorId,
                                        destination,
                                        novelty.toInt(),
                                        Constraints(
                                            minutes.toLong() * 60000,
                                            recentDays,
                                            1,
                                            excludeExplicit,
                                            languages
                                                .split(',')
                                                .map { it.trim() }
                                                .filter { it.isNotEmpty() }
                                                .toSet(),
                                            from,
                                            to,
                                        ),
                                        seed = (System.nanoTime() % Int.MAX_VALUE).toInt(),
                                    )
                                )
                        },
                        Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        enabled = state.tracks.isNotEmpty() && !busy,
                    ) {
                        Glyph(Icons.Rounded.Explore, "")
                        Spacer(Modifier.width(8.dp))
                        Text("Find my route")
                    }
                    TextButton({ agent = true }, Modifier.fillMaxWidth()) {
                        Glyph(Icons.Rounded.AutoAwesome, "")
                        Spacer(Modifier.width(8.dp))
                        Text("Describe a session instead")
                    }
                }
            }
        }
        if (state.tracks.isEmpty())
            item {
                InfoCard(
                    "A route needs a starting collection",
                    "Add local songs or try the sound sketches from Listen.",
                )
                TextButton(onImport) { Text("Add music") }
            }
        if (route != null) {
            val r = route!!
            item {
                SectionTitle("Your listening route")
                Text(
                    "${r.steps.size} stops · ${duration(r.durationMs)} · local library",
                    color = Muted,
                )
                if (r.steps.isNotEmpty())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(vm::playRoute, Modifier.weight(1f)) {
                            Glyph(Icons.Rounded.PlayArrow, "")
                            Text("Play route")
                        }
                        OutlinedButton(
                            {
                                routeName = "${destination.ifBlank{"A new direction"}} · route"
                                save = true
                            },
                            Modifier.weight(1f),
                        ) {
                            Text("Save route")
                        }
                    }
            }
            r.warnings.forEach { warning -> item { InfoCard("About this route", warning) } }
            itemsIndexed(r.steps, key = { _, step -> step.track.id }) { index, step ->
                var menu by remember { mutableStateOf(false) }
                Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "%02d".format(index + 1),
                                color = Sage,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.width(44.dp),
                            )
                            Cover(step.track, Modifier.size(46.dp), 8.dp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(step.track.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    step.track.artist,
                                    color = Muted,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Box {
                                IconAction(
                                    Icons.Rounded.MoreVert,
                                    "Route feedback for ${step.track.title}",
                                    onClick = { menu = true },
                                )
                                DropdownMenu(menu, { menu = false }) {
                                    listOf(
                                            "More like this",
                                            "Less like this",
                                            "Too familiar",
                                            "Too abrupt",
                                            "Avoid artist",
                                        )
                                        .forEach { feedback ->
                                            DropdownMenuItem(
                                                text = { Text(feedback) },
                                                onClick = {
                                                    menu = false
                                                    vm.branchRoute(step.track, feedback)
                                                },
                                            )
                                        }
                                }
                            }
                        }
                        Text(
                            step.reason,
                            color = Cream,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            step.evidence,
                            color = Muted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        } else
            item {
                InfoCard(
                    "A connection you can understand",
                    "Routes use your file tags and recorded listening. Every stop includes its reason. No hidden model is required.",
                    Icons.Rounded.Route,
                )
            }
    }
    if (pickAnchor)
        AlertDialog(
            onDismissRequest = { pickAnchor = false },
            title = { Text("Where shall we start?") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    TextButton({
                        anchorId = null
                        pickAnchor = false
                    }) {
                        Text("Surprise me")
                    }
                    state.tracks.forEach { t ->
                        ListItem(
                            headlineContent = { Text(t.title) },
                            supportingContent = { Text(t.artist) },
                            modifier =
                                Modifier.clickable {
                                    anchorId = t.id
                                    pickAnchor = false
                                },
                            colors = ListItemDefaults.colors(containerColor = Panel),
                        )
                    }
                }
            },
            confirmButton = { TextButton({ pickAnchor = false }) { Text("Close") } },
        )
    if (save)
        AlertDialog(
            onDismissRequest = { save = false },
            title = { Text("Keep this route") },
            text = {
                OutlinedTextField(
                    routeName,
                    { routeName = it.take(100) },
                    label = { Text("Playlist name") },
                )
            },
            confirmButton = {
                TextButton(
                    {
                        vm.action {
                            vm.repo.createPlaylist(routeName, route!!.steps.map { it.track.id })
                        }
                        save = false
                    },
                    enabled = routeName.isNotBlank(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton({ save = false }) { Text("Cancel") } },
        )
    if (agent)
        AgentDialog(
            vm,
            busy,
            {
                vm.cancelIntent()
                agent = false
            },
        )
}

@Composable
private fun AgentDialog(vm: MainViewModel, busy: Boolean, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var cloud by rememberSaveable { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var modelId by rememberSaveable { mutableStateOf(IntentModels.all.first().id) }
    val model = IntentModels.find(modelId)
    val savedProviders by vm.savedProviders.collectAsState()
    val keySaved = model.provider in savedProviders
    val cloudState by vm.cloud.state.collectAsState()
    val error by vm.lastError.collectAsState()
    LaunchedEffect(text, modelId, savedProviders) { consent = false }
    LaunchedEffect(Unit) {
        vm.cloud.refresh()
        vm.lastError.value = null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Session Director") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Try “30 minutes of ambient, explore unheard songs, private session.” Review the route settings before playing.",
                    color = Muted,
                )
                OutlinedTextField(
                    text,
                    {
                        text = it.take(2000)
                        consent = false
                    },
                    Modifier.fillMaxWidth(),
                    label = { Text("What are you in the mood for?") },
                    minLines = 3,
                    enabled = !busy,
                )
                SettingToggle(
                    "Use cloud intent parsing",
                    "Optional · ${cloudState.usage.callsToday}/10 requests today (UTC)",
                    cloud,
                    {
                        cloud = it
                        consent = false
                    },
                )
                if (cloud) {
                    if (!keySaved) Text("Add an API key in Settings first.", color = Coral)
                    IntentModels.all.forEach { choice ->
                        OutlinedButton(
                            {
                                modelId = choice.id
                                consent = false
                            },
                            Modifier.fillMaxWidth(),
                            enabled = !busy,
                        ) {
                            Text(
                                "${if (choice.id == modelId) "✓ " else ""}${choice.provider.label} · ${choice.label}"
                            )
                        }
                    }
                    Text(
                        "Only the prompt above and fixed parsing instructions go to ${model.provider.host}. Your key is sent only in its authorization header. Music, history and profile stay here. Max 256 output tokens; no tools or automatic retries.",
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Reservation: ${usd(model.reservationNanoUsd)}\nToday counted: ${usd(cloudState.usage.todayNanoUsd)} / ${usd(cloudState.limits.dailyNanoUsd)}\nMonth counted: ${usd(cloudState.usage.monthNanoUsd)} / ${usd(cloudState.limits.monthlyNanoUsd)}",
                        color = Sage,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Valid usage reduces the reservation; a timeout or cancellation keeps it. Rates reviewed ${IntentModels.reviewedOn}. Provider charges, taxes and usage elsewhere can differ; use provider billing controls too. Review every returned setting before playing.",
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(consent, { consent = it }, enabled = !busy)
                        Text(
                            "I consent to sending this prompt.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else
                    Text(
                        "Local mode recognizes duration, known genre/tags, familiarity, private sessions and recent-day exclusions. It does not measure mood or energy.",
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                error?.let { Text(it, color = Coral) }
            }
        },
        confirmButton = {
            TextButton(
                { vm.parseIntent(text, cloud, modelId, consent) },
                enabled = !busy && text.isNotBlank() && (!cloud || (consent && keySaved)),
            ) {
                Text(if (busy) "Parsing…" else "Apply to Compass")
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
