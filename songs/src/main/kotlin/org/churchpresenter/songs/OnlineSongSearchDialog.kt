package org.churchpresenter.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.theme.AppShape

private val DIALOG_WIDTH = 860.dp
private val DIALOG_HEIGHT = 580.dp
private val RESULTS_PANE_WIDTH = 310.dp

internal const val ONLINE_SEARCH_INPUT_TAG = "online.song.query"
internal const val ONLINE_SEARCH_BUTTON_TAG = "online.song.search"
internal const val ONLINE_SONGBOOK_INPUT_TAG = "online.song.songbook"
internal const val ONLINE_IMPORT_BUTTON_TAG = "online.song.import"
internal const val ONLINE_IMPORT_LIVE_BUTTON_TAG = "online.song.importLive"

internal sealed interface OnlineSearchUiState {
    data object Idle : OnlineSearchUiState
    data object Loading : OnlineSearchUiState
    data class Loaded(val items: List<OnlineSongResult>) : OnlineSearchUiState
    data object Error : OnlineSearchUiState
}

/** Hosts the online lyrics search dialog and saves/presents the imported song. */
@Composable
internal fun SongsTabController.OnlineSongSearchDialogHost(
    searchClient: OnlineSongSearchClient = remember { OnlineSongSearchClient() },
) {
    if (!dialogs.searchingOnline) return

    val scope = rememberCoroutineScope()
    val defaultSongbook = remember(viewModel.selectedSongbook.value, appSettings.newSongSongbook) {
        viewModel.selectedSongbook.value.ifBlank { appSettings.newSongSongbook }
    }
    var searchState by remember { mutableStateOf<OnlineSearchUiState>(OnlineSearchUiState.Idle) }
    var importError by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            dialogs.closeOnlineSearch()
            tabFocusRequester.requestFocus()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        OnlineSongSearchContent(
            initialSongbook = defaultSongbook,
            searchState = searchState,
            importFailed = importError,
            onSearch = { query ->
                val trimmed = query.trim()
                if (trimmed.isEmpty()) return@OnlineSongSearchContent
                importError = false
                searchState = OnlineSearchUiState.Loading
                scope.launch {
                    searchState = when (val outcome = searchClient.search(trimmed)) {
                        is OnlineSongSearchOutcome.Success -> OnlineSearchUiState.Loaded(outcome.results)
                        is OnlineSongSearchOutcome.NetworkError -> OnlineSearchUiState.Error
                    }
                }
            },
            onImport = { result, songbook, goLive ->
                val newSong = SongItem(
                    number = "",
                    title = result.title,
                    author = result.artist,
                    songbook = songbook.trim(),
                    lyrics = result.formattedLyrics,
                )
                if (!viewModel.createSong(newSong)) {
                    importError = true
                    return@OnlineSongSearchContent
                }
                importError = false
                dialogs.closeOnlineSearch()
                if (goLive) pushImportedSongLive(newSong)
                scope.launch {
                    viewModel.updateSearchQuery("")
                    if (viewModel.isLoading.value) snapshotFlow { viewModel.isLoading.value }.first { !it }
                    viewModel.selectSongByDetails(0, newSong.title, newSong.songbook, newSong.songId)
                    tabFocusRequester.requestFocus()
                }
            },
            onDismiss = {
                dialogs.closeOnlineSearch()
                tabFocusRequester.requestFocus()
            },
        )
    }
}

private fun SongsTabController.pushImportedSongLive(song: SongItem) {
    val sections = viewModel.getLyricSections(song)
    live.titleSlideSelected = false
    onAllSectionsChanged(sections)
    onSectionIndexChanged(0)
    onLineIndexChanged(0)
    sections.firstOrNull()?.let(onSongItemSelected)
    live.live(song.songId, 0, 0)
    live.wentLiveSongId = song.songId
    onSongWentLive(song)
    onPresenting(Presenting.LYRICS)
}

@Composable
internal fun OnlineSongSearchContent(
    initialSongbook: String,
    searchState: OnlineSearchUiState,
    importFailed: Boolean,
    onSearch: (String) -> Unit,
    onImport: (OnlineSongResult, songbook: String, goLive: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var songbook by remember(initialSongbook) { mutableStateOf(initialSongbook) }
    val results = (searchState as? OnlineSearchUiState.Loaded)?.items.orEmpty()
    var selectedIndex by remember(results) { mutableStateOf(if (results.isNotEmpty()) 0 else -1) }
    val selected = results.getOrNull(selectedIndex)
    val queryFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { queryFocus.requestFocus() }

    Surface(
        modifier = Modifier.size(DIALOG_WIDTH, DIALOG_HEIGHT),
        shape = AppShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 6.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OnlineDialogHeader(
                query = query,
                onQueryChange = { query = it },
                isSearching = searchState is OnlineSearchUiState.Loading,
                focusRequester = queryFocus,
                onSearch = { onSearch(query) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                OnlineResultsPane(
                    state = searchState,
                    selectedIndex = selectedIndex,
                    onSelect = { selectedIndex = it },
                    modifier = Modifier.width(RESULTS_PANE_WIDTH).fillMaxHeight(),
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OnlinePreviewPane(
                    selected = selected,
                    songbook = songbook,
                    onSongbookChange = { songbook = it },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            OnlineDialogFooter(
                selected = selected,
                importFailed = importFailed,
                onDismiss = onDismiss,
                onImport = { item, goLive -> onImport(item, songbook, goLive) },
            )
        }
    }
}
