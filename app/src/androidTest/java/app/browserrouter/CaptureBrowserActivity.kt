package app.browserrouter

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Test-only browser handler; never included in user APKs. */
class CaptureBrowserActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = intent.dataString })
    }
}

const val TEST_BROWSER_LABEL = "Test Browser с длинным названием для проверки переноса текста"
