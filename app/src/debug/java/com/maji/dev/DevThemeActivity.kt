package com.maji.dev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maji.ui.theme.MajiBackground
import com.maji.ui.theme.MajiCard
import com.maji.ui.theme.MajiGlassButton
import com.maji.ui.theme.MajiPrimaryButton
import com.maji.ui.theme.MajiRow
import com.maji.ui.theme.MajiTheme
import com.maji.ui.theme.MoodLightColors
import com.maji.ui.theme.SoftAvatar
import com.maji.ui.theme.ThemeModeSwitch
import com.maji.ui.theme.ThemePreference

/**
 * 맞이 테마 확인 화면 (#107, debug 빌드에만 들어가요).
 * 색·글자·유리 버튼·보송한 원·낮밤 선택을 실제 차 화면 크기에서 봐요.
 *
 * 실행: adb shell am start -n com.maji/com.maji.dev.DevThemeActivity
 */
class DevThemeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pref = ThemePreference(this)
        setContent {
            var mode by remember { mutableStateOf(pref.load()) }
            MajiTheme(mode) {
                val c = MajiTheme.colors
                var color by remember { mutableStateOf(MoodLightColors.all[1].second) }
                MajiBackground(accent = color) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(48.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        ThemeModeSwitch(mode, onChange = { mode = it; pref.save(it) })
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            MoodLightColors.all.forEach { (name, col) ->
                                Column(modifier = Modifier.padding(4.dp)) {
                                    SoftAvatar("엄", col)
                                    Text(name, style = MajiTheme.type.caption, color = c.textSub)
                                }
                            }
                        }
                        Text("엄마를 맞이할\n준비 중이에요", style = MajiTheme.type.headline, color = c.text)
                        MajiCard(Modifier.fillMaxWidth()) {
                            MajiRow("열선", "2단")
                            MajiRow("온도", "22℃")
                            MajiRow("시트", "앞으로 2.4초")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            MajiGlassButton("다음 색", onClick = {
                                val list = MoodLightColors.all.map { it.second }
                                color = list[(list.indexOf(color) + 1) % list.size]
                            }, modifier = Modifier.weight(1f))
                            MajiPrimaryButton("다 내렸어요", onClick = {}, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
