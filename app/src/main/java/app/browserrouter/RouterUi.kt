@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package app.browserrouter

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RouterApp(
    model: RouterModel, apps: List<BrowserApp>, sources: List<BrowserApp>, catalogLoading: Boolean,
    roleHeld: Boolean, onRequestRole: () -> Unit, onImport: () -> Unit, onExport: () -> Unit,
    imported: Config?, dismissImport: () -> Unit, message: String?, dismissMessage: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val config = state.config
    var screen by rememberSaveable { mutableStateOf("rules") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var reset by rememberSaveable { mutableStateOf(false) }
    RouterTheme(config ?: Config()) {
        if (editingId != null && config != null) {
            RuleEditor(config.rules.firstOrNull { it.id == editingId }, apps, sources,
                onCancel = { editingId = null }, onSave = { model.saveRule(it) { editingId = null } })
        } else {
            BackHandler(enabled = screen != "rules") { screen = "rules" }
            Scaffold(
                topBar = { LargeTopAppBar(title = { Text(if (screen == "rules") "BrowserRouter" else "Настройки") }) },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(screen == "rules", { screen = "rules" },
                            icon = { Icon(painterResource(R.drawable.ic_route), null) }, label = { Text("Правила") })
                        NavigationBarItem(screen == "settings", { screen = "settings" },
                            icon = { Icon(painterResource(R.drawable.ic_settings), null) }, label = { Text("Настройки") })
                    }
                },
                floatingActionButton = {
                    if (screen == "rules" && config != null) ExtendedFloatingActionButton(
                        text = { Text("Создать правило") }, icon = { Icon(painterResource(R.drawable.ic_add), null) },
                        onClick = { editingId = "new" })
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    when {
                        state.loading -> LoadingIndicator(Modifier.padding(32.dp).size(64.dp))
                        config == null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Нужно восстановить настройки", style = MaterialTheme.typography.headlineSmall)
                            Text(state.error.orEmpty())
                            Button(onClick = onImport) { Text("Импортировать файл") }
                            OutlinedButton(onClick = { reset = true }) { Text("Сбросить настройки") }
                        }
                        else -> AnimatedContent(screen, label = "Экран") { destination ->
                            if (destination == "rules") RulesScreen(config, apps, sources, roleHeld, onRequestRole,
                                onDismissIntro = { model.update { it.copy(onboarded = true) } },
                                onCreate = { editingId = "new" }, onEdit = { editingId = it },
                                onToggle = { rule -> model.saveRule(rule.copy(enabled = !rule.enabled)) },
                                onMove = model::move, onDelete = { deleteId = it })
                            else SettingsScreen(config, apps, catalogLoading, roleHeld, onRequestRole,
                                onUpdate = { transform -> model.update(transform) }, onImport, onExport, { reset = true })
                        }
                    }
                }
            }
        }
        deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null },
            title = { Text("Удалить правило?") }, text = { Text("Это действие удалит выбранный маршрут.") },
            confirmButton = { TextButton(onClick = { model.update { c -> c.copy(rules = c.rules.filterNot { it.id == id }) }; deleteId = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Отмена") } }) }
        if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("Сбросить настройки?") },
            text = { Text("Все правила, fallback и настройки темы будут удалены. Сначала экспортируйте их, если они нужны.") },
            confirmButton = { TextButton(onClick = { model.reset(); reset = false }) { Text("Сбросить") } },
            dismissButton = { TextButton(onClick = { reset = false }) { Text("Отмена") } })
        imported?.let { data ->
            AlertDialog(onDismissRequest = dismissImport, title = { Text("Импорт: ${data.rules.size} правил") },
                text = { Text("Добавление сохранит текущие настройки и поставит новые правила в конец. Замена полностью перезапишет настройки содержимым файла.") },
                confirmButton = {
                    Column {
                        TextButton(onClick = { model.importConfig(data, false); dismissImport() },
                            enabled = config != null && config.rules.size + data.rules.size <= ConfigCodec.MAX_RULES) { Text("Добавить к существующим") }
                        TextButton(onClick = { model.importConfig(data, true); dismissImport() }) { Text("Заменить все настройки") }
                    }
                }, dismissButton = { TextButton(onClick = dismissImport) { Text("Отмена") } })
        }
        val dialogMessage = message ?: state.error?.takeIf { config != null }
        if (dialogMessage != null) AlertDialog(onDismissRequest = { dismissMessage(); model.clearError() },
            title = { Text("BrowserRouter") }, text = { Text(dialogMessage) },
            confirmButton = { TextButton(onClick = { dismissMessage(); model.clearError() }) { Text("Понятно") } })
    }
}

