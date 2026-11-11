package org.churchpresenter.songs

import java.text.Normalizer

private const val CHUNK_LINES = 4
private const val MIN_CHUNK_THRESHOLD = 6
private const val EXACT_PHRASE_TITLE_BONUS = 25
private const val EXACT_PHRASE_LYRICS_BONUS = 40
private const val TOKEN_TITLE_WEIGHT = 4
private const val TOKEN_ARTIST_WEIGHT = 2
private const val TOKEN_LYRICS_WEIGHT = 5
private const val UNICODE_HEX_DIGITS = 4
private const val UNICODE_RADIX = 16

private val LRC_TIMESTAMP = Regex("""^\s*(?:\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]\s*)+""")
private val LRC_META_TAG = Regex("""^\s*\[(?:ar|ti|al|au|length|by|offset|re|ve):.*]\s*$""", RegexOption.IGNORE_CASE)
private val COMBINING_MARKS = Regex("""\p{M}+""")
private val NON_ALNUM = Regex("""[^\p{L}\p{N}]+""")
private val KNOWN_SECTION_HEADER = Regex(
    """^\[([^\]]+)]|\{([^}]+)}|""" +
        """(?:Refr[aã]o|Coro|Chorus|Estrofe\s*\d*|Verse\s*\d*|""" +
        """Ponte|Bridge|Pr[eé]-?Refr[aã]o|Pre-?Chorus|Intro|Outro):?\s*$""",
    RegexOption.IGNORE_CASE,
)

/** Normalizes [text] by removing diacritics and folding whitespace for search/dedupe matching. */
internal fun normalizeForSongMatch(text: String): String {
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    val stripped = COMBINING_MARKS.replace(decomposed, "")
    return NON_ALNUM.replace(stripped.lowercase(), " ").trim()
}

/** Extracts plain lyrics from LRC text by removing `[mm:ss.xx]` timestamps and metadata tags. */
internal fun stripLrcTimestamps(raw: String): String =
    raw.lineSequence()
        .filterNot { LRC_META_TAG.matches(it) }
        .map { LRC_TIMESTAMP.replace(it, "").trimEnd() }
        .joinToString("\n")
        .trim()

/** Computes a search relevance score weighing lyric phrase hits alongside title and artist hits. */
internal fun scoreOnlineSongMatch(query: String, title: String, artist: String, lyrics: String): Int {
    val normQuery = normalizeForSongMatch(query)
    if (normQuery.isBlank()) return 0
    val tokens = normQuery.split(' ').filter { it.length >= 2 }
    val normTitle = normalizeForSongMatch(title)
    val normArtist = normalizeForSongMatch(artist)
    val normLyrics = normalizeForSongMatch(lyrics)

    var score = 0
    if (normQuery in normLyrics) score += EXACT_PHRASE_LYRICS_BONUS
    if (normQuery in normTitle) score += EXACT_PHRASE_TITLE_BONUS
    for (token in tokens) {
        if (token in normTitle) score += TOKEN_TITLE_WEIGHT
        if (token in normArtist) score += TOKEN_ARTIST_WEIGHT
        if (token in normLyrics) score += TOKEN_LYRICS_WEIGHT
    }
    return score
}

/**
 * Converts fetched lyrics into Apresenter `.song` lines (`[Verse 1]`, `{Chorus}`, blank separators)
 * ready for live presentation without requiring manual reformatting.
 */
internal fun formatImportedLyrics(rawLyrics: String): List<String> {
    val cleanLines = rawLyrics.replace("\r\n", "\n").replace('\r', '\n').lines().map { it.trim() }
    val rawBlocks = splitIntoBlocks(cleanLines)
    if (rawBlocks.isEmpty()) return emptyList()

    val repeatedBlockKey = findRepeatedChorusKey(rawBlocks)
    val output = mutableListOf<String>()
    var verseNumber = 1

    rawBlocks.forEachIndexed { index, block ->
        if (index > 0) output += ""
        val first = block.first()
        val headerMatch = KNOWN_SECTION_HEADER.matchEntire(first)
        if (headerMatch != null && block.size > 1) {
            output += canonicalSectionHeader(first)
            output.addAll(block.drop(1))
        } else {
            val blockKey = normalizeForSongMatch(block.joinToString(" "))
            output += if (repeatedBlockKey != null && blockKey == repeatedBlockKey) {
                "{Chorus}"
            } else {
                "[Verse ${verseNumber++}]"
            }
            output.addAll(block)
        }
    }
    return output
}

