package org.churchpresenter.app.churchpresenter

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * [stableFileId] is the hash `MainDesktop` uses to identify a picture folder or presentation file
 * across every consumer that needs to name it: the companion server's REST/WS payloads, the
 * remote picture-select flow matching an incoming folder id against the one currently loaded, and
 * `onSlideChanged`/`onPresentationSlidesLoaded`. It used to be three separate copies of the same
 * expression inline in this file (plus more in `CompanionServer.kt` and `PresentationTab.kt`) —
 * the comment at the remote picture-select call site already called out that it has to match
 * `CompanionServer.updatePictures`'s own copy, which is exactly the kind of thing that silently
 * drifts apart when it isn't a single named function.
 *
 * What matters here is stability and uniqueness, not the specific hash value: the same path must
 * always produce the same id (session-to-session, since it's how a remote client's previously-seen
 * folder id gets matched back up), and different paths must not collide in any of the small,
 * everyday ways paths tend to look similar.
 */
class MainDesktopStableFileIdTest {

    @Test
    fun `the same path always produces the same id`() {
        val a = stableFileId(File("/photos/advent"))
        val b = stableFileId(File("/photos/advent"))
        assertEquals(a, b)
    }

    @Test
    fun `two different paths produce different ids`() {
        val a = stableFileId(File("/photos/advent"))
        val b = stableFileId(File("/photos/christmas"))
        assertNotEquals(a, b)
    }

    @Test
    fun `a trailing slash difference still produces different ids`() {
        // File normalizes away a trailing slash, but a folder and a same-named sibling file are
        // still expected to resolve to distinct absolute paths in the cases that matter.
        val a = stableFileId(File("/photos/advent"))
        val b = stableFileId(File("/photos/advent-2"))
        assertNotEquals(a, b)
    }

    @Test
    fun `case is significant, matching the filesystem's own case sensitivity`() {
        val a = stableFileId(File("/photos/Advent"))
        val b = stableFileId(File("/photos/advent"))
        assertNotEquals(a, b)
    }

    @Test
    fun `the id is a lowercase hexadecimal string with no sign character`() {
        // toUInt() before toString(16) is what keeps a negative Int hashCode from producing a
        // leading '-' in the id -- a client comparing ids as opaque strings must never see one.
        val id = stableFileId(File("/photos/advent"))
        assertEquals(id, id.lowercase())
        assertNotEquals('-', id.firstOrNull())
    }
}
