package app.browserrouter

import android.content.ComponentName
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class LinkErrorTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun missingBrowserOffersAnotherInstalledHandler() {
        ConfigStore(context).save(Config(fallback = "missing.browser"))
        ActivityScenario.launch<LinkActivity>(webIntent("https://example.ru").setComponent(ComponentName(context, LinkActivity::class.java))).use {
            compose.waitUntil(10000) { compose.onAllNodesWithText("Выбранный браузер недоступен. Выберите другой").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(TEST_BROWSER_LABEL).assertExists()
        }
        ConfigStore(context).save(Config())
    }

    @Test fun corruptConfigAllowsManualSelectionWithoutOverwritingFile() {
        val file = File(context.filesDir, "routing-v1.json")
        file.writeText("{broken")
        ActivityScenario.launch<LinkActivity>(webIntent("https://example.ru").setComponent(ComponentName(context, LinkActivity::class.java))).use {
            compose.waitUntil(10000) { compose.onAllNodesWithText("Настройки повреждены или недоступны. Выберите браузер; восстановить настройки можно в BrowserRouter.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(TEST_BROWSER_LABEL).assertExists()
            org.junit.Assert.assertEquals("{broken", file.readText())
        }
        ConfigStore(context).save(Config())
    }
}
