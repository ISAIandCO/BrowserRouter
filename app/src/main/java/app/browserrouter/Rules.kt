package app.browserrouter

import java.net.IDN
import java.net.URI
import java.util.Locale
import java.util.UUID

enum class HostMode { EXACT, DOMAIN, SUFFIX, WILDCARD, REGEX }
enum class Action { BROWSER, ASK }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    val source: String? = null,
    val mode: HostMode = HostMode.DOMAIN,
    val host: String = "",
    val scheme: String? = null,
    val port: Int? = null,
    val pathPrefix: String? = null,
    val action: Action = Action.ASK,
    val browser: String? = null,
)
data class Config(
    val rules: List<Rule> = emptyList(),
    val fallback: String? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val onboarded: Boolean = false,
)

data class WebLink(val original: String, val scheme: String, val host: String, val port: Int, val path: String) {
    companion object {
        fun parse(raw: String): WebLink {
            require(raw.length in 1..16384) { "Ссылка слишком длинная или пустая" }
            val uri = URI(raw)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            require(scheme == "http" || scheme == "https") { "Поддерживаются только HTTP и HTTPS" }
            require(!uri.isOpaque && !uri.rawAuthority.isNullOrEmpty()) { "В ссылке нет адреса сервера" }
            // URI.host is null for Unicode domain names; parse authority without losing the original URL.
            val authority = uri.rawAuthority.substringAfterLast('@')
            val host: String
            val explicitPort: Int?
            if (authority.startsWith('[')) {
                val end = authority.indexOf(']')
                require(end > 0) { "Некорректный IPv6-адрес" }
                host = authority.substring(0, end + 1)
                val tail = authority.substring(end + 1)
                require(tail.isEmpty() || tail.startsWith(':')) { "Некорректный адрес сервера" }
                explicitPort = if (tail.isEmpty()) null else tail.drop(1).toIntOrNull()
                require(tail.isEmpty() || explicitPort != null) { "Некорректный порт" }
                require(uri.host != null) { "Некорректный IPv6-адрес" }
            } else {
                require(authority.count { it == ':' } <= 1) { "IPv6 должен быть заключён в квадратные скобки" }
                host = authority.substringBefore(':')
                explicitPort = if (':' in authority) authority.substringAfter(':').toIntOrNull() else null
                require(':' !in authority || explicitPort != null) { "Некорректный порт" }
            }
            require(explicitPort == null || explicitPort in 1..65535) { "Порт должен быть от 1 до 65535" }
            return WebLink(raw, scheme, normalizeHost(host), explicitPort ?: if (scheme == "https") 443 else 80,
                uri.rawPath.orEmpty().ifEmpty { "/" })
        }
    }
}

fun normalizeHost(value: String): String {
    val host = value.trim().replace('\u3002', '.').replace('\uFF0E', '.').replace('\uFF61', '.').removeSuffix(".")
    require(host.isNotEmpty() && !host.endsWith('.')) { "Некорректное имя сервера" }
    if (host.startsWith('[') && host.endsWith(']')) {
        require(URI("https://$host/").host != null) { "Некорректный IPv6-адрес" }
        return host.lowercase(Locale.ROOT)
    }
    val ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
    require(ascii.length <= 253 && ascii.split('.').all { it.isNotEmpty() && it.length <= 63 }) { "Некорректное имя сервера" }
    return ascii
}

private fun hostPattern(rule: Rule): String = when (rule.mode) {
    HostMode.REGEX -> rule.host
    HostMode.WILDCARD -> rule.host.trim().removeSuffix(".").split('.').joinToString(".") {
        if ('*' in it) {
            require(it == "*") { "Звёздочка должна занимать целую часть имени: *.example.ru" }
            "*"
        } else normalizeHost(it)
    }
    else -> normalizeHost(rule.host.trim().removePrefix("."))
}

fun validateRule(rule: Rule): String? = runCatching {
    require(rule.id.isNotBlank()) { "Пустой идентификатор правила" }
    require(rule.host.length in 1..256) { "Введите домен или шаблон (до 256 символов)" }
    val pattern = hostPattern(rule)
    if (rule.mode == HostMode.REGEX) Regex(pattern)
    require(rule.scheme == null || rule.scheme in listOf("http", "https")) { "Некорректная схема" }
    require(rule.port == null || rule.port in 1..65535) { "Порт должен быть от 1 до 65535" }
    require(rule.pathPrefix == null || (rule.pathPrefix.startsWith('/') && rule.pathPrefix.length <= 2048)) { "Путь должен начинаться с / и быть не длиннее 2048 символов" }
    require(rule.source == null || rule.source.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) { "Некорректный пакет источника" }
    require(rule.action != Action.BROWSER || !rule.browser.isNullOrBlank()) { "Выберите браузер" }
    require(rule.browser == null || validPackage(rule.browser)) { "Некорректный пакет браузера" }
}.exceptionOrNull()?.let { if (it is java.util.regex.PatternSyntaxException) "Ошибка regex: ${it.description}" else it.message ?: "Некорректное правило" }

fun matches(rule: Rule, link: WebLink, source: String?): Boolean {
    if (!rule.enabled || validateRule(rule) != null || (rule.source != null && rule.source != source)) return false
    if (rule.scheme != null && rule.scheme != link.scheme) return false
    if (rule.port != null && rule.port != link.port) return false
    if (rule.pathPrefix != null && !link.path.startsWith(rule.pathPrefix)) return false
    val pattern = hostPattern(rule)
    return when (rule.mode) {
        HostMode.EXACT -> link.host == pattern
        HostMode.DOMAIN, HostMode.SUFFIX -> link.host == pattern || link.host.endsWith(".$pattern")
        HostMode.WILDCARD -> Regex(pattern.split('*').joinToString(".*") { Regex.escape(it) }).matches(link.host)
        HostMode.REGEX -> Regex(pattern).matches(link.host)
    }
}

sealed interface Route {
    data class Open(val packageName: String, val ruleId: String?) : Route
    data class Choose(val reason: String?) : Route
}

fun route(config: Config, link: WebLink, source: String?, available: Set<String>, self: String): Route {
    val rule = config.rules.firstOrNull { matches(it, link, source) }
    if (rule?.action == Action.ASK) return Route.Choose(null)
    val target = if (rule != null) rule.browser else config.fallback
    return when {
        target == null -> Route.Choose(null)
        target == self -> Route.Choose("BrowserRouter не может открывать ссылку через себя")
        target !in available -> Route.Choose("Выбранный браузер недоступен. Выберите другой")
        else -> Route.Open(target, rule?.id)
    }
}
