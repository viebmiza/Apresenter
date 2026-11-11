package org.churchpresenter.songs

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OnlineSongSearchClientTest {

    @Test
    fun `direct LRCLIB query parses plain lyrics and skips instrumental results`() = runBlocking {
        val lyrics = "Tu és tudo o que eu mais quero\nO meu fôlego, tu és\n\n" +
            "Pai, eu amo sua presença\nTeu sorriso é vida em mim\n\n" +
            "Tu és tudo o que eu mais quero\nO meu fôlego, tu és"
        val lrclibJson = """
            [
              {"trackName":"Intro","artistName":"Band","instrumental":true,"plainLyrics":"la la"},
              {"trackName":"Lugar Secreto","artistName":"Gabriela Rocha","albumName":"Ao Vivo","plainLyrics":"$lyrics"}
            ]
        """.trimIndent().replace("\n", "\\n")
        val singleEntryJson = """
            [
              {"trackName":"Intro","artistName":"Band","instrumental":true,"plainLyrics":"la la"},
              {"trackName":"Lugar Secreto","artistName":"Gabriela Rocha","albumName":"Ao Vivo",
               "instrumental":false,"plainLyrics":"${lyrics.replace("\n", "\\n")}"}
            ]
        """.trimIndent()
        val client = OnlineSongSearchClient(
            httpGet = { url -> if ("lrclib" in url) singleEntryJson else null },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val outcome = client.search("Lugar Secreto Gabriela Rocha")

        val success = assertIs<OnlineSongSearchOutcome.Success>(outcome)
        assertEquals(1, success.results.size)
        val item = success.results.single()
        assertEquals("Lugar Secreto", item.title)
        assertEquals("Gabriela Rocha", item.artist)
        assertTrue(item.formattedLyrics.contains("{Chorus}"))
        assertTrue(item.formattedLyrics.contains("[Verse 1]"))
        assertTrue(lrclibJson.isNotEmpty())
    }

    @Test
    fun `synced LRC lyrics have timestamps and metadata stripped when plain lyrics are absent`() = runBlocking {
        val synced = "[ti:Amazing Grace]\\n[00:10.50]Amazing grace how sweet the sound\\n" +
            "[00:15.20]That saved a wretch like me"
        val lrclibJson = """
            [
              {"trackName":"Amazing Grace","artistName":"Hymn","instrumental":false,
               "plainLyrics":"","syncedLyrics":"$synced"}
            ]
        """.trimIndent()
        val client = OnlineSongSearchClient(
            httpGet = { url -> if ("lrclib" in url) lrclibJson else null },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val outcome = client.search("Amazing Grace")

        val success = assertIs<OnlineSongSearchOutcome.Success>(outcome)
        val lines = success.results.single().formattedLyrics
        assertTrue("Amazing grace how sweet the sound" in lines)
        assertTrue(lines.none { it.startsWith("[00:") || it.startsWith("[ti:") })
    }

    @Test
    fun `lyric phrase query falls back to discovery sources and ranks matching lyrics first`() = runBlocking {
        val suggestJson = """
            {"data":[
              {"title":"Wrong Song","artist":{"name":"Other"}},
              {"title":"Lugar Secreto","artist":{"name":"Gabriela Rocha"}}
            ]}
        """.trimIndent()
        val ovhWrong = """{"lyrics":"Completely different words"}"""
        val ovhMatch = """{"lyrics":"Tu és tudo o que eu mais quero\nO meu fôlego, tu és\n\nEm teus braços"}"""

        val client = OnlineSongSearchClient(
            httpGet = { url ->
                when {
                    "lrclib" in url -> "[]"
                    "suggest" in url -> suggestJson
                    "Wrong%20Song" in url -> ovhWrong
                    "Lugar%20Secreto" in url -> ovhMatch
                    else -> null
                }
            },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val outcome = client.search("tu es tudo o que eu mais quero")

        val success = assertIs<OnlineSongSearchOutcome.Success>(outcome)
        assertEquals("Lugar Secreto", success.results.first().title)
        assertEquals("Lyrics.ovh", success.results.first().sourceLabel)
    }

    @Test
    fun `network failure across all sources reports NetworkError instead of empty`(): Unit = runBlocking {
        val client = OnlineSongSearchClient(
            httpGet = { throw java.io.IOException("offline") },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val outcome = client.search("Ousado Amor")

        assertIs<OnlineSongSearchOutcome.NetworkError>(outcome)
    }

    @Test
    fun `single long unbroken block is chunked into presentable four-line verses`() {
        val unbroken = (1..8).joinToString("\n") { "Line $it" }

        val formatted = formatImportedLyrics(unbroken)

        assertEquals(
            listOf(
                "[Verse 1]", "Line 1", "Line 2", "Line 3", "Line 4",
                "",
                "[Verse 2]", "Line 5", "Line 6", "Line 7", "Line 8",
            ),
            formatted,
        )
    }

    @Test
    fun `client fetches over loopback HTTP server cleanly`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val payloadJson = """
            [{"trackName":"Bondade de Deus","artistName":"Isaias Saad",
              "plainLyrics":"Te amo Deus\nTua gra\u00e7a nunca falha"}]
        """.trimIndent()
        server.createContext("/search") { exchange ->
            val payload = payloadJson.toByteArray()
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}/search"
            val client = OnlineSongSearchClient(
                lrclibBaseUrl = base,
                itunesBaseUrl = "http://127.0.0.1:1/unused",
                lyricsOvhSuggestBaseUrl = "http://127.0.0.1:1/unused",
                lyricsOvhV1BaseUrl = "http://127.0.0.1:1/unused",
                ioDispatcher = Dispatchers.IO,
            )

            val outcome = client.search("Bondade de Deus")
            val success = assertIs<OnlineSongSearchOutcome.Success>(outcome)
            assertEquals("Bondade de Deus", success.results.single().title)
            assertTrue("Tua graça nunca falha" in success.results.single().rawLyrics)
        } finally {
            server.stop(0)
        }
    }
}
