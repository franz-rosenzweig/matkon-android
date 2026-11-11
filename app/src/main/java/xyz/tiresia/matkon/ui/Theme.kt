package xyz.tiresia.matkon.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Matkon's own colours (the web app's warm paper and paprika).
private val Light =
    lightColorScheme(
        primary = Color(0xFFB8441A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF6E3D9),
        onPrimaryContainer = Color(0xFF6B240A),
        secondary = Color(0xFF6E685F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF6E3D9),
        onSecondaryContainer = Color(0xFF6B240A),
        tertiary = Color(0xFF2F7A4E),
        onTertiary = Color.White,
        background = Color(0xFFF5F2EC),
        onBackground = Color(0xFF1E1C19),
        surface = Color(0xFFF5F2EC),
        onSurface = Color(0xFF1E1C19),
        surfaceVariant = Color(0xFFEDE8DF),
        onSurfaceVariant = Color(0xFF6E685F),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color.White,
        surfaceContainer = Color(0xFFFBF9F5),
        surfaceContainerHigh = Color(0xFFF1EDE6),
        surfaceContainerHighest = Color(0xFFEDE8DF),
        outline = Color(0xFFA29B90),
        outlineVariant = Color(0xFFE5DFD4),
        inversePrimary = Color(0xFFFFB08C),
    )

private val Dark =
    darkColorScheme(
        primary = Color(0xFFF0804A),
        onPrimary = Color(0xFF1A0E07),
        primaryContainer = Color(0xFF3A2419),
        onPrimaryContainer = Color(0xFFFFD9C8),
        secondary = Color(0xFFA49D93),
        onSecondary = Color(0xFF141312),
        secondaryContainer = Color(0xFF3A2419),
        onSecondaryContainer = Color(0xFFFFD9C8),
        tertiary = Color(0xFF6CC291),
        onTertiary = Color(0xFF0B1F13),
        background = Color(0xFF141312),
        onBackground = Color(0xFFEDE9E3),
        surface = Color(0xFF141312),
        onSurface = Color(0xFFEDE9E3),
        surfaceVariant = Color(0xFF2A2724),
        onSurfaceVariant = Color(0xFFA49D93),
        surfaceContainerLowest = Color(0xFF1E1C1A),
        surfaceContainerLow = Color(0xFF1E1C1A),
        surfaceContainer = Color(0xFF1B1917),
        surfaceContainerHigh = Color(0xFF24211F),
        surfaceContainerHighest = Color(0xFF2A2724),
        outline = Color(0xFF6F6961),
        outlineVariant = Color(0xFF2F2C29),
        inversePrimary = Color(0xFFB8441A),
    )

@Composable
fun MatkonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

val ColorScheme.done get() = tertiary
