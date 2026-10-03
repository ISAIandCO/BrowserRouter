package app.browserrouter

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.allOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntentHandlingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val target = instrumentation.context.packageName
    private val capture = ComponentName(target, CaptureBrowserActivity::class.java.name)
    @Before fun setup() {
        ConfigStore(context).save(Config(onboarded = true))
        Intents.init()
        Intents.intending(hasComponent(capture)).respondWith(ActivityResult(Activity.RESULT_OK, null))
    }
    @After fun teardown() { Intents.release(); ConfigStore(context).save(Config()) }

    @Test fun queryFindsInstalledHandlersAndExcludesSelf() {
        val found = browsers(context.packageManager, context.packageName)
        assertTrue(found.any { it.packageName == target })
        assertFalse(found.any { it.packageName == context.packageName })
    }
    @Test fun externalLinkRoutesToConfiguredPackageWithUnchangedUrl() {
        val url = "https://example.ru/path?q=a%26b#fragment"
        ConfigStore(context).save(Config(listOf(Rule(host = "example.ru", action = Action.BROWSER, browser = target))))
        val incoming = webIntent(url).setComponent(ComponentName(context, LinkActivity::class.java))
            .putExtra("untrusted", "do-not-forward")
            .putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://org.telegram.messenger"))
        ActivityScenario.launch<LinkActivity>(incoming).use {
            waitForForward(url)
            val forwarded = Intents.getIntents().first { it.component == capture }
            assertFalse(forwarded.hasExtra("untrusted"))
            assertFalse(forwarded.hasExtra(Intent.EXTRA_REFERRER))
            assertTrue(forwarded.categories.contains(Intent.CATEGORY_BROWSABLE))
        }
    }
    @Test fun sourceRuleWorksWhenReferrerProvidedAndFallbackOtherwise() {
        val url = "http://example.com/"
        ConfigStore(context).save(Config(listOf(Rule(host = "example.com", source = "org.telegram.messenger", action = Action.BROWSER, browser = target))))
        ActivityScenario.launch<LinkActivity>(webIntent(url).setComponent(ComponentName(context, LinkActivity::class.java))
            .putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://org.telegram.messenger"))).use { waitForForward(url) }
    }
    @Test fun unmatchedLinkUsesFallback() {
        val url = "https://unmatched.local/"
        ConfigStore(context).save(Config(fallback = target))
        ActivityScenario.launch<LinkActivity>(webIntent(url).setComponent(ComponentName(context, LinkActivity::class.java))).use { waitForForward(url) }
    }
    @Test fun malformedUrlAndMissingTargetDoNotLaunchOtherApps() {
        ConfigStore(context).save(Config(fallback = "missing.browser"))
        ActivityScenario.launch<LinkActivity>(webIntent("javascript:alert(1)").setComponent(ComponentName(context, LinkActivity::class.java))).use {
            instrumentation.waitForIdleSync()
            assertFalse(Intents.getIntents().any { intent -> intent.component == capture })
        }
    }
    private fun waitForForward(url: String) {
        val end = System.currentTimeMillis() + 10000
        while (System.currentTimeMillis() < end && Intents.getIntents().none { it.component == capture }) {
            instrumentation.waitForIdleSync()
            Thread.sleep(50)
        }
        Intents.intended(allOf(hasComponent(capture), hasAction(Intent.ACTION_VIEW), hasData(url)))
    }
}
