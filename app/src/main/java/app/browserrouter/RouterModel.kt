package app.browserrouter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

data class RouterState(val config: Config? = null, val loading: Boolean = true, val error: String? = null)

class RouterModel(app: Application) : AndroidViewModel(app) {
    private val store = ConfigStore(app)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(RouterState())
    val state = mutable.asStateFlow()
    init { reload() }

    fun reload() = viewModelScope.launch {
        mutex.withLock {
            runCatching { withContext(Dispatchers.IO) { store.load() } }.fold(
                { mutable.value = RouterState(it, false); GeositeUpdater.schedule(getApplication(), it.geositeUpdates) },
                { mutable.value = RouterState(loading = false, error = "Настройки не прочитаны: ${it.message}. Файл сохранён; восстановите его импортом или сбросьте явно.") },
            )
        }
    }
    fun update(transform: (Config) -> Config) = viewModelScope.launch {
        mutex.withLock {
            val old = mutable.value.config ?: return@withLock
            persist(transform(old))
        }
    }
    private suspend fun persist(config: Config): Boolean {
        return runCatching { withContext(Dispatchers.IO) { store.save(config) } }.fold(
            {
                if (mutable.value.config?.geositeUpdates != config.geositeUpdates) GeositeUpdater.cancelManual(getApplication())
                mutable.value = RouterState(config, false)
                GeositeUpdater.schedule(getApplication(), config.geositeUpdates)
                true
            },
            { mutable.value = mutable.value.copy(error = "Не удалось сохранить: ${it.message}"); false },
        )
    }
    fun saveGeositeUpdates(settings: GeositeUpdates, onSaved: () -> Unit = {}) = viewModelScope.launch {
        mutex.withLock {
            val old = mutable.value.config ?: return@withLock
            if (persist(old.copy(geositeUpdates = settings))) onSaved()
        }
    }
    fun clearError() { mutable.value = mutable.value.copy(error = null) }
    fun saveRule(rule: Rule, onSaved: () -> Unit = {}) = viewModelScope.launch {
        mutex.withLock {
            val config = mutable.value.config ?: return@withLock
            val index = config.rules.indexOfFirst { it.id == rule.id }
            val next = config.copy(rules = config.rules.toMutableList().apply {
                if (index < 0) add(rule) else set(index, rule)
            })
            if (persist(next)) onSaved()
        }
    }
    fun move(id: String, delta: Int) = update { c ->
        val rules = c.rules.toMutableList()
        val index = rules.indexOfFirst { it.id == id }
        if (index >= 0 && index + delta in rules.indices) rules.add(index + delta, rules.removeAt(index))
        c.copy(rules = rules)
    }
    fun importConfig(imported: Config, replace: Boolean) = viewModelScope.launch {
        mutex.withLock {
            val old = mutable.value.config
            val next = if (replace || old == null) imported else old.copy(rules = old.rules + imported.rules.map {
                it.copy(id = UUID.randomUUID().toString())
            })
            persist(next)
        }
    }
    fun reset() = importConfig(Config(), true)
}
