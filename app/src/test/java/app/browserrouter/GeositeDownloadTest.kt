package app.browserrouter

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class GeositeDownloadTest {
    private class Reply(val code: Int = 200, val bytes: ByteArray = byteArrayOf(1, 2, 3),
                        val location: String? = null, val length: Long = bytes.size.toLong()) : HttpURLConnection(URL("https://example.org")) {
        var closed = false
        override fun connect() {}
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getContentLengthLong() = length
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun getHeaderField(name: String?) = if (name == "Location") location else null
    }
    @Test fun acceptsReadySourcesAndGitHubFileOrReleaseLinks() {
        geositeSources.forEach { (_, url) -> assertEquals(url, geositeDownloadUrl(url)) }
        assertEquals("https://raw.githubusercontent.com/owner/repo/release/geosite.dat",
            geositeDownloadUrl("https://github.com/owner/repo/blob/release/geosite.dat"))
        assertEquals("https://raw.githubusercontent.com/owner/repo/release/geosite.dat",
            geositeDownloadUrl("https://github.com/owner/repo/raw/release/geosite.dat"))
        listOf("https://github.com/owner/repo/releases/latest/download/geosite.dat",
            "https://github.com/owner/repo/releases/download/v1/geosite.dat",
            "https://my-cdn.example/geosite.dat?token=opaque").forEach { assertEquals(it, geositeDownloadUrl(it)) }
        listOf("http://example.org/file.dat", "file:///etc/passwd", "https://user:password@example.org/file.dat",
            "https://example.org/file.dat#fragment", "https://github.com/owner/repo", "https://example.org:0/file.dat").forEach {
            assertTrue(it, runCatching { geositeDownloadUrl(it) }.isFailure)
        }
    }
    @Test fun followsHttpsRedirectAndDisconnectsEveryConnection() {
        val first = Reply(code = 302, location = "https://cdn.example/file.dat")
        val second = Reply()
        val seen = mutableListOf<String>()
        val bytes = downloadGeosite(DEFAULT_GEOSITE_URL, connect = { url ->
            seen.add(url.toString()); if (seen.size == 1) first else second
        })
        assertArrayEquals(second.bytes, bytes)
        assertEquals("https://cdn.example/file.dat", seen.last())
        assertTrue(first.closed && second.closed)
        assertFalse(first.instanceFollowRedirects)
    }
    @Test fun rejectsHttpRedirectErrorsEmptyOversizedAndRedirectLoops() {
        val cases = listOf(Reply(code = 302, location = "http://example.org/file.dat"),
            Reply(code = 302), Reply(code = 404), Reply(bytes = byteArrayOf()),
            Reply(length = GeositeDatabase.MAX_BYTES.toLong() + 1))
        cases.forEach { reply ->
            assertTrue(runCatching { downloadGeosite(DEFAULT_GEOSITE_URL, connect = { reply }) }.isFailure)
            assertTrue(reply.closed)
        }
        var redirects = 0
        assertTrue(runCatching { downloadGeosite(DEFAULT_GEOSITE_URL, connect = {
            redirects++; Reply(code = 302, location = "/file.dat")
        }) }.isFailure)
        assertEquals(6, redirects)
        val chunked = Reply(bytes = ByteArray(GeositeDatabase.MAX_BYTES + 1), length = -1)
        assertTrue(runCatching { downloadGeosite(DEFAULT_GEOSITE_URL, connect = { chunked }) }.isFailure)
        assertTrue(chunked.closed)
    }
    @Test fun interruptedDownloadNeverReturnsPartialData() {
        val reply = Reply()
        var checks = 0
        assertTrue(runCatching {
            downloadGeosite(DEFAULT_GEOSITE_URL, checkActive = { if (++checks == 3) throw IOException("cancelled") }, connect = { reply })
        }.isFailure)
        assertTrue(reply.closed)
        val html = Reply(bytes = "<html>not a database</html>".toByteArray())
        assertTrue(runCatching { GeositeDatabase.parse(downloadGeosite(DEFAULT_GEOSITE_URL, connect = { html })) }.isFailure)
    }
    @Test fun updateSettingsAreBackedUpAndStrictlyValidated() {
        val config = Config(geositeUpdates = GeositeUpdates(geositeSources.last().second, true, 6, true))
        assertEquals(config, ConfigCodec.decode(ConfigCodec.encode(config)))
        val old = JSONObject(ConfigCodec.encode(Config())); old.remove("geositeUpdates")
        assertEquals(GeositeUpdates(), ConfigCodec.decode(old.toString()).geositeUpdates)
        listOf(GeositeUpdates(url = "http://example.org/file.dat"), GeositeUpdates(intervalHours = 0),
            GeositeUpdates(intervalHours = 721)).forEach {
            assertTrue(runCatching { ConfigCodec.decode(ConfigCodec.encode(Config(geositeUpdates = it))) }.isFailure)
        }
        val invalid = JSONObject(ConfigCodec.encode(config))
        invalid.getJSONObject("geositeUpdates").put("intervalHours", "24")
        assertTrue(runCatching { ConfigCodec.decode(invalid.toString()) }.isFailure)
    }
}
