package app.browserrouter

import org.json.JSONArray
import org.json.JSONObject

/** Explicit schema avoids reflection and keeps storage/export identical. */
object ConfigCodec {
    const val VERSION = 1
    const val MAX_BYTES = 512 * 1024
    const val MAX_RULES = 200

    fun encode(config: Config): String = JSONObject().apply {
        put("schemaVersion", VERSION)
        put("fallback", config.fallback ?: JSONObject.NULL)
        put("theme", config.theme.name)
        put("dynamicColor", config.dynamicColor)
        put("onboarded", config.onboarded)
        put("rules", JSONArray().apply {
            config.rules.forEach { r -> put(JSONObject().apply {
                put("id", r.id); put("enabled", r.enabled)
                put("source", r.source ?: JSONObject.NULL); put("mode", r.mode.name)
                put("host", r.host); put("scheme", r.scheme ?: JSONObject.NULL)
                put("port", r.port ?: JSONObject.NULL); put("pathPrefix", r.pathPrefix ?: JSONObject.NULL)
                put("action", r.action.name); put("browser", r.browser ?: JSONObject.NULL)
            }) }
        })
    }.toString(2)

    fun decode(text: String): Config {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Файл больше 512 КБ" }
        val root = JSONObject(text)
        require(root.get("schemaVersion") == VERSION) { "Неподдерживаемая версия формата" }
        val array = root.getJSONArray("rules")
        require(array.length() <= MAX_RULES) { "Допускается до $MAX_RULES правил" }
        val rules = (0 until array.length()).map { index ->
            val o = array.getJSONObject(index)
            val r = Rule(
                id = o.requiredString("id"), enabled = o.strictBoolean("enabled", true),
                source = o.nullableString("source"), mode = HostMode.valueOf(o.requiredString("mode")),
                host = o.requiredString("host"), scheme = o.nullableString("scheme"),
                port = if (!o.has("port") || o.isNull("port")) null else {
                    val p = o.get("port")
                    require(p is Int) { "Порт должен быть целым числом" }
                    p
                },
                pathPrefix = o.nullableString("pathPrefix"), action = Action.valueOf(o.requiredString("action")),
                browser = o.nullableString("browser"),
            )
            require(validateRule(r) == null) { "Правило ${index + 1}: ${validateRule(r)}" }
            r
        }
        require(rules.map { it.id }.distinct().size == rules.size) { "Повторяются идентификаторы правил" }
        val fallback = root.nullableString("fallback")
        require(fallback == null || validPackage(fallback)) { "Некорректный fallback-пакет" }
        return Config(rules, fallback, ThemeMode.valueOf(root.optString("theme", "SYSTEM")),
            root.strictBoolean("dynamicColor", true), root.strictBoolean("onboarded", false))
    }
    private fun JSONObject.requiredString(key: String): String {
        val v = get(key)
        require(v is String) { "$key должен быть строкой" }
        return v
    }
    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else requiredString(key)
    private fun JSONObject.strictBoolean(key: String, default: Boolean): Boolean {
        if (!has(key)) return default
        val v = get(key)
        require(v is Boolean) { "$key должен быть true/false" }
        return v
    }
}

fun validPackage(value: String) = value.length <= 255 && value.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))