private fun splitIntoBlocks(lines: List<String>): List<List<String>> {
    val blocks = mutableListOf<List<String>>()
    val current = mutableListOf<String>()
    for (line in lines) {
        if (line.isBlank()) {
            if (current.isNotEmpty()) {
                blocks += current.toList()
                current.clear()
            }
        } else {
            current += line
        }
    }
    if (current.isNotEmpty()) blocks += current.toList()
    return if (blocks.size == 1 && blocks.first().size >= MIN_CHUNK_THRESHOLD && !looksTagged(blocks.first())) {
        blocks.first().chunked(CHUNK_LINES)
    } else {
        blocks
    }
}

private fun looksTagged(lines: List<String>): Boolean = lines.any { KNOWN_SECTION_HEADER.matches(it) }

private fun findRepeatedChorusKey(blocks: List<List<String>>): String? {
    if (blocks.size < 2) return null
    val counts = blocks
        .filterNot { looksTagged(it.take(1)) }
        .map { normalizeForSongMatch(it.joinToString(" ")) }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
    return counts.entries.filter { it.value >= 2 }.maxByOrNull { it.value }?.key
}

private fun canonicalSectionHeader(line: String): String {
    val stripped = line.trim().trimEnd(':').trim()
    return when {
        stripped.startsWith("[") && stripped.endsWith("]") -> stripped
        stripped.startsWith("{") && stripped.endsWith("}") -> stripped
        stripped.contains("refr", ignoreCase = true) ||
            stripped.contains("coro", ignoreCase = true) ||
            stripped.contains("chorus", ignoreCase = true) -> "{Chorus}"
        else -> "[$stripped]"
    }
}

/** Lightweight zero-dependency RFC-8259 JSON parser for online lyrics API responses. */
internal object MinimalJson {

    fun parse(text: String): Any? {
        val cursor = JsonCursor(text)
        cursor.skipWhitespace()
        return if (cursor.hasNext()) cursor.readValue() else null
    }
}

private class JsonCursor(private val text: String) {
    var pos = 0

    fun hasNext(): Boolean = pos < text.length

    fun skipWhitespace() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    fun readValue(): Any? {
        skipWhitespace()
        if (!hasNext()) error("Unexpected end of JSON")
        return when (text[pos]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't', 'f' -> readBoolean()
            'n' -> { pos += "null".length; null }
            else -> readNumber()
        }
    }

    private fun readObject(): Map<String, Any?> {
        pos++
        val map = linkedMapOf<String, Any?>()
        skipWhitespace()
        if (hasNext() && text[pos] == '}') {
            pos++
            return map
        }
        while (hasNext()) {
            skipWhitespace()
            val key = readString()
            skipWhitespace()
            require(hasNext() && text[pos] == ':') { "Expected ':' at $pos" }
            pos++
            map[key] = readValue()
            skipWhitespace()
            if (!hasNext() || text[pos] != ',') break
            pos++
        }
        if (hasNext() && text[pos] == '}') pos++
        return map
    }

    private fun readArray(): List<Any?> {
        pos++
        val list = mutableListOf<Any?>()
        skipWhitespace()
        if (hasNext() && text[pos] == ']') {
            pos++
            return list
        }
        while (hasNext()) {
            list += readValue()
            skipWhitespace()
            if (!hasNext() || text[pos] != ',') break
            pos++
        }
        if (hasNext() && text[pos] == ']') pos++
        return list
    }

    private fun readString(): String {
        pos++
        val sb = StringBuilder()
        while (hasNext()) {
            val c = text[pos++]
            if (c == '"') return sb.toString()
            if (c == '\\' && hasNext()) {
                sb.append(readJsonEscape(text[pos++], this))
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    fun nextUnicodeChar(): Char {
        val end = (pos + UNICODE_HEX_DIGITS).coerceAtMost(text.length)
        val hex = text.substring(pos, end)
        pos = end
        return hex.toIntOrNull(UNICODE_RADIX)?.toChar() ?: '?'
    }

    private fun readBoolean(): Boolean =
        if (text.startsWith("true", pos)) {
            pos += "true".length
            true
        } else {
            pos += "false".length
            false
        }

    private fun readNumber(): Number {
        val start = pos
        while (pos < text.length && text[pos] in "-0123456789.eE+") pos++
        val raw = text.substring(start, pos)
        return if ('.' in raw || 'e' in raw || 'E' in raw) raw.toDoubleOrNull() ?: 0.0
        else raw.toLongOrNull() ?: 0L
    }
}

private fun readJsonEscape(esc: Char, cursor: JsonCursor): Char = when (esc) {
    '"', '\\', '/' -> esc
    'b' -> '\b'
    'f' -> '\u000C'
    'n' -> '\n'
    'r' -> '\r'
    't' -> '\t'
    'u' -> cursor.nextUnicodeChar()
    else -> esc
}
