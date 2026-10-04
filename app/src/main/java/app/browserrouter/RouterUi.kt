@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.browserrouter

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.zIndex
import kotlinx.coroutines.isActive
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
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
    geosite: GeositeState?, geositeBusy: Boolean, onGeositeImport: () -> Unit, onGeositeReset: () -> Unit,
    updateStatus: GeositeUpdateStatus, updatePending: Boolean, onGeositeSave: (GeositeUpdates, Boolean) -> Unit,
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
                onCancel = { editingId = null }, onSave = { model.saveRule(it) { editingId = null } }, geosite = geosite?.database)
        } else {
            BackHandler(enabled = screen != "rules") { screen = "rules" }
            Scaffold(
                topBar = { TopAppBar(title = { Text(if (screen == "rules") "Правила" else "Настройки") }) },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(screen == "rules", { screen = "rules" },
                            icon = { Icon(painterResource(R.drawable.ic_route), null) }, label = { Text("Правила") })
                        NavigationBarItem(screen == "settings", { screen = "settings" },
                            icon = { Icon(painterResource(R.drawable.ic_settings), null) }, label = { Text("Настройки") })
                    }
                },
                floatingActionButton = {
                    if (screen == "rules" && config != null) FloatingActionButton(
                        onClick = { editingId = "new" }) {
                        Icon(painterResource(R.drawable.ic_add), "Создать правило")
                    }
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
                                onUpdate = { transform -> model.update(transform) }, onImport, onExport, { reset = true },
                                geosite, geositeBusy, onGeositeImport, onGeositeReset, updateStatus, updatePending, onGeositeSave)
                        }
                    }
                }
            }
        }
        deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null },
            title = { Text("Удалить правило?") },
            confirmButton = { TextButton(onClick = { model.update { c -> c.copy(rules = c.rules.filterNot { it.id == id }) }; deleteId = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Отмена") } }) }
        if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("Сбросить настройки?") },
            text = { Text("Все правила и настройки будут удалены.") },
            confirmButton = { TextButton(onClick = { model.reset(); reset = false }) { Text("Сбросить") } },
            dismissButton = { TextButton(onClick = { reset = false }) { Text("Отмена") } })
        imported?.let { data ->
            AlertDialog(onDismissRequest = dismissImport, title = { Text("Импорт: ${data.rules.size} правил") },
                text = { Text("Добавить правила или заменить текущие настройки?") },
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
    onToggle: (Rule) -> Unit, onMove: (String, Int, (List<Rule>) -> Unit) -> Unit, onDelete: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    var ordered by remember(config.rules) { mutableStateOf(config.rules) }
    val currentConfig by rememberUpdatedState(config)
    val currentMove by rememberUpdatedState(onMove)
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragTop by remember { mutableFloatStateOf(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragHeight by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    fun moveDragged() {
        val id = draggedId ?: return
        val visible = listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
            (item.key as? String)?.let { it to (item.offset + item.size / 2f) }
        }
        ordered = moveRule(ordered, id, ruleDropTarget(ordered, id, dragTop + dragOffset + dragHeight / 2f, visible))
    }
    fun cancelDrag() { draggedId = null; ordered = currentConfig.rules }
    fun drop() {
        val id = draggedId ?: return
        val delta = ordered.indexOfFirst { it.id == id } - currentConfig.rules.indexOfFirst { it.id == id }
        draggedId = null
        if (delta != 0) currentMove(id, delta) { ordered = it }
    }
    LaunchedEffect(config.rules) { cancelDrag() }
    LaunchedEffect(draggedId) {
        var previous = withFrameNanos { it }
        while (draggedId != null && isActive) {
            val now = withFrameNanos { it }
            val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f)
            previous = now
            val layout = listState.layoutInfo
            val speed = when {
                pointerY < layout.viewportStartOffset + edge -> -(layout.viewportStartOffset + edge - pointerY) / edge
                pointerY > layout.viewportEndOffset - edge -> (pointerY - layout.viewportEndOffset + edge) / edge
                else -> 0f
            }.coerceIn(-1f, 1f)
            if (speed != 0f) listState.scrollBy(speed * edge * 12f * seconds)
            moveDragged()
        }
    }
    LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxWidth(), state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!config.onboarded) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.ic_route), null, Modifier.size(48.dp))
                    Text("Каждой ссылке — свой браузер", style = MaterialTheme.typography.headlineSmall)
                    Text("Назначьте браузером по умолчанию и добавьте правило.")
                    Button(onClick = onRequestRole, shapes = ButtonDefaults.shapes()) { Text(if (roleHeld) "Проверить назначение" else "Назначить по умолчанию") }
                    FilledTonalButton(onClick = onCreate) { Text("Создать первое правило") }
                    TextButton(onClick = onDismissIntro) { Text("Скрыть подсказку") }
                }
            }
        }
        item {
            AssistChip(onClick = onRequestRole, label = { Text(if (roleHeld) "По умолчанию · включено" else "По умолчанию · не назначен") })
        }
        item { Text("Выше — приоритетнее", style = MaterialTheme.typography.titleMedium) }
        if (config.rules.isEmpty()) item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Пока нет маршрутов", style = MaterialTheme.typography.headlineSmall)
                    FilledTonalButton(onClick = onCreate) { Text("Добавить правило") }
                }
            }
        }
        itemsIndexed(ordered, key = { _, r -> r.id }) { index, rule ->
            val dragging = draggedId == rule.id
            val moveAction: (Int) -> Unit = { delta -> currentMove(rule.id, delta) { ordered = it } }
            val modifier = Modifier.animateItem(placementSpec = if (dragging) null else spring())
                .fillMaxWidth().zIndex(if (dragging) 1f else 0f)
                .graphicsLayer {
                    translationY = if (dragging) dragTop + dragOffset -
                        (listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == rule.id }?.offset ?: dragTop.toInt()) else 0f
                }
                .semantics(mergeDescendants = true) {
                    customActions = buildList {
                        if (index > 0) add(CustomAccessibilityAction("Повысить приоритет") { moveAction(-1); true })
                        if (index < ordered.lastIndex) add(CustomAccessibilityAction("Понизить приоритет") { moveAction(1); true })
                    }
                }
                .onKeyEvent { event ->
                    val delta = when (event.key) { Key.DirectionUp -> -1; Key.DirectionDown -> 1; else -> 0 }
                    if (event.type == KeyEventType.KeyDown && event.isAltPressed && delta != 0 && index + delta in ordered.indices) {
                        moveAction(delta)
                        true
                    } else false
                }.focusable()
                .pointerInput(rule.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == rule.id }?.let {
                                draggedId = rule.id
                                dragTop = it.offset.toFloat()
                                dragHeight = it.size.toFloat()
                                dragOffset = 0f
                                pointerY = dragTop + offset.y
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDragEnd = { drop() },
                        onDragCancel = { cancelDrag() },
                        onDrag = { change, amount ->
                            if (draggedId == rule.id) {
                                change.consume()
                                dragOffset += amount.y
                                pointerY += amount.y
                                moveDragged()
                            }
                        },
                    )
                }
            Card(modifier, elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 8.dp else 0.dp),
                colors = CardDefaults.cardColors(
                containerColor = if (rule.enabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_drag), "Перетащить ${rule.host}", Modifier.size(24.dp).padding(end = 4.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${index + 1}. ${rule.host}", style = MaterialTheme.typography.titleLarge)
                            Text(modeLabel(rule.mode), style = MaterialTheme.typography.labelLarge)
                        }
                        Switch(rule.enabled, { onToggle(rule) }, Modifier.semantics { contentDescription = "Активность правила ${rule.host}" }, enabled = draggedId == null)
                    }
                    Text(rule.source?.let { pkg -> "Источник: ${sources.firstOrNull { it.packageName == pkg }?.label ?: pkg}" } ?: "Из любого приложения")
                    if (rule.scheme != null || rule.port != null || rule.pathPrefix != null)
                        Text(listOfNotNull(rule.scheme, rule.port?.let { "Порт $it" }, rule.pathPrefix).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    Text("→ ${if (rule.action == Action.ASK) "Выбрать при открытии" else appLabel(apps, rule.browser)}",
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onEdit(rule.id) }, enabled = draggedId == null) { Text("Изменить") }
                        IconButton(onClick = { onDelete(rule.id) }, enabled = draggedId == null) { Icon(painterResource(R.drawable.ic_delete), "Удалить ${rule.host}") }
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
    Card(onClick = onClick, modifier = Modifier.testTag("app-${app.packageName}").fillMaxWidth()) {
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
                if (filtered.isEmpty()) item { Text("Приложения не найдены") }
                itemsIndexed(filtered, key = { _, a -> a.packageName }) { _, app -> AppRow(app) { onSelect(app.packageName) } }
            }
        }
    }
}