@Composable
private fun RulesScreen(
    config: Config, apps: List<BrowserApp>, sources: List<BrowserApp>, roleHeld: Boolean, onRequestRole: () -> Unit,
    onDismissIntro: () -> Unit, onCreate: () -> Unit, onEdit: (String) -> Unit,
    onToggle: (Rule) -> Unit, onMove: (String, Int) -> Unit, onDelete: (String) -> Unit,
) {
    LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!config.onboarded) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.ic_route), null, Modifier.size(48.dp))
                    Text("Каждой ссылке — свой браузер", style = MaterialTheme.typography.headlineSmall)
                    Text("Назначьте BrowserRouter браузером по умолчанию и создайте первое правило. Сайты откроются в выбранных вами приложениях.")
                    Button(onClick = onRequestRole, shapes = ButtonDefaults.shapes()) { Text(if (roleHeld) "Проверить назначение" else "Назначить по умолчанию") }
                    FilledTonalButton(onClick = onCreate) { Text("Создать первое правило") }
                    TextButton(onClick = onDismissIntro) { Text("Скрыть подсказку") }
                }
            }
        }
        item {
            AssistChip(onClick = onRequestRole, label = { Text(if (roleHeld) "По умолчанию · включено" else "По умолчанию · не назначен") })
        }
        item { Text("Первое активное совпадение определяет браузер", style = MaterialTheme.typography.titleMedium) }
        if (config.rules.isEmpty()) item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Пока нет маршрутов", style = MaterialTheme.typography.headlineSmall)
                    Text("Например, *.ru можно отправить в Bearium, а *.com — в Firefox. Выбор браузеров всегда ваш.")
                    FilledTonalButton(onClick = onCreate) { Text("Добавить правило") }
                }
            }
        }
        itemsIndexed(config.rules, key = { _, r -> r.id }) { index, rule ->
            Card(Modifier.animateItem().fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = if (rule.enabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${index + 1}. ${rule.host}", style = MaterialTheme.typography.titleLarge)
                            Text(modeLabel(rule.mode), style = MaterialTheme.typography.labelLarge)
                        }
                        Switch(rule.enabled, { onToggle(rule) }, Modifier.semantics { contentDescription = "Активность правила ${rule.host}" })
                    }
                    Text(rule.source?.let { pkg -> "Источник: ${sources.firstOrNull { it.packageName == pkg }?.label ?: pkg} (если определён)" } ?: "Из любого приложения")
                    if (rule.scheme != null || rule.port != null || rule.pathPrefix != null)
                        Text(listOfNotNull(rule.scheme, rule.port?.let { "Порт $it" }, rule.pathPrefix).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    Text("→ ${if (rule.action == Action.ASK) "Выбрать при открытии" else appLabel(apps, rule.browser)}",
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { onEdit(rule.id) }) { Text("Изменить") }
                        IconButton(onClick = { onMove(rule.id, -1) }, enabled = index > 0) {
                            Icon(painterResource(R.drawable.ic_up), "Повысить приоритет ${rule.host}")
                        }
                        IconButton(onClick = { onMove(rule.id, 1) }, enabled = index < config.rules.lastIndex) {
                            Icon(painterResource(R.drawable.ic_down), "Понизить приоритет ${rule.host}")
                        }
                        IconButton(onClick = { onDelete(rule.id) }) { Icon(painterResource(R.drawable.ic_delete), "Удалить ${rule.host}") }
                    }
                }
            }
        }
        item {
            Text("Остальные ссылки → ${appLabel(apps, config.fallback)}", style = MaterialTheme.typography.titleMedium)
        }
    }
}

fun appLabel(apps: List<BrowserApp>, pkg: String?): String = if (pkg == null) "Выбор браузера" else
    apps.firstOrNull { it.packageName == pkg }?.label ?: "Недоступен: $pkg"

@Composable
fun AppRow(app: BrowserApp, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val bitmap = remember(app.packageName, app.icon) { app.icon?.toBitmap(48, 48)?.asImageBitmap() }
            if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
            else Icon(painterResource(R.drawable.ic_route), null, Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun AppPicker(title: String, apps: List<BrowserApp>, allowNone: Boolean = true,
              onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).imePadding()) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(query, { query = it }, label = { Text("Поиск приложения") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), singleLine = true)
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (allowNone) item {
                    FilledTonalButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (title.contains("Источник")) "Любое приложение" else "Выбирать при открытии")
                    }
                }
                val filtered = apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
                if (filtered.isEmpty()) item { Text("Подходящих приложений нет. Проверьте, что они установлены и включены.") }
                itemsIndexed(filtered, key = { _, a -> a.packageName }) { _, app -> AppRow(app) { onSelect(app.packageName) } }
            }
        }
    }
}
