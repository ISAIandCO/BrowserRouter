package app.browserrouter

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GeositeUpdateStatus(val lastSuccess: Long = 0, val result: String? = null)

object GeositeUpdater {
    const val PERIODIC = "geosite-periodic-update"
    const val MANUAL = "geosite-manual-update"
    private fun constraints(settings: GeositeUpdates) = Constraints.Builder()
        .setRequiredNetworkType(if (settings.unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()

    fun schedule(context: Context, settings: GeositeUpdates) {
        val manager = WorkManager.getInstance(context)
        if (!settings.autoUpdate) manager.cancelUniqueWork(PERIODIC)
        else {
            val request = PeriodicWorkRequestBuilder<GeositeUpdateWorker>(settings.intervalHours.toLong(), TimeUnit.HOURS)
                .setConstraints(constraints(settings))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
            manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
    fun updateNow(context: Context, settings: GeositeUpdates) {
        val request = OneTimeWorkRequestBuilder<GeositeUpdateWorker>()
            .setInputData(workDataOf("manual" to true, "revision" to GeositeStore.revision(context)))
            .setConstraints(constraints(settings))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniqueWork(MANUAL, ExistingWorkPolicy.REPLACE, request)
    }
    fun cancelManual(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(MANUAL) }
    fun status(context: Context): GeositeUpdateStatus {
        val prefs = context.getSharedPreferences("geosite-update-status", Context.MODE_PRIVATE)
        return GeositeUpdateStatus(prefs.getLong("lastSuccess", 0), prefs.getString("result", null))
    }
    fun record(context: Context, result: String, success: Boolean) {
        val edit = context.getSharedPreferences("geosite-update-status", Context.MODE_PRIVATE).edit().putString("result", result)
        if (success) edit.putLong("lastSuccess", System.currentTimeMillis())
        edit.apply()
    }
}

class GeositeUpdateWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = updateLock.withLock {
        withContext(Dispatchers.IO) {
            val context = applicationContext
            val manual = inputData.getBoolean("manual", false)
            try {
                val settings = ConfigStore(context).load().geositeUpdates
                if (!manual && !settings.autoUpdate) return@withContext Result.success()
                val revision = if (manual) inputData.getLong("revision", -1) else GeositeStore.revision(context)
                if (revision != GeositeStore.revision(context)) return@withContext Result.success()
                val coroutine = currentCoroutineContext()
                val bytes = downloadGeosite(settings.url, checkActive = { coroutine.ensureActive() })
                coroutine.ensureActive()
                val state = GeositeStore(context).commitDownload(bytes, settings, revision)
                    ?: return@withContext Result.success() // Source changed or a local import/reset won.
                val missing = ConfigStore(context).load().rules.filter { it.enabled && it.mode == HostMode.GEOSITE &&
                    state.database.selectorError(it.host) != null }
                val message = if (missing.isEmpty()) "База проверена и обновлена: ${state.database.names.size} групп"
                    else "База обновлена. Отсутствуют группы правил: ${missing.joinToString { it.host }}. Для них будет выбор браузера."
                GeositeUpdater.record(context, message, true)
                Result.success()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                // Do not include server URLs or response bodies (which may contain private access tokens).
                val message = when (e) {
                    is IOException -> "Ошибка сети при обновлении базы"
                    else -> "Загруженная база или настройки источника некорректны"
                }
                GeositeUpdater.record(context, "$message. Предыдущая база сохранена.", false)
                if (e is IOException && runAttemptCount < 2) Result.retry() else Result.failure()
            }
        }
    }
    companion object { private val updateLock = Mutex() }
}
