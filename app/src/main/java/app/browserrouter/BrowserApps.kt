package app.browserrouter

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri

data class BrowserApp(val packageName: String, val label: String, val icon: Drawable?, val component: ComponentName?)

@Suppress("DEPRECATION")
fun browsers(pm: PackageManager, self: String, url: String? = null): List<BrowserApp> {
    val urls = if (url == null) listOf("https://browserrouter.invalid/", "http://browserrouter.invalid/") else listOf(url)
    return urls.flatMap { value ->
        pm.queryIntentActivities(webIntent(value), PackageManager.MATCH_ALL or PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_RESOLVED_FILTER)
    }.filter { r ->
        val a = r.activityInfo
        a.packageName != self && a.exported && a.enabled && a.applicationInfo.enabled && a.permission == null &&
            (url != null || (r.filter != null && r.filter.countDataAuthorities() == 0 && r.filter.countDataPaths() == 0))
    }.distinctBy { it.activityInfo.packageName }.map { r ->
        BrowserApp(r.activityInfo.packageName, r.loadLabel(pm).toString(), r.loadIcon(pm),
            ComponentName(r.activityInfo.packageName, r.activityInfo.name))
    }.sortedBy { it.label.lowercase() }
}

@Suppress("DEPRECATION")
fun sourceApps(pm: PackageManager, self: String): List<BrowserApp> =
    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .filter { it.activityInfo.packageName != self }.distinctBy { it.activityInfo.packageName }.map {
            BrowserApp(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm), null)
        }.sortedBy { it.label.lowercase() }

fun webIntent(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)

fun Activity.sourcePackage(): String? = runCatching {
    callingPackage?.takeIf(::validPackage) ?: referrer?.let {
        if (it.scheme == "android-app") it.host?.takeIf(::validPackage) else null
    }
}.getOrNull()?.takeUnless { it == packageName }

fun Activity.openBrowser(link: WebLink, browser: BrowserApp): String? {
    if (browser.packageName == packageName || browser.component?.packageName != browser.packageName)
        return "Недопустимый обработчик ссылки"
    return runCatching {
        // Fresh intent deliberately excludes untrusted flags, selectors, ClipData and extras.
        startActivity(webIntent(link.original).setComponent(browser.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.exceptionOrNull()?.let { "Не удалось открыть браузер. Возможно, он удалён или отключён" }
}
