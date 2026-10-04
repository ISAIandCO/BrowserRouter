package app.browserrouter

import com.google.protobuf.CodedOutputStream
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

class GeositeTest {
    private fun proto(write: (CodedOutputStream) -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = CodedOutputStream.newInstance(bytes)
        write(out); out.flush()
        return bytes.toByteArray()
    }
    private fun entry(type: Int, value: String, attr: String? = null) = proto {
        it.writeEnum(1, type); it.writeString(2, value)
        if (attr != null) it.writeByteArray(3, proto { a -> a.writeString(1, attr); a.writeBool(2, true) })
    }
    private fun database(vararg entries: ByteArray): ByteArray = proto { list ->
        list.writeByteArray(1, proto { group ->
            group.writeString(1, "TEST")
            entries.forEach { group.writeByteArray(2, it) }
            group.writeString(99, "future metadata")
        })
    }
    private fun sample() = GeositeDatabase.parse(database(
        entry(2, "пример.рф"), entry(3, "exact.ru"), entry(0, "keyword"),
        entry(1, "ad[0-9]+\\.example", "ads"), entry(2, "cdn.example", "cdn"),
    ))

    @Test fun typesRespectV2raySemanticsAndNormalizedHosts() {
        val db = sample()
        assertTrue(db.matches("geosite:TEST", normalizeHost("Пример.РФ.")))
        assertTrue(db.matches("test", normalizeHost("www.пример.рф")))
        assertFalse(db.matches("test", normalizeHost("пример.рф.evil.com")))
        assertTrue(db.matches("test", "exact.ru"))
        assertFalse(db.matches("test", "www.exact.ru"))
        assertTrue(db.matches("test", "has-keyword.example"))
        assertTrue(db.matches("test", "x.ad12.example.org")) // Search, not whole-host regex match.
        assertFalse(db.matches("test", "ordinary.example"))
    }
    @Test fun attributesFilterAndUnknownGroupsAreReported() {
        val db = sample()
        assertTrue(db.matches("test@ads", "ad1.example"))
        assertFalse(db.matches("test@ads", "cdn.example"))
        assertNotNull(db.selectorError("test@missing"))
        assertNotNull(db.selectorError("missing"))
        assertNotNull(db.selectorError("test@ads@cdn"))
        assertEquals(listOf("geolocation-!cn", "!cn"), geositeSelector("GEOSITE:geolocation-!cn@!cn"))
    }
    @Test fun routingPreservesPrioritySourceOtherConditionsAndFallback() {
        val first = Rule(host = "geosite:test@ads", mode = HostMode.GEOSITE,
            source = "org.telegram.messenger", scheme = "https", port = 443, pathPrefix = "/news",
            action = Action.BROWSER, browser = "org.mozilla.firefox")
        val second = Rule(host = "example", action = Action.BROWSER, browser = "app.bearium.browser")
        val config = Config(listOf(first, second), fallback = "com.android.chrome")
        val apps = setOf(first.browser!!, second.browser!!, config.fallback!!)
        fun run(url: String, source: String? = "org.telegram.messenger", db: GeositeDatabase? = sample()) =
            route(config, WebLink.parse(url), source, apps, "app.browserrouter", db)
        assertEquals(Route.Open(first.browser!!, first.id), run("https://ad1.example/news"))
        assertEquals(Route.Open(second.browser!!, second.id), run("https://cdn.example/news"))
        assertEquals(Route.Open(second.browser!!, second.id), run("https://ad1.example/news", null))
        assertEquals(Route.Open(second.browser!!, second.id), run("http://ad1.example/news"))
        assertEquals(Route.Open(config.fallback!!, null), run("https://outside.ru"))
        assertTrue(run("https://ad1.example/news", db = null) is Route.Choose)
        val missing = config.copy(rules = listOf(first.copy(host = "missing")))
        assertTrue(route(missing, WebLink.parse("https://outside.ru/news"), "org.telegram.messenger", apps,
            "app.browserrouter", sample()) is Route.Choose)
    }
    @Test fun corruptUnsupportedAndOversizedImportsFailBeforeReplacement() {
        listOf(byteArrayOf(), byteArrayOf(10, 100, 1), byteArrayOf(11),
            database(entry(99, "example.ru")), database(entry(1, "[")),
            database(entry(1, "(a)\\1")), database(entry(2, "bad..ru")),
            database(entry(2, "example.ru")).let { it + it },
        ).forEach { assertTrue(runCatching { GeositeDatabase.parse(it) }.isFailure) }
        assertTrue(runCatching { ByteArray(GeositeDatabase.MAX_BYTES + 1).inputStream().readGeositeBytes() }.isFailure)
        listOf("", "geosite:", "google@", "../google", "google:ads").forEach {
            assertNotNull(validateRule(Rule(host = it, mode = HostMode.GEOSITE)))
        }
    }
    @Test fun bundledDatabaseIsPinnedCompleteAndSupportsRealGroups() {
        val bytes = File("src/main/assets/geosite.dat").readBytes()
        val source = File("src/main/assets/geosite-source.txt").readText()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertTrue(source.contains("SHA256: $hash"))
        val db = GeositeDatabase.parse(bytes)
        assertEquals(1542, db.names.size)
        assertTrue(db.matches("google", "www.google.com"))
        assertTrue(db.matches("youtube", "www.youtube.com"))
        assertFalse(db.matches("youtube", "youtube.com.evil.example"))
        assertTrue(db.matches("google@ads", "adservice.google.com"))
        assertNotNull(db.selectorError("a-group-that-does-not-exist"))
    }
    @Test fun configRoundTripAndOldSchemaMigration() {
        val config = Config(rules = listOf(Rule(host = "geosite:test@ads", mode = HostMode.GEOSITE)))
        assertEquals(config, ConfigCodec.decode(ConfigCodec.encode(config)))
        val old = Config(rules = listOf(Rule(host = "example.ru")))
        assertEquals(old, ConfigCodec.decode(ConfigCodec.encode(old).replace("\"schemaVersion\": 2", "\"schemaVersion\": 1")))
    }
}
