package org.churchpresenter.songs

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

private const val HTTP_OK = 200
private const val HTTP_TIMEOUT_SECONDS = 10L
private const val MAX_DISPLAY_RESULTS = 12
private const val MIN_DIRECT_HITS_BEFORE_DISCOVERY = 3
private const val MAX_DISCOVERED_PAIRS = 4
private const val MIN_LYRIC_SCORE_DIRECT_HIT = 25
private const val LRCLIB_SEARCH_URL = "https://lrclib.net/api/search"
private const val ITUNES_SEARCH_URL = "https://itunes.apple.com/search"
private const val LYRICS_OVH_SUGGEST_URL = "https://api.lyrics.ovh/suggest"
private const val LYRICS_OVH_V1_URL = "https://api.lyrics.ovh/v1"
private const val USER_AGENT = "Apresenter/1.0 (https://github.com/viebmiza/Apresenter)"

/** One song match retrieved from online lyrics services, ready for preview and import. */
internal data class OnlineSongResult(
    val title: String,
    val artist: String,
    val album: String,
    val rawLyrics: String,
    val formattedLyrics: List<String>,
    val sourceLabel: String,
    val score: Int = 0,
) {
    val sectionCount: Int get() = formattedLyrics.count { it.startsWith("[") || it.startsWith("{") }
        .coerceAtLeast(if (formattedLyrics.isEmpty()) 0 else 1)
}

/** Result of an online lyrics search request. */
internal sealed interface OnlineSongSearchOutcome {
    data class Success(val results: List<OnlineSongResult>) : OnlineSongSearchOutcome
    data object NetworkError : OnlineSongSearchOutcome
}

/** Minimal HTTP GET abstraction so online song lookup can run against local stubs in tests. */
internal fun interface HttpTextGet {
    suspend operator fun invoke(url: String): String?
}

internal object DefaultHttpTextGet : HttpTextGet {
    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
            .build()
    }

    override suspend fun invoke(url: String): String? {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        return if (response.statusCode() == HTTP_OK) response.body() else null
    }
}

/**
 * Searches public keyless lyrics sources by song title, artist, or lyric phrase.
 *
 * Uses LRCLIB directly for title/artist queries and combines iTunes + Lyrics.ovh discovery when
 * a query looks like a lyric phrase or yields few direct hits.
 */
internal class OnlineSongSearchClient(
    private val httpGet: HttpTextGet = DefaultHttpTextGet,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val lrclibBaseUrl: String = LRCLIB_SEARCH_URL,
    private val itunesBaseUrl: String = ITUNES_SEARCH_URL,
    private val lyricsOvhSuggestBaseUrl: String = LYRICS_OVH_SUGGEST_URL,
    private val lyricsOvhV1BaseUrl: String = LYRICS_OVH_V1_URL,
) {

    suspend fun search(query: String): OnlineSongSearchOutcome = withContext(ioDispatcher) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext OnlineSongSearchOutcome.Success(emptyList())

        var anyResponse = false
        val collected = mutableListOf<OnlineSongResult>()

        val directBody = safeGet("$lrclibBaseUrl?q=${encodeQueryParam(trimmed)}")
        if (directBody != null) {
            anyResponse = true
            collected += parseLrcLibResults(directBody, trimmed)
        }

        val hasStrongLyricHit = collected.any { it.score >= MIN_LYRIC_SCORE_DIRECT_HIT }
        if (collected.size < MIN_DIRECT_HITS_BEFORE_DISCOVERY || !hasStrongLyricHit) {
            val discovered = discoverCandidateTracks(trimmed)
            anyResponse = anyResponse || discovered.responded
            val resolved = resolveDiscoveredTracks(discovered.pairs, trimmed, collected)
            anyResponse = anyResponse || resolved.responded
            collected += resolved.items
        }

        if (!anyResponse && collected.isEmpty()) {
            return@withContext OnlineSongSearchOutcome.NetworkError
        }

        OnlineSongSearchOutcome.Success(deduplicateAndRank(collected, trimmed).take(MAX_DISPLAY_RESULTS))
    }

    private data class DiscoveryBatch(val responded: Boolean, val pairs: List<Pair<String, String>>)
    private data class ResolvedBatch(val responded: Boolean, val items: List<OnlineSongResult>)

    private suspend fun discoverCandidateTracks(query: String): DiscoveryBatch = coroutineScope {
        val encodedQuery = encodeQueryParam(query)
        val encodedPath = encodePathSegment(query)
        val itunesDeferred = async { safeGet("$itunesBaseUrl?term=$encodedQuery&entity=song&limit=6") }
        val suggestDeferred = async { safeGet("$lyricsOvhSuggestBaseUrl/$encodedPath") }

        val itunesBody = itunesDeferred.await()
        val suggestBody = suggestDeferred.await()
        val pairs = buildList {
            if (suggestBody != null) addAll(parseSuggestPairs(suggestBody))
            if (itunesBody != null) addAll(parseItunesPairs(itunesBody))
        }.distinctBy { normalizeForSongMatch(it.first) to normalizeForSongMatch(it.second) }

        DiscoveryBatch(responded = itunesBody != null || suggestBody != null, pairs = pairs)
    }

    private suspend fun resolveDiscoveredTracks(
        pairs: List<Pair<String, String>>,
        query: String,
        existing: List<OnlineSongResult>,
    ): ResolvedBatch = coroutineScope {
        val existingKeys = existing.map {
            normalizeForSongMatch(it.title) to normalizeForSongMatch(it.artist)
        }.toSet()
        val candidates = pairs.filterNot { (title, artist) ->
            (normalizeForSongMatch(title) to normalizeForSongMatch(artist)) in existingKeys
        }.take(MAX_DISCOVERED_PAIRS)

        val fetched = candidates.map { (title, artist) ->
            async { fetchTrackLyrics(title, artist, query) }
        }.awaitAll()

        ResolvedBatch(responded = fetched.any { it.responded }, items = fetched.flatMap { it.items })
    }

    private suspend fun fetchTrackLyrics(title: String, artist: String, query: String): ResolvedBatch {
        val lrcUrl = "$lrclibBaseUrl?track_name=${encodeQueryParam(title)}&artist_name=${encodeQueryParam(artist)}"
        val lrcBody = safeGet(lrcUrl)
        if (lrcBody != null) {
            val lrcMatches = parseLrcLibResults(lrcBody, query)
            if (lrcMatches.isNotEmpty()) return ResolvedBatch(responded = true, items = lrcMatches)
        }

        val ovhUrl = "$lyricsOvhV1BaseUrl/${encodePathSegment(artist)}/${encodePathSegment(title)}"
        val ovhBody = safeGet(ovhUrl)
        val item = ovhBody?.let(::parseLyricsOvhBody)?.let { lyrics ->
            val formatted = formatImportedLyrics(lyrics)
            if (formatted.isEmpty()) null
            else OnlineSongResult(
                title = title,
                artist = artist,
                album = "",
                rawLyrics = lyrics,
                formattedLyrics = formatted,
                sourceLabel = "Lyrics.ovh",
                score = scoreOnlineSongMatch(query, title, artist, lyrics),
            )
        }
        val items = if (item != null) listOf(item) else emptyList()
        return ResolvedBatch(responded = lrcBody != null || ovhBody != null, items = items)
    }

    private suspend fun safeGet(url: String): String? = runCatching { httpGet(url) }.getOrNull()
}

