package com.maji.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * 맞이 테마 (#107). 기본(자동)은 차의 낮/밤 모드를 따라가요 (Pleos는 안드로이드 야간 모드를 써요).
 * 사용자가 [자동 · 낮 · 밤] 중에 고르면 그 값을 써요 (ThemeModeSwitch, ThemePreference).
 * 아직 Material 부품을 쓰는 기존 화면도 어색하지 않도록 MaterialTheme 색도 같이 맞춰 둬요.
 */
@Composable
fun MajiTheme(mode: ThemeMode = ThemeMode.AUTO, content: @Composable () -> Unit) {
    val darkTheme = mode.isDark(isSystemInDarkTheme())
    val colors = if (darkTheme) DarkMajiColors else LightMajiColors
    val material = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary,
            background = colors.background, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text, onSurfaceVariant = colors.textSub
        )
    } else {
        lightColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary,
            background = colors.background, onBackground = colors.text,
            surface = colors.background, onSurface = colors.text, onSurfaceVariant = colors.textSub
        )
    }
    CompositionLocalProvider(LocalMajiColors provides colors, LocalMajiType provides DefaultMajiType) {
        MaterialTheme(colorScheme = material, content = content)
    }
}

/** 화면에서 MajiTheme.colors.text 처럼 꺼내 써요 */
object MajiTheme {
    val colors: MajiColors
        @Composable @ReadOnlyComposable get() = LocalMajiColors.current
    val type: MajiType
        @Composable @ReadOnlyComposable get() = LocalMajiType.current
}
