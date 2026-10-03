@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package app.browserrouter

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun RouterTheme(config: Config = Config(), content: @Composable () -> Unit) {
    val dark = when (config.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = if (config.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(primary = Color(0xFFB9C3FF), secondary = Color(0xFFBFC7DC), tertiary = Color(0xFFA3D4BC))
    else lightColorScheme(primary = Color(0xFF4057A5), secondary = Color(0xFF565E71), tertiary = Color(0xFF326B52))
    MaterialExpressiveTheme(colorScheme = colors, content = content)
}
