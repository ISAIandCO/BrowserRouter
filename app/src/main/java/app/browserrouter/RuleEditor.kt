@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.browserrouter

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.UUID

fun modeLabel(mode: HostMode) = when (mode) {
    HostMode.EXACT -> "Точный адрес"
    HostMode.DOMAIN -> "Домен и поддомены"
    HostMode.SUFFIX -> "Окончание домена"
    HostMode.WILDCARD -> "Шаблон со звёздочкой"
    HostMode.REGEX -> "Regex"
}

@Composable
fun RuleEditor(initial: Rule?, apps: List<BrowserApp>, sources: List<BrowserApp>, onCancel: () -> Unit, onSave: (Rule) -> Unit) {
    val id = rememberSaveable { initial?.id ?: UUID.randomUUID().toString() }
    var host by rememberSaveable { mutableStateOf(initial?.host.orEmpty()) }
    var mode by rememberSaveable { mutableStateOf((initial?.mode ?: HostMode.DOMAIN).name) }
    var source by rememberSaveable { mutableStateOf(initial?.source.orEmpty()) }
    var browser by rememberSaveable { mutableStateOf(initial?.browser) }
    var action by rememberSaveable { mutableStateOf((initial?.action ?: Action.ASK).name) }
    var scheme by rememberSaveable { mutableStateOf(initial?.scheme.orEmpty()) }
    var port by rememberSaveable { mutableStateOf(initial?.port?.toString().orEmpty()) }
    var path by rememberSaveable { mutableStateOf(initial?.pathPrefix.orEmpty()) }
    var enabled by rememberSaveable { mutableStateOf(initial?.enabled ?: true) }
    var advanced by rememberSaveable { mutableStateOf(initial?.let { it.scheme != null || it.port != null || it.pathPrefix != null } ?: false) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val draft = Rule(id, enabled, source.trim().ifEmpty { null }, HostMode.valueOf(mode), host.trim(),
        scheme.ifEmpty { null }, port.toIntOrNull(), path.ifEmpty { null }, Action.valueOf(action), browser)
    val error = if (port.isNotEmpty() && port.toIntOrNull() == null) "Порт должен быть числом" else validateRule(draft)
    fun leave() { if (draft != (initial ?: Rule(id = id))) discard = true else onCancel() }
    BackHandler { leave() }
    Scaffold(topBar = { TopAppBar(title = { Text(if (initial == null) "Новое правило" else "Изменить правило") },
        navigationIcon = { TextButton(onClick = { leave() }) { Text("Назад") } }) },
        bottomBar = {
            Surface(shadowElevation = 3.dp) {
                Button(onClick = { onSave(draft) }, enabled = error == null,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp).heightIn(min = 56.dp)) { Text("Сохранить правило") }
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Text("Откуда приходит ссылка", style = MaterialTheme.typography.titleLarge)
                    FilledTonalButton(onClick = { picker = "source" }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (source.isEmpty()) "Любое приложение" else sources.firstOrNull { it.packageName == source }?.label ?: source)
                    }
                    Text("Android может не передать источник или передать недостоверные сведения. Тогда правила с конкретным источником не совпадут.",
                        style = MaterialTheme.typography.bodySmall)
                }
                item {
                    Text("Какие адреса", style = MaterialTheme.typography.titleLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HostMode.entries.forEach { option ->
                            FilterChip(selected = mode == option.name, onClick = { mode = option.name }, label = { Text(modeLabel(option)) })
                        }
                    }
                }
                item {
                    OutlinedTextField(host, { host = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(if (mode == HostMode.REGEX.name) "Regex для hostname" else "Домен или шаблон") },
                        placeholder = { Text(if (mode == HostMode.WILDCARD.name) "*.ru" else "example.ru") },
                        isError = host.isNotBlank() && error != null,
                        supportingText = { Text(error ?: when (HostMode.valueOf(mode)) {
                            HostMode.DOMAIN -> "Совпадут example.ru и все его поддомены"
                            HostMode.EXACT -> "Совпадёт только указанный hostname"
                            HostMode.SUFFIX -> "Проверка по границе точки: .ru не совпадёт с example.ru.evil.com"
                            HostMode.WILDCARD -> "* занимает целую часть имени и может охватывать несколько поддоменов. *.example.ru не включает example.ru"
                            HostMode.REGEX -> "Сопоставляется весь нормализованный hostname в punycode. Regex чувствителен к регистру; hostname всегда в нижнем регистре"
                        }) })
                }
                item {
                    Text("Куда открыть", style = MaterialTheme.typography.titleLarge)
                    FilledTonalButton(onClick = { picker = "browser" }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (action == Action.ASK.name) "Выбирать при открытии" else appLabel(apps, browser))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Правило активно", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Switch(enabled, { enabled = it }, Modifier.semantics { contentDescription = "Правило активно" })
                    }
                    TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Скрыть дополнительные условия" else "Дополнительные условия") }
                    AnimatedVisibility(advanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Схема", style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("" to "Любая", "http" to "HTTP", "https" to "HTTPS").forEach { (value, label) ->
                                    FilterChip(scheme == value, { scheme = value }, label = { Text(label) })
                                }
                            }
                            OutlinedTextField(port, { port = it }, label = { Text("Порт (необязательно)") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = { Text("По умолчанию HTTPS = 443, HTTP = 80") })
                            OutlinedTextField(path, { path = it }, label = { Text("Начало пути, например /news/") }, modifier = Modifier.fillMaxWidth(),
                                supportingText = { Text("Сравнивается путь в исходном URL, с учётом регистра и %-кодирования; query не учитывается") })
                            OutlinedTextField(source, { source = it }, label = { Text("Пакет источника вручную") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                supportingText = { Text("Для приложений, отсутствующих в списке. Пустое поле — любой источник") })
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Маршрут", style = MaterialTheme.typography.labelLarge)
                            Text("${sources.firstOrNull { it.packageName == source }?.label ?: source.ifEmpty { "Любое приложение" }} → ${host.ifEmpty { "Адрес" }} → ${if (action == Action.ASK.name) "Выбор браузера" else appLabel(apps, browser)}",
                                style = MaterialTheme.typography.titleMedium)
                            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    picker?.let { type -> AppPicker(if (type == "source") "Источник ссылки" else "Выберите браузер", if (type == "source") sources else apps,
        onSelect = { value ->
            if (type == "source") source = value.orEmpty()
            else { browser = value; action = if (value == null) Action.ASK.name else Action.BROWSER.name }
            picker = null
        }, onDismiss = { picker = null }) }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Отменить изменения?") },
        text = { Text("Несохранённые изменения правила будут потеряны.") },
        confirmButton = { TextButton(onClick = onCancel) { Text("Отменить изменения") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Продолжить редактирование") } })
}
