package org.churchpresenter.app.churchpresenter

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Two more small decisions extracted out of [MainDesktop]: [retrySecondsLeft] is the "reconnecting
 * in Xs" countdown shown next to the Instance Link status badge, and [findLottiePresetFile]
 * matches a lower-third preset from the schedule against the files actually on disk in the
 * configured Lottie folder.
 *
 * [retrySecondsLeft] has to floor at zero rather than count into negative seconds once the retry
 * moment has passed and the reconnect hasn't fired yet on this recomposition. [findLottiePresetFile]
 * matches by either the preset's label or its stable id — a schedule item saved before a preset was
 * renamed only carries the old label, so falling back to the id keeps it resolvable.
 */
class MainDesktopRetryAndLottieTest {

    // ── retrySecondsLeft ─────────────────────────────────────────────────────────

    @Test
    fun `no scheduled retry means no countdown at all`() {
        assertNull(retrySecondsLeft(nextRetryAtMs = null, nowMs = 1_000L))
    }

    @Test
    fun `five seconds out counts down to five`() {
        assertEquals(5L, retrySecondsLeft(nextRetryAtMs = 6_000L, nowMs = 1_000L))
    }

    @Test
    fun `the exact retry moment counts down to zero`() {
        assertEquals(0L, retrySecondsLeft(nextRetryAtMs = 1_000L, nowMs = 1_000L))
    }

    @Test
    fun `a retry moment already in the past floors at zero, never negative`() {
        assertEquals(0L, retrySecondsLeft(nextRetryAtMs = 1_000L, nowMs = 5_000L))
    }

    @Test
    fun `a partial second remaining truncates down rather than rounding up`() {
        // 1.5s left must read as 1, not 2 -- an operator watching the badge shouldn't see it
        // undercount by rounding up past the real reconnect moment.
        assertEquals(1L, retrySecondsLeft(nextRetryAtMs = 2_500L, nowMs = 1_000L))
    }

    // ── findLottiePresetFile ─────────────────────────────────────────────────────

    private fun lottie(name: String) = File("/lower-thirds/$name.json")

    @Test
    fun `a preset matches by its label`() {
        val files = listOf(lottie("welcome"), lottie("offering"))
        val found = findLottiePresetFile(files, presetLabel = "welcome", presetId = "does-not-exist")
        assertSame(files[0], found)
    }

    @Test
    fun `a preset renamed since the schedule item was saved still matches by its id`() {
        val idNamedFile = lottie("preset-abc123")
        val files = listOf(lottie("welcome"), idNamedFile)
        val found = findLottiePresetFile(files, presetLabel = "old-label-no-longer-on-disk", presetId = "preset-abc123")
        assertSame(idNamedFile, found)
    }

    @Test
    fun `neither the label nor the id matching anything on disk resolves to null`() {
        val files = listOf(lottie("welcome"), lottie("offering"))
        assertNull(findLottiePresetFile(files, presetLabel = "missing", presetId = "also-missing"))
    }

    @Test
    fun `an empty folder listing resolves to null`() {
        assertNull(findLottiePresetFile(emptyList(), presetLabel = "welcome", presetId = "preset-abc123"))
    }

    @Test
    fun `a null folder listing -- the folder doesn't exist or isn't readable -- resolves to null`() {
        assertNull(findLottiePresetFile(null, presetLabel = "welcome", presetId = "preset-abc123"))
    }

    @Test
    fun `the label is tried before the id, but either alone is sufficient`() {
        val files = listOf(lottie("welcome"))
        assertSame(files[0], findLottiePresetFile(files, presetLabel = "welcome", presetId = "welcome"))
    }
}
