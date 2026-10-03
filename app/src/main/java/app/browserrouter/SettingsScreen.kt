@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.browserrouter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(config: Config, apps: List<BrowserApp>, catalogLoading: Boolean, roleHeld: Boolean,
                   onRequestRole: () -> Unit, onUpdate: ((Config) -> Config) -> Unit, onImport: () -> Unit,
                   onExport: () -> Unit, onReset: () -> Unit) {
    var picker by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.testTag("settings-list").widthIn(max = 840.dp).fillMaxWidth(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { SettingsGroup("Открытие ссылок") {
            Text("Если ни одно правило не совпало", style = MaterialTheme.typography.titleMedium)
            FilledTonalButton(onClick = { picker = true }, enabled = !catalogLoading, modifier = Modifier.fillMaxWidth()) { Text(appLabel(apps, config.fallback)) }
            Text("Если выбранный браузер недоступен, BrowserRouter предложит другой. Пропускать первое совпавшее правило он не будет.")
            HorizontalDivider()
            Text(if (roleHeld) "BrowserRouter назначен по умолчанию" else "BrowserRouter не назначен по умолчанию", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onRequestRole) { Text("Настроить браузер по умолчанию") }
            Text("Приложения со встроенным браузером, явным выбором пакета или подтверждёнными App Links могут открывать ссылки напрямую.", style = MaterialTheme.typography.bodySmall)
        } }
        item { SettingsGroup("Оформление") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode -> FilterChip(config.theme == mode,
                    { onUpdate { it.copy(theme = mode) } }, label = { Text(when (mode) {
                        ThemeMode.SYSTEM -> "Системная"; ThemeMode.LIGHT -> "Светлая"; ThemeMode.DARK -> "Тёмная"
                    }) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Цвета обоев", style = MaterialTheme.typography.titleMedium)
                    Text("Dynamic color на Android 12 и новее", style = MaterialTheme.typography.bodySmall)
                }
                Switch(config.dynamicColor, { value -> onUpdate { it.copy(dynamicColor = value) } },
                    Modifier.semantics { contentDescription = "Цвета обоев" })
            }
        } }
        item { SettingsGroup("Правила и резервная копия") {
            Text("JSON содержит правила, fallback и настройки оформления. Храните резервную копию перед переустановкой.")
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Экспортировать настройки") }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Импортировать настройки") }
            TextButton(onClick = onReset) { Text("Сбросить настройки") }
        } }
        item { SettingsGroup("О BrowserRouter") {
            Text("BrowserRouter ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
            Text("Маршрутизатор веб-ссылок для Android. Без фоновой службы, сетевого доступа, аналитики и истории посещений.")
            Text("Источник ссылки определяется по calling package / referrer, когда Android передаёт эти данные. Они могут отсутствовать или быть подменены. Не используйте правила как защитную границу.")
            Text("Лицензия GPL-3.0 · ISAIandCO", style = MaterialTheme.typography.bodySmall)
        } }
    }
    if (picker) AppPicker("Браузер для остальных ссылок", apps,
        onSelect = { value -> onUpdate { it.copy(fallback = value) }; picker = false }, onDismiss = { picker = false })
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Card {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}
