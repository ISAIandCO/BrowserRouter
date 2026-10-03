package app.browserrouter

import android.content.Context
import android.util.AtomicFile

// AtomicFile does not synchronize readers and writers. Both activities run in one process.
private val configFileLock = Any()

class ConfigStore(context: Context) {
    private val file = AtomicFile(java.io.File(context.filesDir, "routing-v1.json"))
    fun load(): Config = synchronized(configFileLock) { try {
        file.openRead().use { stream ->
            val bytes = stream.readLimited()
            ConfigCodec.decode(bytes.toString(Charsets.UTF_8))
        }
    } catch (e: java.io.FileNotFoundException) { Config() } }

    fun save(config: Config) = synchronized(configFileLock) {
        val text = ConfigCodec.encode(config)
        ConfigCodec.decode(text)
        val stream = file.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
            check(file.baseFile.readText(Charsets.UTF_8) == text) { "Не удалось завершить запись настроек" }
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }
}

fun java.io.InputStream.readLimited(): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= ConfigCodec.MAX_BYTES) { "Файл больше 512 КБ" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
