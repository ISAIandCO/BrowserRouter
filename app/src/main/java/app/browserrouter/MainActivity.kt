package app.browserrouter

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val model by viewModels<RouterModel>()
    private var browserApps by mutableStateOf<List<BrowserApp>>(emptyList())
    private var sources by mutableStateOf<List<BrowserApp>>(emptyList())
    private var catalogLoading by mutableStateOf(true)
    private var roleHeld by mutableStateOf(false)
    private var imported by mutableStateOf<Config?>(null)
    private var message by mutableStateOf<String?>(null)
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshCatalog() }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val config = model.state.value.config
        if (uri != null && config != null) lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(ConfigCodec.encode(config).toByteArray(Charsets.UTF_8))
                } ?: error("Файл не удалось открыть") }
            }
            message = result.fold({ "Настройки экспортированы" }, { "Ошибка экспорта: ${it.message}" })
        }
    }
    private val import = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)?.use {
                    ConfigCodec.decode(it.readLimited().toString(Charsets.UTF_8))
                } ?: error("Файл не удалось открыть") }
            }
            result.fold({ imported = it }, { message = "Ошибка импорта: ${it.message}" })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RouterApp(model, browserApps, sources, catalogLoading, roleHeld,
                onRequestRole = ::requestBrowserRole,
                onImport = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                onExport = { export.launch("browserrouter-rules.json") },
                imported = imported, dismissImport = { imported = null },
                message = message, dismissMessage = { message = null })
        }
    }
    override fun onResume() { super.onResume(); refreshCatalog() }
    private fun refreshCatalog() {
        val roles = getSystemService(RoleManager::class.java)
        roleHeld = roles.isRoleAvailable(RoleManager.ROLE_BROWSER) && roles.isRoleHeld(RoleManager.ROLE_BROWSER)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { browsers(packageManager, packageName) to sourceApps(packageManager, packageName) }
            }.fold({ (b, s) -> browserApps = b; sources = s }, { message = "Не удалось получить список приложений: ${it.message}" })
            catalogLoading = false
        }
    }
    private fun requestBrowserRole() {
        val roles = getSystemService(RoleManager::class.java)
        runCatching {
            if (roles.isRoleAvailable(RoleManager.ROLE_BROWSER))
                roleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
            else startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }.onFailure { message = "Откройте системные настройки → Приложения по умолчанию → Браузер" }
    }
}
