@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.browserrouter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun GeositeSourceSettings(saved: GeositeUpdates, busy: Boolean, onSave: (GeositeUpdates, Boolean) -> Unit) {
    var url by rememberSaveable(saved.url) { mutableStateOf(saved.url) }
    var custom by rememberSaveable(saved.url) { mutableStateOf(geositeSources.none { it.second == saved.url }) }
    var auto by rememberSaveable(saved.autoUpdate) { mutableStateOf(saved.autoUpdate) }
    var hours by rememberSaveable(saved.intervalHours) { mutableStateOf(saved.intervalHours.toString()) }
    var unmetered by rememberSaveable(saved.unmeteredOnly) { mutableStateOf(saved.unmeteredOnly) }
    var picker by rememberSaveable { mutableStateOf(false) }
    val draft = GeositeUpdates(url.trim(), auto, hours.toIntOrNull() ?: 0, unmetered)
    val error = runCatching { validateGeositeUpdates(draft) }.exceptionOrNull()?.message
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Источник обновлений", style = MaterialTheme.typography.titleMedium)
        FilledTonalButton(onClick = { picker = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (custom) "Свой HTTPS-адрес" else geositeSources.firstOrNull { it.second == url }?.first ?: "Выбрать источник")
        }
        if (custom) OutlinedTextField(url, { url = it }, label = { Text("HTTPS-ссылка на .dat") },
            modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            supportingText = { Text("Прямая HTTPS-ссылка на файл") })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Автообновление", Modifier.weight(1f))
            Switch(auto, { auto = it }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Автообновление geosite" })
        }
        if (auto) {
            OutlinedTextField(hours, { hours = it }, label = { Text("Интервал, часов") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("1–720 часов") })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Только сеть без тарификации", Modifier.weight(1f))
            Switch(unmetered, { unmetered = it }, enabled = !busy,
                modifier = Modifier.semantics { contentDescription = "Обновлять geosite только в сети без тарификации" })
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { onSave(draft, true) }, enabled = !busy && error == null, modifier = Modifier.fillMaxWidth()) {
            Text("Сохранить и обновить сейчас")
        }
        OutlinedButton(onClick = { onSave(draft, false) }, enabled = !busy && error == null && draft != saved,
            modifier = Modifier.fillMaxWidth()) { Text("Сохранить настройки источника") }
    }
    if (picker) ModalBottomSheet(onDismissRequest = { picker = false }) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
            item { Text("Источники geosite", style = MaterialTheme.typography.headlineSmall) }
            items(geositeSources) { (label, source) ->
                TextButton(onClick = { url = source; custom = false; picker = false }, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
            item {
                TextButton(onClick = { custom = true; picker = false }, modifier = Modifier.fillMaxWidth()) { Text("Свой HTTPS-адрес") }
            }
        }
    }
}
