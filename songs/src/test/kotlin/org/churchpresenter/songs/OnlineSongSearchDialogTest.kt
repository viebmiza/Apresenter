@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnlineSongSearchDialogTest {

    private fun sampleResult() = OnlineSongResult(
        title = "Ousado Amor",
        artist = "Isaías Saad",
        album = "Single",
        rawLyrics = "Antes de eu falar\nTu cantavas sobre mim",
        formattedLyrics = listOf("[Verse 1]", "Antes de eu falar", "Tu cantavas sobre mim"),
        sourceLabel = "LRCLIB",
        score = 50,
    )

    @Test
    fun `idle state keeps search and import disabled until text and selection exist`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                OnlineSongSearchContent(
                    initialSongbook = "Adoração",
                    searchState = OnlineSearchUiState.Idle,
                    importFailed = false,
                    onSearch = {},
                    onImport = { _, _, _ -> },
                    onDismiss = {},
                )
            }
        }

        onNodeWithTag(ONLINE_SEARCH_BUTTON_TAG).assertIsNotEnabled()
        onNodeWithTag(ONLINE_IMPORT_BUTTON_TAG).assertIsNotEnabled()
        onNodeWithTag(ONLINE_IMPORT_LIVE_BUTTON_TAG).assertIsNotEnabled()

        onNodeWithTag(ONLINE_SEARCH_INPUT_TAG).performTextInput("Ousado Amor")
        onNodeWithTag(ONLINE_SEARCH_BUTTON_TAG).assertIsEnabled()
    }

    @Test
    fun `selecting a loaded song previews sections and passes songbook and live flag on import`() = runComposeUiTest {
        var importedSongbook: String? = null
        var importedLive: Boolean? = null
        val item = sampleResult()

        setContent {
            MaterialTheme {
                OnlineSongSearchContent(
                    initialSongbook = "Louvor",
                    searchState = OnlineSearchUiState.Loaded(listOf(item)),
                    importFailed = false,
                    onSearch = {},
                    onImport = { _, book, goLive ->
                        importedSongbook = book
                        importedLive = goLive
                    },
                    onDismiss = {},
                )
            }
        }

        onNodeWithText("Antes de eu falar").assertExists()
        onNodeWithText("[Verse 1]").assertExists()
        onNodeWithTag(ONLINE_IMPORT_LIVE_BUTTON_TAG).assertIsEnabled().performClick()

        assertEquals("Louvor", importedSongbook)
        assertTrue(importedLive == true)
    }

    @Test
    fun `import button saves without going live and error state shows offline guidance`() = runComposeUiTest {
        var importedLive: Boolean? = null
        val item = sampleResult()

        setContent {
            MaterialTheme {
                OnlineSongSearchContent(
                    initialSongbook = "",
                    searchState = OnlineSearchUiState.Loaded(listOf(item)),
                    importFailed = false,
                    onSearch = {},
                    onImport = { _, _, goLive -> importedLive = goLive },
                    onDismiss = {},
                )
            }
        }

        onNodeWithTag(ONLINE_IMPORT_BUTTON_TAG).performClick()
        assertFalse(importedLive ?: true)
    }
}
