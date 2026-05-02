package vn.anticode.mobile.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    secondary = Secondary,
    tertiary = Accent,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Background,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = Error,
    outline = Border
)

@Composable
fun AnticodeTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            @Suppress("DEPRECATION")
            activity.window.statusBarColor = Background.toArgb()
            @Suppress("DEPRECATION")
            activity.window.navigationBarColor = Background.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
