package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.ui.graphics.vector.ImageVector
import org.churchpresenter.sharedui.models.Tabs

/**
 * Logical categories grouping the top-level tabs into clean, focused workspaces:
 * - WORSHIP: Core ministry content (Songs, Bible, Presentation, Pictures, Media, Announcements).
 * - BROADCAST: Transmission and remote displays (Lower Third, Signage, Canvas, Web, STT).
 * - EXTRAS: Interactive tools and utilities (Q&A, Dictionary, Companion Surface, Crossword).
 * - ALL: Complete flat list of all visible tabs.
 */
enum class TabCategory(val title: String, val icon: ImageVector) {
    WORSHIP("Culto & Mídia", Icons.Default.LibraryMusic),
    BROADCAST("Transmissão", Icons.Default.Sensors),
    EXTRAS("Extras", Icons.Default.AutoAwesome),
    ALL("Todas", Icons.Default.ViewAgenda);

    fun matches(tab: Tabs): Boolean = when (this) {
        WORSHIP -> tab in WORSHIP_TABS
        BROADCAST -> tab in BROADCAST_TABS
        EXTRAS -> tab in EXTRAS_TABS
        ALL -> true
    }

    companion object {
        private val WORSHIP_TABS = setOf(
            Tabs.SONGS,
            Tabs.BIBLE,
            Tabs.PRESENTATION,
            Tabs.PICTURES,
            Tabs.MEDIA,
            Tabs.ANNOUNCEMENTS,
        )

        private val BROADCAST_TABS = setOf(
            Tabs.LOWER_THIRD,
            Tabs.SIGNAGE,
            Tabs.CANVAS,
            Tabs.WEB,
            Tabs.STT,
        )

        private val EXTRAS_TABS = setOf(
            Tabs.QA,
            Tabs.DICTIONARY,
            Tabs.COMPANION_SURFACE,
            Tabs.CROSSWORD,
        )

        fun categoryFor(tab: Tabs): TabCategory = when (tab) {
            in WORSHIP_TABS -> WORSHIP
            in BROADCAST_TABS -> BROADCAST
            in EXTRAS_TABS -> EXTRAS
            else -> ALL
        }
    }
}
