package app.browserrouter

import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    private fun link(url: String = "https://example.ru/news/item") = WebLink.parse(url)
    private val browser = "org.mozilla.firefox"
    private fun rule(host: String = "example.ru", mode: HostMode = HostMode.DOMAIN) = Rule(host = host, mode = mode, action = Action.BROWSER, browser = browser)

    @Test fun exactOnlyMatchesOneHost() {
        val r = rule(mode = HostMode.EXACT)
        assertTrue(matches(r, link(), null))
        assertFalse(matches(r, link("https://www.example.ru"), null))
    }
    @Test fun domainsIncludeApexAndChildrenButRespectBoundary() {
        val r = rule()
        assertTrue(matches(r, link(), null))
        assertTrue(matches(r, link("https://deep.www.example.ru"), null))
        assertFalse(matches(r, link("https://badexample.ru"), null))
        assertFalse(matches(r, link("https://example.ru.evil.com"), null))
    }
    @Test fun suffixRespectsDotBoundary() {
        val r = rule(".ru", HostMode.SUFFIX)
        assertTrue(matches(r, link(), null))
        assertFalse(matches(r, link("https://example.ru.evil.com"), null))
        assertFalse(matches(r, link("https://notru"), null))
    }
    @Test fun wildcardSupportsSeveralLabelsAndExcludesApex() {
        val r = rule("*.pikabu.ru", HostMode.WILDCARD)
        assertTrue(matches(r, link("https://a.b.pikabu.ru"), null))
        assertFalse(matches(r, link("https://pikabu.ru"), null))
        assertTrue(matches(rule("*.ru", HostMode.WILDCARD), link(), null))
        assertFalse(matches(r, link("https://a.pikabu.ru.evil.com"), null))
    }
    @Test fun regexUsesWholeNormalizedHost() {
        val r = rule(".*\\.ru", HostMode.REGEX)
        assertTrue(matches(r, link(), null))
        assertFalse(matches(r, link("https://example.ru.evil.com"), null))
    }
    @Test fun invalidRegexDoesNotCrashOrMatch() {
        val r = rule("[", HostMode.REGEX)
        assertNotNull(validateRule(r))
        assertFalse(matches(r, link(), null))
    }
    @Test fun idnCaseTrailingDotAndUnicodeDot() {
        assertEquals("xn--e1afmkfd.xn--p1ai", link("https://ПРИМЕР.РФ.:8443/a").host)
        assertTrue(matches(rule("пример.рф"), link("https://xn--e1afmkfd.xn--p1ai"), null))
        assertTrue(matches(rule("*.рф", HostMode.WILDCARD), link("https://пример.рф"), null))
        assertEquals("example.ru", normalizeHost("EXAMPLE。RU."))
    }
    @Test fun portSchemeAndPathAreConjunctive() {
        val r = rule().copy(port = 8443, scheme = "https", pathPrefix = "/news/")
        assertTrue(matches(r, link("https://example.ru:8443/news/a?x=1"), null))
        assertFalse(matches(r, link(), null))
        assertFalse(matches(r, link("http://example.ru:8443/news/a"), null))
        assertFalse(matches(r, link("https://example.ru:8443/NEWS/a"), null))
        assertTrue(matches(rule().copy(port = 443), link(), null))
    }
    @Test fun userInfoDoesNotChangeHostnameAndUrlIsPreserved() {
        val raw = "https://evil.com@example.ru/a%2Fb?q=a%26b#hash"
        val parsed = link(raw)
        assertEquals("example.ru", parsed.host)
        assertEquals(raw, parsed.original)
        assertEquals("/a%2Fb", parsed.path)
    }
    @Test fun disabledRulesAndUnknownSourcesAreSkipped() {
        assertFalse(matches(rule().copy(enabled = false), link(), null))
        assertFalse(matches(rule().copy(source = "org.telegram.messenger"), link(), null))
        assertTrue(matches(rule().copy(source = "org.telegram.messenger"), link(), "org.telegram.messenger"))
    }
    @Test fun firstMatchingRuleWins() {
        val first = rule("*.ru", HostMode.WILDCARD)
        val second = rule().copy(browser = "app.bearium.browser")
        val result = route(Config(listOf(first, second)), link(), null, setOf(browser, second.browser!!), "app.browserrouter")
        assertEquals(Route.Open(browser, first.id), result)
    }
    @Test fun missingFirstTargetDoesNotSilentlyUseSecondRuleOrFallback() {
        val first = rule().copy(browser = "missing.browser")
        val second = rule()
        assertTrue(route(Config(listOf(first, second), browser), link(), null, setOf(browser), "app.browserrouter") is Route.Choose)
    }
    @Test fun fallbackAndAsk() {
        assertEquals(Route.Open(browser, null), route(Config(fallback = browser), link(), null, setOf(browser), "app.browserrouter"))
        assertTrue(route(Config(), link(), null, setOf(browser), "app.browserrouter") is Route.Choose)
        assertTrue(route(Config(fallback = "missing.browser"), link(), null, setOf(browser), "app.browserrouter") is Route.Choose)
        assertTrue(route(Config(listOf(rule().copy(action = Action.ASK)), browser), link(), null, setOf(browser), "app.browserrouter") is Route.Choose)
    }
    @Test fun selfRoutingIsRejectedEvenIfListedAsAvailable() {
        val self = "app.browserrouter"
        assertTrue(route(Config(listOf(rule().copy(browser = self))), link(), null, setOf(self), self) is Route.Choose)
        assertTrue(route(Config(fallback = self), link(), null, setOf(self), self) is Route.Choose)
    }
    @Test fun ipv6AndIpWork() {
        assertEquals("[2001:db8::1]", link("https://[2001:db8::1]:8443/a").host)
        assertTrue(matches(rule("127.0.0.1", HostMode.EXACT), link("http://127.0.0.1/"), null))
    }
    @Test fun malformedUrlsAreRejected() {
        listOf("javascript:alert(1)", "intent://example.ru", "https:///path", "https://example.ru:0", "https://example.ru:65536", "https://example.ru:", "https://a..ru", "https://a.ru..", "https://exa mple.ru").forEach { raw ->
            assertTrue(raw, runCatching { link(raw) }.isFailure)
        }
    }
}
