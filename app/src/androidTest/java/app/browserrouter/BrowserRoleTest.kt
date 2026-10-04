package app.browserrouter

import android.app.role.RoleManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class BrowserRoleTest {
    @Test fun androidAcceptsRouterAsBrowserRoleHolder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val role = context.getSystemService(RoleManager::class.java)
        assertTrue(role.isRoleAvailable(RoleManager.ROLE_BROWSER))
        fun command(value: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(value)
        ).use { input -> input.readBytes().toString(Charsets.UTF_8)
        }
        try {
            val output = command("cmd role add-role-holder --user 0 android.app.role.BROWSER ${context.packageName}")
            assertTrue(output, role.isRoleHeld(RoleManager.ROLE_BROWSER))
        } finally {
            command("cmd role remove-role-holder --user 0 android.app.role.BROWSER ${context.packageName}")
        }
    }
}
