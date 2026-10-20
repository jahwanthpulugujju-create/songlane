package app.songlane

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.songlane.agent.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun cloudOptionsManageIndependentKeysAndCeilingsAndFullDeletionRestoresLocalDefaults() {
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        compose.waitUntil(15000) { vm.playback.state.value.connected }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Manage cloud options"))
        compose.onNodeWithText("Manage cloud options").performClick()
        compose
            .onNodeWithText("Anthropic API key")
            .performTextInput("ui-fixture-key-not-a-provider-secret-123")
        compose.onNodeWithText("Save key").performClick()
        compose.waitUntil(5000) { AiProvider.ANTHROPIC in vm.savedProviders.value }
        compose.onNodeWithText("OpenAI", substring = false).performClick()
        compose
            .onNodeWithText("OpenAI API key")
            .performTextInput("ui-fixture-openai-key-not-a-secret-123")
        compose.onNodeWithText("Save key").performClick()
        compose.waitUntil(5000) { vm.savedProviders.value.size == 2 }
        compose.onNodeWithText("Anthropic", substring = false).performClick()
        compose.onNodeWithText("Delete key").performClick()
        compose.waitUntil(5000) { vm.savedProviders.value == setOf(AiProvider.OPENAI) }
        compose.onNodeWithText("Daily ceiling (USD)").performScrollTo().performTextReplacement("0")
        compose.onNodeWithText("Save ceilings and timeout").performScrollTo().performClick()
        compose.waitUntil(5000) { vm.cloud.state.value.limits.dailyNanoUsd == 0L }
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { vm.eraseAll() }
        compose.waitUntil(10000) {
            vm.savedProviders.value.isEmpty() && vm.cloud.state.value == CloudState()
        }
        assertEquals(0, vm.cloud.state.value.usage.callsToday)
        assertEquals(500_000_000L, vm.cloud.state.value.limits.dailyNanoUsd)
        assertNull(vm.vault.read(AiProvider.OPENAI))
        assertNull(vm.vault.read(AiProvider.ANTHROPIC))
    }
}
