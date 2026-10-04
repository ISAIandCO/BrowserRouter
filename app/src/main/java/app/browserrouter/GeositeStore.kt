package app.browserrouter

import android.content.Context
import android.util.AtomicFile
import java.io.File

data class GeositeState(val database: GeositeDatabase, val custom: Boolean)

/** Both activities share the same immutable snapshot; replacement is atomic. Call on Dispatchers.IO. */
class GeositeStore(private val context: Context) {
    private val file = AtomicFile(File(context.filesDir, "geosite.dat"))
    fun load(): GeositeState = synchronized(lock) {
        cached?.let { return@synchronized it }
        val custom = file.baseFile.exists() || File(context.filesDir, "geosite.dat.bak").exists()
        val bytes = if (custom) file.openRead().use { it.readGeositeBytes() }
            else context.assets.open("geosite.dat").use { it.readGeositeBytes() }
        GeositeState(GeositeDatabase.parse(bytes), custom).also { cached = it }
    }
    fun replace(bytes: ByteArray): GeositeState {
        val database = GeositeDatabase.parse(bytes) // Validate everything before touching the existing file.
        return synchronized(lock) { replaceValidated(bytes, database) }
    }
    private fun replaceValidated(bytes: ByteArray, database: GeositeDatabase): GeositeState {
        invalidatePendingUpdates()
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
            check(file.baseFile.readBytes().contentEquals(bytes)) { "Не удалось завершить запись базы" }
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
        return GeositeState(database, true).also { cached = it }
    }
    fun reset(): GeositeState = synchronized(lock) {
        val database = context.assets.open("geosite.dat").use { GeositeDatabase.parse(it.readGeositeBytes()) }
        invalidatePendingUpdates()
        file.delete()
        check(!file.baseFile.exists() && !File(context.filesDir, "geosite.dat.bak").exists()) { "Не удалось удалить пользовательскую базу" }
        GeositeState(database, false).also { cached = it }
    }
    fun commitDownload(bytes: ByteArray, expected: GeositeUpdates, revision: Long): GeositeState? {
        val database = GeositeDatabase.parse(bytes)
        return synchronized(lock) {
            if (GeositeStore.revision(context) != revision || ConfigStore(context).load().geositeUpdates != expected) return@synchronized null
            // The parsed download and its source are committed under the same monitor as config writes.
            replaceValidated(bytes, database)
        }
    }
    @android.annotation.SuppressLint("ApplySharedPref") // Must survive process death before a queued worker starts.
    private fun invalidatePendingUpdates() {
        val prefs = context.getSharedPreferences("geosite-control", Context.MODE_PRIVATE)
        check(prefs.edit().putLong("revision", prefs.getLong("revision", 0) + 1).commit()) { "Не удалось сохранить состояние базы" }
    }
    companion object {
        private val lock = storageLock
        fun revision(context: Context): Long = synchronized(lock) {
            context.getSharedPreferences("geosite-control", Context.MODE_PRIVATE).getLong("revision", 0)
        }
        private var cached: GeositeState? = null
    }
}
