package app.browserrouter

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

const val DEFAULT_GEOSITE_URL = "https://raw.githubusercontent.com/v2fly/domain-list-community/release/dlc.dat"
val geositeSources = listOf(
    "v2fly · GitHub" to DEFAULT_GEOSITE_URL,
    "v2fly · CDN" to "https://cdn.jsdelivr.net/gh/v2fly/domain-list-community@release/dlc.dat",
    "Loyalsoldier · GitHub" to "https://raw.githubusercontent.com/Loyalsoldier/v2ray-rules-dat/release/geosite.dat",
    "Loyalsoldier · CDN" to "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geosite.dat",
    "runetfreedom · GitHub" to "https://raw.githubusercontent.com/runetfreedom/russia-v2ray-rules-dat/release/geosite.dat",
    "runetfreedom · CDN" to "https://cdn.jsdelivr.net/gh/runetfreedom/russia-v2ray-rules-dat@release/geosite.dat",
)
data class GeositeUpdates(
    val url: String = DEFAULT_GEOSITE_URL,
    val autoUpdate: Boolean = false,
    val intervalHours: Int = 24,
    val unmeteredOnly: Boolean = false,
)

/** Accept direct HTTPS files, GitHub raw/blob file pages, and GitHub release asset URLs. */
fun geositeDownloadUrl(raw: String): String {
    require(raw.length in 1..2048) { "Введите HTTPS-ссылку на файл базы (до 2048 символов)" }
    val uri = URI(raw.trim())
    require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null &&
        uri.fragment == null && (uri.port == -1 || uri.port in 1..65535)) {
        "Нужна HTTPS-ссылка на файл без логина, пароля и фрагмента"
    }
    if (uri.host.equals("github.com", true)) {
        val parts = uri.rawPath.trim('/').split('/')
        if (parts.size >= 5 && parts[2] in listOf("blob", "raw")) {
            return "https://raw.githubusercontent.com/${parts[0]}/${parts[1]}/${parts.drop(3).joinToString("/")}" +
                (uri.rawQuery?.let { "?$it" } ?: "")
        }
        require(parts.size >= 6 && parts[2] == "releases" &&
            ((parts[3] == "download") || (parts[3] == "latest" && parts[4] == "download"))) {
            "Укажите ссылку на файл .dat, а не главную страницу Git-репозитория"
        }
    }
    return uri.toASCIIString()
}

fun validateGeositeUpdates(settings: GeositeUpdates) {
    geositeDownloadUrl(settings.url)
    require(settings.intervalHours in 1..720) { "Интервал обновления: от 1 до 720 часов" }
}

/** Bounded download with explicit HTTPS-only redirects. Existing data is never touched here. */
fun downloadGeosite(
    source: String,
    checkActive: () -> Unit = {},
    connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
): ByteArray {
    var target = geositeDownloadUrl(source)
    val deadline = System.nanoTime() + 120_000_000_000L
    fun check() {
        checkActive()
        if (System.nanoTime() > deadline) throw IOException("Превышено время загрузки базы")
    }
    repeat(6) { redirect ->
        check()
        val connection = connect(URL(target))
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "BrowserRouter geosite updater")
            connection.setRequestProperty("Accept", "application/octet-stream")
            connection.setRequestProperty("Accept-Encoding", "identity")
            val code = connection.responseCode
            check()
            if (code in listOf(301, 302, 303, 307, 308)) {
                if (redirect == 5) throw IOException("Слишком много перенаправлений")
                val location = connection.getHeaderField("Location") ?: throw IOException("Пустое перенаправление")
                target = geositeDownloadUrl(URI(target).resolve(location).toString())
            } else {
                if (code != 200) throw IOException("Сервер базы вернул HTTP $code")
                require(connection.contentLengthLong <= GeositeDatabase.MAX_BYTES) { "База geosite больше 16 МБ" }
                return connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        check()
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= GeositeDatabase.MAX_BYTES) { "База geosite больше 16 МБ" }
                        output.write(buffer, 0, count)
                    }
                    require(output.size() > 0) { "Сервер вернул пустую базу" }
                    output.toByteArray()
                }
            }
        } finally { connection.disconnect() }
    }
    throw IOException("Не удалось загрузить базу")
}