private fun parseLrcLibResults(body: String, query: String): List<OnlineSongResult> {
    val root = runCatching { MinimalJson.parse(body) }.getOrNull() as? List<*> ?: return emptyList()
    return root.mapNotNull { entry ->
        val obj = entry as? Map<*, *> ?: return@mapNotNull null
        if (obj["instrumental"] == true) return@mapNotNull null
        val title = (obj["trackName"] as? String ?: obj["name"] as? String).orEmpty().trim()
        val artist = (obj["artistName"] as? String).orEmpty().trim()
        val album = (obj["albumName"] as? String).orEmpty().trim()
        val plain = (obj["plainLyrics"] as? String).orEmpty().trim()
        val synced = (obj["syncedLyrics"] as? String).orEmpty().trim()
        val lyrics = plain.ifBlank { stripLrcTimestamps(synced) }
        if (title.isBlank() || lyrics.isBlank()) return@mapNotNull null
        val formatted = formatImportedLyrics(lyrics)
        if (formatted.isEmpty()) return@mapNotNull null
        OnlineSongResult(
            title = title,
            artist = artist,
            album = album,
            rawLyrics = lyrics,
            formattedLyrics = formatted,
            sourceLabel = "LRCLIB",
            score = scoreOnlineSongMatch(query, title, artist, lyrics),
        )
    }
}

private fun parseSuggestPairs(body: String): List<Pair<String, String>> {
    val obj = runCatching { MinimalJson.parse(body) }.getOrNull() as? Map<*, *> ?: return emptyList()
    val data = obj["data"] as? List<*> ?: return emptyList()
    return data.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val title = (map["title_short"] as? String ?: map["title"] as? String).orEmpty().trim()
        val artistMap = map["artist"] as? Map<*, *>
        val artist = (artistMap?.get("name") as? String).orEmpty().trim()
        if (title.isNotBlank() && artist.isNotBlank()) title to artist else null
    }
}

private fun parseItunesPairs(body: String): List<Pair<String, String>> {
    val obj = runCatching { MinimalJson.parse(body) }.getOrNull() as? Map<*, *> ?: return emptyList()
    val results = obj["results"] as? List<*> ?: return emptyList()
    return results.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val title = (map["trackName"] as? String).orEmpty().trim()
        val artist = (map["artistName"] as? String).orEmpty().trim()
        if (title.isNotBlank() && artist.isNotBlank()) title to artist else null
    }
}

private fun parseLyricsOvhBody(body: String): String? {
    val obj = runCatching { MinimalJson.parse(body) }.getOrNull() as? Map<*, *> ?: return null
    return (obj["lyrics"] as? String)?.trim()?.takeIf { it.isNotBlank() }
}

private fun isBetterMatch(candidate: OnlineSongResult, current: OnlineSongResult?): Boolean {
    if (current == null) return true
    if (candidate.score != current.score) return candidate.score > current.score
    return candidate.rawLyrics.length > current.rawLyrics.length
}

private fun deduplicateAndRank(items: List<OnlineSongResult>, query: String): List<OnlineSongResult> {
    val deduped = linkedMapOf<Pair<String, String>, OnlineSongResult>()
    for (item in items) {
        val key = normalizeForSongMatch(item.title) to normalizeForSongMatch(item.artist)
        if (isBetterMatch(item, deduped[key])) deduped[key] = item
    }
    val queryNorm = normalizeForSongMatch(query)
    return deduped.values.sortedWith(
        compareByDescending<OnlineSongResult> { it.score }
            .thenByDescending { normalizeForSongMatch(it.title).startsWith(queryNorm) }
            .thenByDescending { it.rawLyrics.length },
    )
}

private fun encodeQueryParam(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)

private fun encodePathSegment(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
