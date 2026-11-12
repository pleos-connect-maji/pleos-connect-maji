package com.maji.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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

@OptIn(ExperimentalLayoutApi::class)
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
    modifier: Modifier = Modifier,
    season: String? = null,       // "겨울" → 어느 계절 칸에 기억되는지 알려 줘요 (열선·온도는 계절마다 따로)
    boarded: Boolean = false      // 탄 직후(어서 와요, #113): 무드등은 빼요. 출발할 때 프로필 색으로 켜져서 여기서 바꿔도 덮여요
) {
    val c = MajiTheme.colors
    MajiCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("자리 조절", style = MajiTheme.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text)
                SubText(
                    when {
                        boarded -> "시트는 출발 전에 맞춰 두면 편해요 · 무드등은 출발하면 켜져요"
                        season != null -> "타는 동안 바꿔도 돼요 · 내릴 때 $season 자리로 기억해요"
                        else -> "타는 동안 바꿔도 돼요 · 내릴 때 값을 기억해요"
                    }
                )
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

            // 무드등: 색 6개 + 밝기 (함께 가는 중에만)
            if (!boarded) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                // 폭이 모자라면(넓은 창 경계 1100dp 근처는 패널이 약 400dp) 두 줄로 넘겨서 원마다 76dp 누르는 영역을 지켜요 (#112 리뷰)
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
