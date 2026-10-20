package app.songlane

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun demoLibraryPlaysAndCompassProducesValidatedRoute() {
        val repo = (compose.activity.application as SonglaneApplication).repository
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        compose.waitUntil(15000) { vm.playback.state.value.connected }
        compose.runOnIdle {
            vm.playback.clear()
            vm.route.value = null
        }
        runBlocking { repo.deleteAll() }
        compose.onNodeWithText("Try the sound sketches").performScrollTo().performClick()
        compose.waitUntil(20000) { repo.state.value.tracks.size == 8 }
        compose.onNodeWithText("Library", useUnmergedTree = false).performClick()
        compose.onNodeWithText("Mosslight").performScrollTo().performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithContentDescription("Pause").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Pause").assertExists()
        compose.onNodeWithContentDescription("Like Mosslight").performClick()
        compose.waitUntil(5000) { repo.state.value.favorites.isNotEmpty() }
        compose.onNodeWithText("Compass", useUnmergedTree = false).performClick()
        compose
            .onNodeWithText("Find my route")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(20000) { vm.route.value != null || vm.lastError.value != null }
        assertNull(vm.lastError.value)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Your listening route"))
        compose.onNodeWithText("Your listening route").assertIsDisplayed()
        compose.onNodeWithText("Play route").assertExists()
        assertEquals(8, repo.state.value.tracks.size)
        compose.onNodeWithText("You", useUnmergedTree = false).performClick()
        compose.onNodeWithText("Your taste,\nyour terms.").assertExists()
    }
}
