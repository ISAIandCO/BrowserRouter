package app.browserrouter

import org.junit.Assert.*
import org.junit.Test

class ConfigCodecTest {
    private val config = Config(listOf(Rule(host = "пример.рф", action = Action.BROWSER, browser = "app.bearium.browser")),
        fallback = "org.mozilla.firefox", theme = ThemeMode.DARK, onboarded = true)
    @Test fun exportRoundTripPreservesOrderAndSettings() {
        assertEquals(config, ConfigCodec.decode(ConfigCodec.encode(config)))
    }
    @Test fun unknownSchemaDoesNotSilentlyReset() {
        assertTrue(runCatching { ConfigCodec.decode(ConfigCodec.encode(config).replace("\"schemaVersion\": 2", "\"schemaVersion\": 999")) }.isFailure)
    }
    @Test fun duplicateIdsMalformedAndOversizedFilesAreRejected() {
        assertTrue(runCatching { ConfigCodec.decode(ConfigCodec.encode(config.copy(rules = config.rules + config.rules))) }.isFailure)
        assertTrue(runCatching { ConfigCodec.decode("{") }.isFailure)
        assertTrue(runCatching { ConfigCodec.decode(" ".repeat(ConfigCodec.MAX_BYTES + 1)) }.isFailure)
    }
    @Test fun invalidRuleIsRejectedAtImport() {
        val invalid = config.copy(rules = listOf(Rule(host = "[", mode = HostMode.REGEX)))
        assertTrue(runCatching { ConfigCodec.decode(ConfigCodec.encode(invalid)) }.isFailure)
    }
    @Test fun booleanStringsAreNotCoerced() {
        val text = ConfigCodec.encode(config).replace("\"enabled\": true", "\"enabled\": \"false\"")
        assertTrue(runCatching { ConfigCodec.decode(text) }.isFailure)
    }
    @Test fun legacySuffixRulesMigrateWithoutChangingHostOrderOrTarget() {
        val old = config.copy(rules = listOf(
            Rule(id = "zone", host = ".com", action = Action.BROWSER, browser = "org.mozilla.firefox"),
            config.rules.first(),
        ))
        for (version in listOf(1, 2)) {
            val json = ConfigCodec.encode(old).replace("\"mode\": \"DOMAIN\"", "\"mode\": \"SUFFIX\"")
                .replace("\"schemaVersion\": 2", "\"schemaVersion\": $version")
            val migrated = ConfigCodec.decode(json)
            assertEquals(old, migrated)
            assertFalse(ConfigCodec.encode(migrated).contains("\"SUFFIX\""))
        }
    }
}
