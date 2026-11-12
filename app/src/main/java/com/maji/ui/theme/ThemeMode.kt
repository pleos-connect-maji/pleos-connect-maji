package com.maji.ui.theme

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** 화면 밝기 선택. 기본은 차를 따라가요(자동) */
enum class ThemeMode(val label: String) { AUTO("자동"), LIGHT("낮"), DARK("밤") }

/** 고른 화면 밝기를 앱 저장소에 기억해요 (앱을 다시 켜도 그대로) */
class ThemePreference(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("maji_ui", Context.MODE_PRIVATE)

    fun load(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString(KEY, ThemeMode.AUTO.name)!!) }.getOrDefault(ThemeMode.AUTO)

    fun save(mode: ThemeMode) = prefs.edit().putString(KEY, mode.name).apply()

    private companion object { const val KEY = "theme_mode" }
}

/** 자동이면 차의 낮/밤을, 아니면 고른 값을 써요 */
fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.AUTO -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * 작은 화면 밝기 고르기 [자동 · 낮 · 밤]. 홈 구석에 작게 둬요.
 * 눈에 보이는 칩은 작지만 누르는 영역은 76dp 높이를 지켜요.
 */
@Composable
fun ThemeModeSwitch(mode: ThemeMode, onChange: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    val c = MajiTheme.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.clip(CircleShape).background(c.glass).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ThemeMode.entries.forEach { m ->
                val on = m == mode
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = MinTouch - 8.dp)  // 바깥 여백 4dp×2를 더해 76dp
                        .semantics { selected = on }
                        .clickable(role = Role.Tab) { onChange(m) }
                        .clip(CircleShape)
                        .background(if (on) c.primary else Color.Transparent)
                        .padding(horizontal = 26.dp)
                ) {
                    Text(m.label, style = MajiTheme.type.caption, color = if (on) c.onPrimary else c.textSub)
                }
            }
        }
    }
}
