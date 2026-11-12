package com.maji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import com.maji.ui.theme.MajiGlassButton
import com.maji.ui.theme.MajiPrimaryButton
import com.maji.ui.theme.SquircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maji.contract.Climate
import com.maji.ui.theme.MajiBackground
import com.maji.ui.theme.MajiCard
import com.maji.ui.theme.MajiRow
import com.maji.ui.theme.MajiTheme
import java.util.Locale

// 화면들이 같이 쓰는 부품과 문구 도우미 (#109).

// ───────────── 문구 ─────────────

/** 받침에 따라 조사를 골라요: josa("엄마", "을", "를") = "엄마를", josa("동생", "을", "를") = "동생을" */
fun josa(word: String, withBatchim: String, withoutBatchim: String): String {
    val last = word.lastOrNull() ?: return word + withoutBatchim
    val batchim = last in '가'..'힣' && (last.code - 0xAC00) % 28 != 0
    return word + if (batchim) withBatchim else withoutBatchim
}

/** 열선 값을 줄 이름과 값으로: 2 → ("열선", "2단"), -1 → ("통풍", "1단"), 0 → ("열선", "끔") */
fun heatRow(level: Int): Pair<String, String> = when {
    level < 0 -> "통풍" to "${-level}단"
    level == 0 -> "열선" to "끔"
    else -> "열선" to "${level}단"
}

/** 열선 상태 (#111): 끔 / 열선 / 통풍. 차 값은 -3..3 하나지만 화면에서는 나눠서 골라요 */
enum class HeatMode(val label: String) {
    OFF("끔"), HEAT("열선"), VENT("통풍");

    companion object {
        fun of(level: Int): HeatMode = when {
            level > 0 -> HEAT
            level < 0 -> VENT
            else -> OFF
        }

        /** 모드와 세기로 차 값을 만들어요. 끔에서 열선·통풍을 고르면 1단부터 */
        fun levelOf(mode: HeatMode, strength: Int): Int = when (mode) {
            OFF -> 0
            HEAT -> strength.coerceIn(1, 3)
            VENT -> -strength.coerceIn(1, 3)
        }
    }
}

/** 2 → "열선 2단", -1 → "통풍 1단", 0 → "끔" */
fun heatText(level: Int): String = heatRow(level).let { (k, v) -> if (v == "끔") "끔" else "$k $v" }

/** 22.0 → "22℃", 22.5 → "22.5℃" */
fun tempText(celsius: Float): String =
    if (celsius % 1f == 0f) "${celsius.toInt()}℃" else String.format(Locale.US, "%.1f℃", celsius)

/** 0 → "원래 위치", 2400 → "앞으로 2.4초", -800 → "뒤로 0.8초". 0.05초보다 작으면(0.0초로 보여서) 원래 위치 */
fun seatText(ms: Int): String = when {
    kotlin.math.abs(ms) < 50 -> "원래 위치"
    ms > 0 -> String.format(Locale.US, "앞으로 %.1f초", ms / 1000f)
    else -> String.format(Locale.US, "뒤로 %.1f초", -ms / 1000f)
}

/** 맞출 값 목록 (값이 없는 칸은 빼요). 시트는 0이 아니면 넣어요 */
fun climateRows(climate: Climate?, seatMoveMs: Int = 0): List<Pair<String, String>> = buildList {
    climate?.seatHeat?.let { add(heatRow(it)) }
    climate?.temperature?.let { add("온도" to tempText(it)) }
    if (seatMoveMs != 0) add("시트" to seatText(seatMoveMs))
}

/** 계절 값 한 줄: "열선 3단 · 26.5℃". 아직 안 맞춘 계절이면 null */
fun seasonValueText(climate: Climate?): String? {
    if (climate == null) return null
    return buildList {
        climate.seatHeat?.let { add(heatText(it).let { t -> if (t == "끔") "열선 끔" else t }) }
        climate.temperature?.let { add(tempText(it)) }
    }.ifEmpty { listOf("온도 그대로") }.joinToString(" · ")
}

fun seasonName(s: com.maji.contract.Season): String = if (s == com.maji.contract.Season.WINTER) "겨울" else "여름"

fun initialOf(name: String): String = name.trim().take(1).ifEmpty { "?" }

// ───────────── 화면 틀 ─────────────

/** 넓은 창(앱 하나, 약 1682dp)인지. 반쪽 창(약 841dp)이면 위에서 아래로 쌓아요 */
const val WIDE_MIN_DP = 1100

/**
 * 진행 화면들의 공통 틀. 운전자가 힐끗 봐도 한눈에 들어오게 내용을 한 덩어리로 모아요.
 * 덩어리는 화면 가운데보다 살짝 위(시각적 중심, 위:아래 여백 4:6)에 둬요.
 * 넓은 창: 왼쪽 = 사람과 상황(top), 오른쪽 = 내용(content) + 바로 아래 버튼. 왼쪽(운전석 쪽)부터 읽혀요.
 * 반쪽 창: top → content 덩어리를 시각적 중심에, 버튼은 손이 닿기 쉬운 맨 아래.
 */
