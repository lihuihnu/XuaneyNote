package io.github.lihuihnu.xuaneynote.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.lihuihnu.xuaneynote.model.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF4C6653), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8D6), onPrimaryContainer = Color(0xFF233B2C),
    background = Color(0xFFF9F8F4), surface = Color(0xFFF9F8F4),
    surfaceVariant = Color(0xFFE4E8DF), secondary = Color(0xFF75634B),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFB4D0B6), onPrimary = Color(0xFF203727),
    primaryContainer = Color(0xFF354E3D), onPrimaryContainer = Color(0xFFD0E9D1),
    background = Color(0xFF141915), surface = Color(0xFF141915),
    surfaceVariant = Color(0xFF40483F), secondary = Color(0xFFD4C3A7),
)

@Composable
fun XuaneyTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
