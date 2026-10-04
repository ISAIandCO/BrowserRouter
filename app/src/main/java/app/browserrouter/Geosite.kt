package app.browserrouter

import com.google.protobuf.CodedInputStream
import com.google.re2j.Pattern
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale

fun geositeSelector(value: String): List<String> {
    val parts = value.trim().lowercase(Locale.ROOT).removePrefix("geosite:").split('@')
    require(parts.size <= 9 && parts.all { it.matches(Regex("[a-z0-9_!\\-]{1,128}")) }) {
        "Введите группу geosite, например google или geosite:google@ads"
    }
    return parts
}

/** V2Ray GeoSiteList protobuf. No DNS, country lookup or network request. */
class GeositeDatabase private constructor(private val groups: Map<String, List<Entry>>) {
    private data class Entry(val type: Int, val value: String, val attributes: Set<String>, val regex: Pattern?) {
        fun matches(host: String) = when (type) {
            0 -> host.contains(value)
            1 -> regex!!.matcher(host).find() // V2Ray regex searches; user regex rules match the whole hostname.
            2 -> host == value || host.endsWith(".$value")
            3 -> host == value
            else -> false
        }
    }
    val names: List<String> = groups.keys.sorted()
    fun selectorError(selector: String): String? = runCatching { selected(selector) }.exceptionOrNull()?.message
    private fun selected(selector: String): List<Entry> {
        val parts = geositeSelector(selector)
        val entries = requireNotNull(groups[parts.first()]) { "Группа ${parts.first()} отсутствует в базе" }
        val filtered = if (parts.size == 1) entries else entries.filter { it.attributes.containsAll(parts.drop(1)) }
        require(filtered.isNotEmpty()) { "В группе $selector нет подходящих записей" }
        return filtered
    }
    fun matches(selector: String, host: String) = selected(selector).any { it.matches(host) }

    companion object {
        const val MAX_BYTES = 16 * 1024 * 1024
        private const val MAX_ENTRIES = 300_000
        fun parse(bytes: ByteArray): GeositeDatabase {
            require(bytes.size in 1..MAX_BYTES) { "База geosite пуста или больше 16 МБ" }
            val groups = linkedMapOf<String, List<Entry>>()
            var count = 0
            fields(bytes) { input, tag ->
                if (tag != 10) skip(input, tag) else {
                    var name = ""
                    val entries = mutableListOf<Entry>()
                    fields(input.readByteArray()) { group, field ->
                        when (field) {
                            10 -> name = group.readStringRequireUtf8().lowercase(Locale.ROOT)
                            18 -> {
                                require(++count <= MAX_ENTRIES) { "Больше $MAX_ENTRIES записей geosite" }
                                entries.add(readEntry(group.readByteArray()))
                            }
                            else -> skip(group, field)
                        }
                    }
                    require(name.matches(Regex("[a-z0-9_!\\-]{1,128}"))) { "Некорректное имя группы geosite" }
                    require(name !in groups) { "Повторяющаяся группа geosite: $name" }
                    require(groups.size < 10_000) { "Слишком много групп geosite" }
                    groups[name] = entries
                }
            }
            require(groups.isNotEmpty() && count > 0) { "Файл не содержит групп geosite" }
            return GeositeDatabase(groups)
        }
        private fun readEntry(bytes: ByteArray): Entry {
            var type = 0
            var value = ""
            val attributes = mutableSetOf<String>()
            fields(bytes) { input, tag ->
                when (tag) {
                    8 -> type = input.readEnum()
                    18 -> value = input.readStringRequireUtf8()
                    26 -> {
                        require(attributes.size < 64) { "Слишком много атрибутов geosite" }
                        var key = ""
                        fields(input.readByteArray()) { attr, field ->
                            if (field == 10) key = attr.readStringRequireUtf8().lowercase(Locale.ROOT) else skip(attr, field)
                        }
                        require(key.matches(Regex("[a-z0-9_!\\-]{1,128}"))) { "Некорректный атрибут geosite" }
                        attributes.add(key)
                    }
                    else -> skip(input, tag)
                }
            }
            require(type in 0..3 && value.length in 1..2048) { "Некорректная запись geosite" }
            if (type == 2 || type == 3) value = normalizeHost(value)
            else if (type == 0) value = value.lowercase(Locale.ROOT)
            val regex = if (type == 1) Pattern.compile(value).also {
                require(it.programSize() <= 10_000) { "Слишком сложный regexp geosite" }
            } else null
            return Entry(type, value, attributes, regex)
        }
        private fun fields(bytes: ByteArray, read: (CodedInputStream, Int) -> Unit) {
            val input = CodedInputStream.newInstance(bytes)
            input.setRecursionLimit(8)
            while (!input.isAtEnd) {
                val tag = input.readTag()
                require(tag != 0) { "Некорректный protobuf geosite" }
                read(input, tag)
            }
        }
        private fun skip(input: CodedInputStream, tag: Int) {
            // GeoSite uses no deprecated protobuf groups. Reject them instead of recursing.
            require((tag and 7) in listOf(0, 1, 2, 5) && input.skipField(tag)) { "Некорректное поле protobuf geosite" }
        }
    }
}

fun InputStream.readGeositeBytes(): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= GeositeDatabase.MAX_BYTES) { "База geosite больше 16 МБ" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
