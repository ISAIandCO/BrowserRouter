package app.browserrouter

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import java.io.File

class UiSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun resetConfiguration() {
        ConfigStore(InstrumentationRegistry.getInstrumentation().targetContext).save(Config(onboarded = true))
        compose.activityRule.scenario.recreate()
    }

    @Test fun createRulePersistsAndControlsHaveLabels() {
        compose.waitUntil(10000) { compose.onAllNodesWithText("Создать правило").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Создать правило").performClick()
        compose.onNodeWithText("Сохранить правило").assertIsNotEnabled()
        compose.onNodeWithText("Домен или шаблон").performTextInput("example.ru")
        compose.onNodeWithText("Сохранить правило").assertIsEnabled().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("1. example.ru").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Активность правила example.ru").assertExists()
        compose.onNodeWithContentDescription("Удалить example.ru").assertExists()
        screenshot("rule-list")
        compose.onNodeWithText("Изменить").performClick()
        compose.onNodeWithText("Домен или шаблон").assertTextContains("example.ru")
        screenshot("rule-editor")
    }

    @Test fun settingsAndThemeAreReachable() {
        compose.waitUntil(10000) { compose.onAllNodesWithText("Создать правило").fetchSemanticsNodes().isNotEmpty() }
        screenshot("home")
        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Тёмная").performScrollTo().performClick()
        screenshot("settings-dark")
        compose.onNodeWithText("Светлая").performClick()
        screenshot("settings-light")
        compose.onNodeWithContentDescription("Цвета обоев").assertExists()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val dir = File(instrumentation.targetContext.filesDir, "screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(dir, "$name-${args.getString("visualVariant", "default")}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
