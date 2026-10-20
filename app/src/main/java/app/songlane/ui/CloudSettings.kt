package app.songlane.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.songlane.MainViewModel
import app.songlane.agent.*

@Composable
fun CloudSettingsSummary(vm: MainViewModel) {
    val state by vm.cloud.state.collectAsState()
    val keys by vm.savedProviders.collectAsState()
    var open by remember { mutableStateOf(false) }
    Text(
        "${keys.size} provider keys · ${state.usage.callsToday}/10 requests today (UTC)",
        color = Muted,
    )
    Text(
        "Counted today: ${usd(state.usage.todayNanoUsd)} of ${usd(state.limits.dailyNanoUsd)}",
        style = MaterialTheme.typography.bodyMedium,
    )
    TextButton({ open = true }) { Text("Manage cloud options") }
    if (open) CloudSettingsDialog(vm) { open = false }
}

@Composable
fun CloudSettingsDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val state by vm.cloud.state.collectAsState()
    val keys by vm.savedProviders.collectAsState()
    var providerName by rememberSaveable { mutableStateOf(AiProvider.ANTHROPIC.name) }
    val provider = AiProvider.valueOf(providerName)
    var key by remember(providerName) { mutableStateOf("") }
    var daily by rememberSaveable { mutableStateOf(usdInput(state.limits.dailyNanoUsd)) }
    var monthly by rememberSaveable { mutableStateOf(usdInput(state.limits.monthlyNanoUsd)) }
    var timeout by rememberSaveable { mutableIntStateOf(state.limits.timeoutSeconds) }
    val context = LocalContext.current
    val error by vm.lastError.collectAsState()
    LaunchedEffect(Unit) { vm.cloud.refresh() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cloud options") },
        text = {
            Column(
                Modifier.heightIn(max = 510.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Optional, paid API access. Local listening and Session Director work without a key.",
                    color = Muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiProvider.entries.forEach { choice ->
                        FilterChip(
                            provider == choice,
                            { providerName = choice.name },
                            { Text(choice.label) },
                        )
                    }
                }
                Text(
                    if (provider in keys) "Encrypted ${provider.label} key stored."
                    else "No ${provider.label} key stored.",
                    color = if (provider in keys) Sage else Muted,
                )
                OutlinedTextField(
                    key,
                    { key = it.take(512) },
                    Modifier.fillMaxWidth(),
                    label = { Text("${provider.label} API key") },
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
                            vm.saveKey(key.trim(), provider)
                            key = ""
                        },
                        enabled = key.isNotBlank(),
                    ) {
                        Text("Save key")
                    }
                    if (provider in keys)
                        TextButton({ vm.deleteKey(provider) }) { Text("Delete key") }
                }
                Text(
                    "Saving checks format and encrypts on this device; it sends no validation request. Your first consented parse verifies access. Deleting here does not revoke the key at its provider.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton({
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.keysUrl)))
                }) {
                    Text("Manage or revoke at ${provider.label}")
                }
                HorizontalDivider()
                Eyebrow("Local spending ceilings · USD")
                Text(
                    "Shared across both providers on this installation. UTC day and calendar month. Set either ceiling to 0 to stop cloud requests.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    daily,
                    { daily = it.take(12) },
                    Modifier.fillMaxWidth(),
                    label = { Text("Daily ceiling (USD)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    monthly,
                    { monthly = it.take(12) },
                    Modifier.fillMaxWidth(),
                    label = { Text("Monthly ceiling (USD)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text("Request timeout · no automatic retries")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60).forEach { seconds ->
                        FilterChip(
                            timeout == seconds,
                            { timeout = seconds },
                            { Text("${seconds}s") },
                        )
                    }
                }
                OutlinedButton(
                    { vm.configureCloud(daily, monthly, timeout) },
                    Modifier.fillMaxWidth(),
                ) {
                    Text("Save ceilings and timeout")
                }
                Text(
                    "Today: ${usd(state.usage.todayNanoUsd)} / ${usd(state.limits.dailyNanoUsd)}\nThis month: ${usd(state.usage.monthNanoUsd)} / ${usd(state.limits.monthlyNanoUsd)}\n${state.usage.callsToday}/10 requests today · ${state.usage.unresolved} unresolved reservations this month",
                    color = Muted,
                )
                if (state.problem != null) Text(state.problem!!, color = Coral)
                else if (state.usage.halted)
                    Text(
                        "Cloud paused after an unexpected model or usage receipt. Local mode works; update the app before reconnecting.",
                        color = Coral,
                    )
                Text(
                    "Before sending, Songlane reserves the price of the model's full input window plus 256 output tokens. A valid usage receipt reduces it to standard-price token cost. Timeouts, cancellations and missing receipts keep the reservation. This intentionally needs more headroom than a short prompt normally costs.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Rates reviewed ${IntentModels.reviewedOn}; cloud pauses after ${IntentModels.validThrough} until pricing is updated. This ledger is not your provider invoice: taxes, price changes and use elsewhere are outside it. Set provider billing controls too. Keys, limits and usage are excluded from backups and Taste Passport.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                error?.let { Text(it, color = Coral) }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
    )
}
