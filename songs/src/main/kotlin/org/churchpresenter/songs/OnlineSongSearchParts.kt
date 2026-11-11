package org.churchpresenter.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_search
import org.churchpresenter.icons.generated.resources.ic_web
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.online_song_empty_prompt
import org.churchpresenter.strings.generated.resources.online_song_error
import org.churchpresenter.strings.generated.resources.online_song_import
import org.churchpresenter.strings.generated.resources.online_song_import_and_present
import org.churchpresenter.strings.generated.resources.online_song_import_failed
import org.churchpresenter.strings.generated.resources.online_song_no_results
import org.churchpresenter.strings.generated.resources.online_song_preview_empty
import org.churchpresenter.strings.generated.resources.online_song_search_action
import org.churchpresenter.strings.generated.resources.online_song_search_hint
import org.churchpresenter.strings.generated.resources.online_song_search_title
import org.churchpresenter.strings.generated.resources.online_song_searching
import org.churchpresenter.strings.generated.resources.online_song_sections_count
import org.churchpresenter.strings.generated.resources.online_song_select_prompt
import org.churchpresenter.strings.generated.resources.online_song_songbook_label
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.hoverTint
import org.churchpresenter.theme.sunken
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val FIELD_HEIGHT = 40.dp
private const val SELECTED_ALPHA = 0.14f
private const val HEADER_ALPHA = 0.12f

@Composable
internal fun OnlineDialogHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(IconRes.drawable.ic_web),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(Res.string.online_song_search_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OnlineQueryField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = stringResource(Res.string.online_song_search_hint),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                onSubmit = onSearch,
                testTagName = ONLINE_SEARCH_INPUT_TAG,
            )
            KeyButton(
                shape = AppShape(8.dp),
                enabled = query.isNotBlank() && !isSearching,
                onClick = onSearch,
                modifier = Modifier.height(FIELD_HEIGHT).testTag(ONLINE_SEARCH_BUTTON_TAG),
            ) {
                Icon(
                    painter = painterResource(IconRes.drawable.ic_search),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.online_song_search_action))
            }
        }
    }
}

@Composable
private fun OnlineQueryField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit = {},
    testTagName: String = "",
) {
    Row(
        modifier = modifier
            .height(FIELD_HEIGHT)
            .sunken(AppShape(8.dp), elevationPalette())
            .hoverTint(AppShape(8.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (testTagName.isNotEmpty()) Modifier.testTag(testTagName) else Modifier)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            onSubmit()
                            true
                        } else {
                            false
                        }
                    },
            )
        }
    }
}

@Composable
internal fun OnlineResultsPane(
    state: OnlineSearchUiState,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        OnlineSearchUiState.Idle -> OnlineStatusMessage(stringResource(Res.string.online_song_empty_prompt), modifier)
        OnlineSearchUiState.Loading -> OnlineLoadingPane(modifier)
        OnlineSearchUiState.Error -> OnlineStatusMessage(stringResource(Res.string.online_song_error), modifier)
        is OnlineSearchUiState.Loaded -> {
            if (state.items.isEmpty()) {
                OnlineStatusMessage(stringResource(Res.string.online_song_no_results), modifier)
            } else {
                LazyColumn(
                    modifier = modifier,
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(state.items) { index, item ->
                        OnlineResultRow(
                            item = item,
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineLoadingPane(modifier: Modifier) {
    Box(modifier = modifier.padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            Text(
                text = stringResource(Res.string.online_song_searching),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnlineStatusMessage(text: String, modifier: Modifier) {
    Box(modifier = modifier.padding(20.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnlineResultRow(item: OnlineSongResult, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_ALPHA)
    else MaterialTheme.colorScheme.surfaceContainerLow
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOf(item.artist, item.sourceLabel).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(Res.string.online_song_sections_count, item.sectionCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun OnlinePreviewPane(
    selected: OnlineSongResult?,
    songbook: String,
    onSongbookChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected == null) {
        OnlineStatusMessage(stringResource(Res.string.online_song_preview_empty), modifier)
        return
    }
    Column(
        modifier = modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = selected.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected.artist.isNotBlank()) {
                Text(
                    text = selected.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.online_song_songbook_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OnlineQueryField(
                value = songbook,
                onValueChange = onSongbookChange,
                placeholder = "",
                modifier = Modifier.weight(1f),
                testTagName = ONLINE_SONGBOOK_INPUT_TAG,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(AppShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            selected.formattedLyrics.forEach { line ->
                val isHeader = line.startsWith("[") || line.startsWith("{")
                if (line.isBlank()) {
                    Spacer(Modifier.height(6.dp))
                } else if (isHeader) {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(AppShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = HEADER_ALPHA))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                } else {
                    Text(text = line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
internal fun OnlineDialogFooter(
    selected: OnlineSongResult?,
    importFailed: Boolean,
    onDismiss: () -> Unit,
    onImport: (OnlineSongResult, goLive: Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (importFailed) {
            Text(
                text = stringResource(Res.string.online_song_import_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = selected?.let {
                    stringResource(Res.string.online_song_sections_count, it.sectionCount)
                } ?: stringResource(Res.string.online_song_select_prompt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        GhostButton(onClick = onDismiss) {
            Text(stringResource(Res.string.cancel))
        }
        KeyButton(
            shape = AppShape(8.dp),
            enabled = selected != null,
            onClick = { selected?.let { onImport(it, false) } },
            modifier = Modifier.testTag(ONLINE_IMPORT_BUTTON_TAG),
        ) {
            Text(stringResource(Res.string.online_song_import))
        }
        RaisedButton(
            shape = AppShape(8.dp),
            enabled = selected != null,
            onClick = { selected?.let { onImport(it, true) } },
            modifier = Modifier.testTag(ONLINE_IMPORT_LIVE_BUTTON_TAG),
        ) {
            Text(stringResource(Res.string.online_song_import_and_present))
        }
    }
}
