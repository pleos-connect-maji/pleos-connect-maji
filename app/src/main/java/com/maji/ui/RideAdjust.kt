package com.maji.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maji.contract.AmbientLight
import com.maji.profile.ProfileRules
import com.maji.ui.theme.MajiCard
import com.maji.ui.theme.MajiTheme
import com.maji.ui.theme.MoodLightColors

// 함께 가는 동안 자리 조절 (#111). 옆에 탄 사람이 타는 내내 열선·온도·시트·무드등을 바꿀 수 있어요.
// 주행 중에도 열어 둬요: Pleos 가이드상 한 손 단순 조작(누르기·누르고 있기)은 허용, 글자 입력은 없어요.
// 여기서 바꾼 값은 "다 내렸어요" 때 그 사람 설정으로 기억돼요 (흐름 C).
// 시트는 주행 중에 차가 거절해요(에뮬레이터 확인: D단에서 출발 거절 50001). 그래서 시트만 정차 중에 열어요.
// 시트는 차 버튼으로 움직이면 앱이 알 수 없어서(SDK에 위치가 없음) 여기서 움직인 것만 기억해요.

private const val LIGHT_STEP = 10
private const val TEMP_STEP = 0.5f

@Composable
fun RideAdjustPanel(
    heat: Int,
    temperature: Float?,          // null = 아직 모름 (에뮬레이터는 못 읽어요). 누르면 기본 22℃에서 시작
    seatMs: Int,                  // 원래 자리에서 움직인 시간
    seatEnabled: Boolean,         // 주행 중이면 false (차가 시트 이동을 막아요)
    light: AmbientLight,
    onHeat: (Int) -> Unit,
    onTemperature: (Float) -> Unit,
    onSeatStart: (forward: Boolean) -> Unit,
    onSeatStop: () -> Unit,
    onLight: (AmbientLight) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MajiTheme.colors
    MajiCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("자리 조절", style = MajiTheme.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text)
                SubText("타는 동안 바꿔도 돼요 · 내릴 때 값을 기억해요")
            }

            // 열선·통풍: [끔][열선][통풍] + 세기
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeatMode.entries.forEach { m ->
                    Chip(m.label, selected = HeatMode.of(heat) == m, enabled = true, modifier = Modifier.weight(1f)) {
                        onHeat(HeatMode.levelOf(m, kotlin.math.abs(heat)))
                    }
                }
                HeatStrength(heat, enabled = true, onChange = onHeat, modifier = Modifier.weight(1.6f))
            }

            // 온도 | 시트
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubText("온도")
                    val t = temperature ?: DEFAULT_TEMP
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepButton("−", t > ProfileRules.TEMP_MIN) { onTemperature((t - TEMP_STEP).coerceAtLeast(ProfileRules.TEMP_MIN)) }
                        Text(
                            temperature?.let { tempLabel(it) } ?: "모름",
                            style = MajiTheme.type.title.copy(fontFeatureSettings = "tnum"), color = c.text,
                            textAlign = TextAlign.Center, modifier = Modifier.weight(1f).widthIn(min = 120.dp)
                        )
                        StepButton("+", t < ProfileRules.TEMP_MAX) { onTemperature((t + TEMP_STEP).coerceAtMost(ProfileRules.TEMP_MAX)) }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubText(if (seatEnabled) "시트 · ${seatText(seatMs)}" else "시트 · 차를 세우면 움직여요")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HoldButton("◀ 뒤", seatEnabled && seatMs > -ProfileRules.SEAT_MOVE_MAX_MS, { onSeatStart(false) }, onSeatStop, Modifier.weight(1f))
                        HoldButton("앞 ▶", seatEnabled && seatMs < ProfileRules.SEAT_MOVE_MAX_MS, { onSeatStart(true) }, onSeatStop, Modifier.weight(1f))
                    }
                }
            }

            // 무드등: 색 6개 + 밝기
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubText("무드등", Modifier.weight(1f))
                    StepButton("−", light.brightness > ProfileRules.BRIGHTNESS_MIN) {
                        onLight(light.copy(brightness = (light.brightness - LIGHT_STEP).coerceAtLeast(ProfileRules.BRIGHTNESS_MIN)))
                    }
                    Text(
                        "${light.brightness}%", style = MajiTheme.type.title.copy(fontFeatureSettings = "tnum"), color = c.text,
                        textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 120.dp)
                    )
                    StepButton("+", light.brightness < ProfileRules.BRIGHTNESS_MAX) {
                        onLight(light.copy(brightness = (light.brightness + LIGHT_STEP).coerceAtMost(ProfileRules.BRIGHTNESS_MAX)))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MoodLightColors.all.forEach { (name, color) ->
                        val argb = color.toArgb()
                        ColorChoice(name, color, selected = (light.color and 0xFFFFFF) == (argb and 0xFFFFFF), enabled = true) {
                            onLight(light.copy(color = argb))
                        }
                    }
                }
            }
        }
    }
}
