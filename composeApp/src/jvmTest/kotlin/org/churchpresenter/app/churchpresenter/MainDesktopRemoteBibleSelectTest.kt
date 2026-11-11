package org.churchpresenter.app.churchpresenter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Two decisions behind a remote "select this Bible verse" request (POST /api/bible/select or the
 * WS select_bible_verse command), extracted out of [MainDesktop]'s `selectBibleVerseFlow` handler:
 * [resolveBookIndex] matches the client's free-text book name against the primary Bible's own book
 * list, and [parseVerseRangeEnd] turns the client's range string ("16-18", "2,4,5") into the end
 * verse used for CCLI/statistics logging.
 *
 * Both take untrusted input from a phone or a linked instance, so the interesting behavior is what
 * happens on malformed input — a book name that doesn't exist, a range with garbage in it — which
 * has to degrade gracefully rather than crash the display pipeline for everyone else in the room.
 */
class MainDesktopRemoteBibleSelectTest {

    private val books = listOf("Genesis", "Exodus", "Leviticus", "John", "Revelation")

    // ── resolveBookIndex ─────────────────────────────────────────────────────────

    @Test
    fun `an exact-case book name resolves to its position`() {
        assertEquals(3, resolveBookIndex(books, "John"))
    }

    @Test
    fun `book name matching is case-insensitive, as it has to be for a phone client`() {
        assertEquals(3, resolveBookIndex(books, "john"))
        assertEquals(3, resolveBookIndex(books, "JOHN"))
    }

    @Test
    fun `a book name that is not in the list resolves to -1`() {
        assertEquals(-1, resolveBookIndex(books, "Acts"))
    }

    @Test
    fun `an empty book list resolves to -1 rather than throwing`() {
        assertEquals(-1, resolveBookIndex(emptyList(), "John"))
    }

    @Test
    fun `the first matching book wins when the list has a duplicate`() {
        assertEquals(0, resolveBookIndex(listOf("John", "John"), "John"))
    }

    // ── parseVerseRangeEnd ────────────────────────────────────────────────────────

    @Test
    fun `a blank range has no end at all`() {
        assertNull(parseVerseRangeEnd("", verseNumber = 16))
        assertNull(parseVerseRangeEnd("   ", verseNumber = 16))
    }

    @Test
    fun `a dash range's end is the larger number`() {
        assertEquals(18, parseVerseRangeEnd("16-18", verseNumber = 16))
    }

    @Test
    fun `a comma list's end is the maximum of the listed verses`() {
        assertEquals(5, parseVerseRangeEnd("2,4,5", verseNumber = 2))
    }

    @Test
    fun `a single verse number with no separator has no end beyond itself`() {
        // "16" alone parses to the list [16]; max() is 16, which is not greater than verseNumber,
        // so there is no separate end.
        assertNull(parseVerseRangeEnd("16", verseNumber = 16))
    }

    @Test
    fun `an end equal to the start verse is not treated as a real range`() {
        assertNull(parseVerseRangeEnd("16-16", verseNumber = 16))
    }

    @Test
    fun `an end smaller than the start verse is discarded rather than reported backwards`() {
        assertNull(parseVerseRangeEnd("16-10", verseNumber = 16))
    }

    @Test
    fun `non-numeric tokens are dropped rather than crashing the parse`() {
        assertEquals(18, parseVerseRangeEnd("16-abc-18", verseNumber = 16))
    }

    @Test
    fun `a range that is entirely non-numeric has no end`() {
        assertNull(parseVerseRangeEnd("abc-def", verseNumber = 16))
    }

    @Test
    fun `whitespace around numbers in the range is trimmed`() {
        assertEquals(18, parseVerseRangeEnd(" 16 - 18 ", verseNumber = 16))
    }
}
