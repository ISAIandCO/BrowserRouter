@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package app.browserrouter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LinkActivity : ComponentActivity() {
    private var config by mutableStateOf(Config())
    private var link by mutableStateOf<WebLink?>(null)
    private var choices by mutableStateOf<List<BrowserApp>>(emptyList())
    private var error by mutableStateOf<String?>(null)
    private var source: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = sourcePackage()
        lifecycleScope.launch {
            try {
                require(intent.action == android.content.Intent.ACTION_VIEW) { "Ожидалась веб-ссылка" }
                val parsed = withContext(Dispatchers.Default) { WebLink.parse(intent.dataString.orEmpty()) }
                link = parsed
                val apps = withContext(Dispatchers.IO) { browsers(packageManager, packageName, parsed.original) }
                choices = apps
                val loaded = withContext(Dispatchers.IO) { runCatching { ConfigStore(this@LinkActivity).load() } }
                if (loaded.isFailure) {
                    error = "Настройки повреждены или недоступны. Выберите браузер; восстановить настройки можно в BrowserRouter."
                } else {
                    config = loaded.getOrThrow()
                    GeositeUpdater.schedule(this@LinkActivity, config.geositeUpdates)
                    val geosite = if (config.rules.any { it.enabled && it.mode == HostMode.GEOSITE })
                        withContext(Dispatchers.IO) { runCatching { GeositeStore(this@LinkActivity).load().database }.getOrNull() }
                        else null
                    when (val decision = withContext(Dispatchers.Default) {
                        route(config, parsed, source, apps.map { it.packageName }.toSet(), packageName, geosite)
                    }) {
                        is Route.Open -> {
                            error = openBrowser(parsed, apps.first { it.packageName == decision.packageName })
                            if (error == null) finish()
                        }
                        is Route.Choose -> error = decision.reason
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Ссылка не обработана: ${e.message}"
            } finally {
                if (isActive && !isFinishing && !isDestroyed) showChooser()
            }
        }
    }

    // Automatic routes never attach Compose content to the transparent routing window.
    private fun showChooser() {
        enableEdgeToEdge()
        setContent {
            RouterTheme(config) {
                Scaffold(topBar = { TopAppBar(title = { Text("Открыть ссылку") }, navigationIcon = {
                    TextButton(onClick = { finish() }) { Text("Назад") }
                }) }) { padding ->
                    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        item { Text(link?.host ?: "BrowserRouter", style = MaterialTheme.typography.headlineMedium) }
                        item { Text(if (source == null) "Источник не определён" else "Источник (по данным Intent): $source",
                            style = MaterialTheme.typography.bodyMedium) }
                        error?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.error) } }
                        if (link != null && choices.isEmpty()) item {
                            Text("Не найдено приложений для этой ссылки. Установите или включите браузер и попробуйте снова.")
                        }
                        items(choices, key = { it.packageName }) { app ->
                            AppRow(app) {
                                link?.let { current ->
                                    error = openBrowser(current, app)
                                    if (error == null) finish()
                                }
                            }
                        }
                        item { TextButton(onClick = { finish() }) { Text("Отмена") } }
                    }
                }
            }
        }
    }
}