@Composable
fun MajiScaffold(
    accent: Color?,
    modifier: Modifier = Modifier,
    top: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
    actions: (@Composable RowScope.() -> Unit)? = null,
    scrollContent: Boolean = false  // 반쪽 창에서 내용이 길면(자리 조절 #111) 위·내용만 스크롤, 버튼은 맨 아래 고정
) {
    MajiBackground(modifier = modifier, accent = accent) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth.value >= WIDE_MIN_DP
            if (wide) {
                Column(Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 56.dp)) {
                    Spacer(Modifier.weight(0.4f))
                    // 오른쪽(내용·버튼)은 왼쪽 덩어리의 세로 가운데에 맞춰요.
                    // 내용 없이 버튼만 있을 때 버튼이 위에 붙어 보이지 않게 ("다 내렸어요" 등)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(64.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), content = top)
                        Column(Modifier.weight(1.08f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            content()
                            if (actions != null) {
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), content = actions)
                            }
                        }
                    }
                    Spacer(Modifier.weight(0.6f))
                }
            } else if (scrollContent) {
                Column(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = top)
                        content()
                    }
                    if (actions != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), content = actions)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Spacer(Modifier.weight(0.4f))
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = top)
                    content()
                    Spacer(Modifier.weight(0.6f))
                    if (actions != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), content = actions)
                    }
                }
            }
        }
    }
}

@Composable
fun Headline(text: String) {
    Text(text, style = MajiTheme.type.headline, color = MajiTheme.colors.text)
}

@Composable
fun SubText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MajiTheme.type.caption, color = MajiTheme.colors.textSub, modifier = modifier)
}

/** 값 목록 카드. caption은 카드 위쪽 작은 설명 */
@Composable
fun ValueCard(rows: List<Pair<String, String>>, caption: String? = null, mark: String? = null, markColor: Color = Color.Unspecified) {
    // 값이 없으면 카드를 아예 안 그려요 (설명 한 줄만 든 빈 카드는 읽히지도 않고 어색해요)
    if (rows.isEmpty()) return
    MajiCard(Modifier.fillMaxWidth()) {
        if (caption != null) {
            // 카드 제목: 보조 회색 작은 글씨가 아니라 본문 크기·진한 글씨로 또렷하게
            Text(caption, style = MajiTheme.type.body.copy(fontWeight = FontWeight.SemiBold), color = MajiTheme.colors.text,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 4.dp))
        }
        rows.forEach { (label, value) ->
            if (mark != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(mark, style = MajiTheme.type.title, color = markColor, modifier = Modifier.padding(start = 32.dp))
                    MajiRow(label, value)
                }
            } else {
                MajiRow(label, value)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 출발 · 도착 · 탑승 · 함께 가는 길 단계 표시. 지금 단계 위에는 그 사람 원이 있어요 */
@Composable
fun StepTrack(current: Int, personColor: Color, initial: String) {
    val c = MajiTheme.colors
    val labels = listOf("출발", "도착", "탑승", "함께")
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            labels.indices.forEach { i ->
                val done = i <= current
                if (i == current) {
                    com.maji.ui.theme.SoftAvatar(initial, personColor, size = 48.dp)
                } else {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(if (done) c.text else c.textSub.copy(alpha = 0.3f)))
                }
                if (i < labels.lastIndex) {
                    Box(Modifier.weight(1f).height(3.dp).background(if (i < current) c.text else c.textSub.copy(alpha = 0.25f)))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(it, style = MajiTheme.type.caption, color = c.textSub) }
        }
    }
}

/** 작은 원 버튼 (카드의 ✎ 같은 것). 누르는 영역은 76dp */
@Composable
fun RoundGlassButton(symbol: String, onClick: () -> Unit, enabled: Boolean = true, size: Dp = 76.dp, modifier: Modifier = Modifier) {
    val c = MajiTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clickable(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(c.glass)
    ) {
        Text(symbol, style = MajiTheme.type.title, color = c.text.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

@Composable
fun HSpace(width: Dp) = Spacer(Modifier.width(width))

/** 아래에서 떠오르는 확인 시트. 왼쪽 [닫기], 오른쪽 실행 (토스 문구 규칙) */
@Composable
fun ConfirmSheet(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val c = MajiTheme.colors
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    ) {
        val wide = maxWidth.value >= WIDE_MIN_DP
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp)
                .then(if (wide) Modifier.widthIn(max = 900.dp) else Modifier.fillMaxWidth())
                .clip(SquircleShape(56.dp))
                .background(c.sheet)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, style = MajiTheme.type.title, color = c.text)
            SubText(body)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MajiGlassButton("닫기", onClick = onDismiss, modifier = Modifier.weight(1f))
                MajiPrimaryButton(confirm, onClick = onConfirm, modifier = Modifier.weight(1.5f))
            }
        }
    }
}

