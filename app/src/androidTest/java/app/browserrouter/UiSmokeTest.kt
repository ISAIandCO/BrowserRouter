package app.browserrouter

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import androidx.lifecycle.ViewModelProvider
import android.util.Log
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap

class UiSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun resetConfiguration() {
        ConfigStore(InstrumentationRegistry.getInstrumentation().targetContext).save(Config(onboarded = true))
        compose.activityRule.scenario.onActivity { ViewModelProvider(it)[RouterModel::class.java].reload() }
        compose.activityRule.scenario.recreate()
    }
    @After fun captureCurrentState() {
        runCatching { screenshot("last-state") }
    }

    private fun waitForHome() {
        try {
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Создать правило").fetchSemanticsNodes().isNotEmpty() }
        } catch (error: Throwable) {
            compose.runOnIdle {
                Log.e("UiSmoke", "Model: ${ViewModelProvider(compose.activity)[RouterModel::class.java].state.value}")
            }
            Log.e("UiSmoke", compose.onRoot().printToString())
            throw error
        }
    }

    @Test fun createRulePersistsAndControlsHaveLabels() {
        waitForHome()
        compose.onNodeWithContentDescription("Создать правило").performClick()
        compose.onNodeWithText("Сохранить правило").assertIsNotEnabled()
        compose.onNodeWithText("Домен или шаблон").performTextInput("example.ru")
        compose.onNodeWithText("Выбирать при открытии").performScrollTo().performClick()
        compose.onNodeWithText("Поиск приложения").performTextInput("Test Browser")
        compose.onNodeWithText("Test Browser").performClick()
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
        waitForHome()
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
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name-${args.getString("visualVariant", "default")}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BrowserRouterTests")
        }
        val resolver = instrumentation.targetContext.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(uri)?.use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
