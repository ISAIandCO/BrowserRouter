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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val model by viewModels<RouterModel>()
    private var browserApps by mutableStateOf<List<BrowserApp>>(emptyList())
    private var sources by mutableStateOf<List<BrowserApp>>(emptyList())
    private var catalogLoading by mutableStateOf(true)
    private var roleHeld by mutableStateOf(false)
    private var geosite by mutableStateOf<GeositeState?>(null)
    private var updateStatus by mutableStateOf(GeositeUpdateStatus())
    private var networkBusy by mutableStateOf(false)
    private var updatePending by mutableStateOf(false)
    private var geositeBusy by mutableStateOf(true)
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

    private val importGeosite = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) changeGeosite {
            contentResolver.openInputStream(uri)?.use { GeositeStore(this).replace(it.readGeositeBytes()) }
                ?: error("Файл не удалось открыть")
        }
    }
    private fun changeGeosite(operation: () -> GeositeState) = lifecycleScope.launch {
        geositeBusy = true
        val result = withContext(Dispatchers.IO) { runCatching(operation) }
        result.fold({
            geosite = it
            GeositeUpdater.cancelManual(this@MainActivity)
            model.saveGeositeUpdates(model.state.value.config?.geositeUpdates?.copy(autoUpdate = false) ?: GeositeUpdates())
            val missing = model.state.value.config?.rules.orEmpty().filter { r ->
                r.enabled && r.mode == HostMode.GEOSITE && it.database.selectorError(r.host) != null
            }
            message = if (missing.isEmpty()) "База geosite готова: ${it.database.names.size} групп"
                else "База geosite готова. Проверьте группы правил: ${missing.joinToString { r -> r.host }}. Для них будет предложен выбор браузера."
        }, { message = "Ошибка базы geosite: ${it.message}. Предыдущая база сохранена." })
        geositeBusy = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { GeositeStore(this@MainActivity).load() } }
            result.fold({ geosite = it }, { message = "База geosite недоступна: ${it.message}" })
            geositeBusy = false
        }
        lifecycleScope.launch {
            val manager = WorkManager.getInstance(this@MainActivity)
            combine(manager.getWorkInfosForUniqueWorkFlow(GeositeUpdater.MANUAL),
                manager.getWorkInfosForUniqueWorkFlow(GeositeUpdater.PERIODIC)) { manual, periodic -> manual to periodic }
                .collect { (manual, periodic) ->
                    networkBusy = (manual + periodic).any { it.state == WorkInfo.State.RUNNING }
                    updatePending = manual.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
                    updateStatus = withContext(Dispatchers.IO) { GeositeUpdater.status(this@MainActivity) }
                    if (!networkBusy) {
                        withContext(Dispatchers.IO) { runCatching { GeositeStore(this@MainActivity).load() } }
                            .onSuccess { geosite = it }
                    }
                }
        }
        setContent {
            RouterApp(model, browserApps, sources, catalogLoading, roleHeld,
                onRequestRole = ::requestBrowserRole,
                onImport = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                onExport = { export.launch("browserrouter-rules.json") },
                imported = imported, dismissImport = { imported = null },
                message = message, dismissMessage = { message = null },
                geosite = geosite, geositeBusy = geositeBusy || networkBusy,
                onGeositeImport = { importGeosite.launch(arrayOf("*/*")) },
                onGeositeReset = { changeGeosite { GeositeStore(this).reset() } },
                updateStatus = updateStatus, updatePending = updatePending,
                onGeositeSave = { settings, update ->
                    GeositeUpdater.cancelManual(this)
                    model.saveGeositeUpdates(settings) {
                        if (update) GeositeUpdater.updateNow(this, settings)
                    }
                })
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
