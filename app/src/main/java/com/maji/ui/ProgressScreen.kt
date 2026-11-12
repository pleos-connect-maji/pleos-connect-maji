package com.maji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maji.R
import com.maji.contract.Climate
import com.maji.contract.MajiFlow
import com.maji.contract.MajiStep
import com.maji.contract.Profile
import com.maji.contract.ProfileRepository
import com.maji.ui.theme.MajiCard
import com.maji.ui.theme.MajiGlassButton
import com.maji.ui.theme.MajiPrimaryButton
import com.maji.ui.theme.MajiTheme
import com.maji.ui.theme.MoodLightColors
import com.maji.ui.theme.SoftAvatar
import com.maji.ui.theme.SquircleShape
import com.maji.ui.theme.ThemeMode
import com.maji.voice.GreetingRules
import java.time.LocalDate

// 맞이 진행 화면 (#24, 리디자인 #109). 단계마다 화면이 바뀌어요.
//   HEADING      맞이하러 가는 중 (주행 중 버튼 없음, 정차 중엔 맞이 그만하기)
//   PREPARING    맞이할 준비 중 / 대체 모드면 "직접 맞춰 주세요"
//   BOARDED      어서 와요 + 들려주는 인사
//   RIDING_HOME  함께 가는 중(주행) / 차를 세움 → 다 내렸어요 → 확인 시트
//   FINISHED     오늘도 잘 다녀왔어요 (다 내렸어요로 끝났을 때만 보여요)
// 화면에는 흐름·프로필에 이미 있는 값만 보여요 (남은 시간, 항목별 완료 같은 건 기능이 없어서 안 보여요).

/** 화면 제목과 부제. 받침에 맞춰 조사를 골라요 */
data class ProgressCopy(val title: String, val subtitle: String?)

fun progressCopy(step: MajiStep, name: String, drivingRestricted: Boolean, fallback: Boolean = false, closeWindow: Boolean = true): ProgressCopy =
    when (step) {
        MajiStep.IDLE -> ProgressCopy("누구를 맞이하러 갈까요?", null)
        MajiStep.HEADING -> ProgressCopy("${josa(name, "을", "를")}\n맞이하러 가는 중", null)
        MajiStep.PREPARING ->
            if (fallback) ProgressCopy("$name 자리는\n직접 맞춰 주세요", "이 차에서는 앱이 자리를 바꿀 수 없어요")
            else ProgressCopy("${josa(name, "을", "를")} 맞이할\n준비 중이에요", "열선·온도·시트를 맞추고 있어요")
        MajiStep.BOARDED -> ProgressCopy("$name, 어서 와요", if (fallback) null else "자리를 맞춰 뒀어요")
        MajiStep.RIDING_HOME ->
            if (drivingRestricted) ProgressCopy("${josa(name, "과", "와")}\n함께 가는 중", if (closeWindow) "$name 무드등을 켜고 창문을 닫아요" else "$name 무드등을 켜요")
            else ProgressCopy("${josa(name, "이", "가")} 다 내리면\n눌러 주세요", "자리를 원래대로 돌려요")
        MajiStep.FINISHED ->
            if (fallback) ProgressCopy("오늘도\n잘 다녀왔어요", "자리는 직접 원래대로 돌려 주세요")
            else ProgressCopy("오늘도\n잘 다녀왔어요", "다음에도 $name 자리를 준비해 둘게요")
    }

/** 단계 표시에서 지금 위치 (출발 0 · 도착 1 · 탑승 2 · 함께 3) */
private fun trackIndex(step: MajiStep): Int = when (step) {
    MajiStep.HEADING -> 0
    MajiStep.PREPARING -> 1
    MajiStep.BOARDED -> 2
    else -> 3
}

/**
 * 흐름의 단계를 받아서 진행 화면을 띄워요.
 * controlAvailable이 false면 대체 모드 화면을 써요. 열선·온도는 흐름이 쓰는 계절 값과 같아요.
 * onFinished: "다 내렸어요"로 끝냈을 때 (완료 화면을 보여 주려고 MainActivity가 기억해요)
 * onDone: 완료 화면에서 "처음으로"를 눌렀을 때
 */
@Composable
fun ProgressRoute(
    flow: MajiFlow,
    profiles: ProfileRepository,
    drivingRestricted: Boolean,
    controlAvailable: Boolean,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
    onDone: () -> Unit = {}
) {
    var step by remember { mutableStateOf(flow.step) }
    LaunchedEffect(flow) {
        flow.addListener { step = it }
    }
    val profile = flow.profile
    val climate = profile?.let { profiles.climateFor(it, profiles.seasonFor(LocalDate.now().monthValue)) }
    val presets = stringArrayResource(R.array.greeting_presets).toList()
    ProgressScreen(
        step = step,
        profileName = profile?.name.orEmpty(),
        drivingRestricted = drivingRestricted,
        fallback = !controlAvailable,
        climate = climate,
        profile = profile,
        greeting = profile?.let { GreetingRules.textFor(it, presets) },
        onFinish = { flow.finish(); onFinished() },
        onCancel = { flow.cancel() },
        onDone = onDone,
        modifier = modifier
    )
}

/** 화면만 그려요. 값과 이벤트를 밖에서 받아서 미리보기와 테스트에도 쓸 수 있어요. */
@Composable
fun ProgressScreen(
    step: MajiStep,
    profileName: String,
    drivingRestricted: Boolean,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    fallback: Boolean = false,
    climate: Climate? = null,
    profile: Profile? = null,
    greeting: String? = null,
    onDone: () -> Unit = {}
) {
    val c = MajiTheme.colors
    val color = MoodLightColors.of(profile?.lightColor)
    val initial = initialOf(profileName)
    val copy = progressCopy(step, profileName, drivingRestricted, fallback, profile?.closeWindow ?: true)
    var askFinish by remember { mutableStateOf(false) }  // 다 내렸어요 → 한 번 더 물어봐요
    val canCancel = !drivingRestricted

    Box(modifier.fillMaxSize()) {
        when (step) {
            MajiStep.BOARDED -> MajiScaffold(
                accent = color,
                top = {
                    Spacer(Modifier.height(72.dp))
                    SoftAvatar(initial, color, size = 128.dp)
                    Headline(copy.title)
                    copy.subtitle?.let { SubText(it) }
                },
                content = {
                    if (greeting != null) {
                        MajiCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SubText("들려주는 인사")
                                Text("“$greeting”", style = MajiTheme.type.title, color = c.text)
                            }
                        }
                    }
                    SubText("기어를 D로 바꾸면 함께 출발해요")
                }
            )

            MajiStep.FINISHED -> MajiScaffold(
                accent = color.copy(alpha = 0.6f),
                top = {
                    Spacer(Modifier.height(96.dp))
                    Box(Modifier.size(96.dp).clip(CircleShape).background(c.success.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Text("✓", style = MajiTheme.type.headline, color = c.success)
                    }
                    Headline(copy.title)
                    copy.subtitle?.let { SubText(it) }
                },
                actions = { MajiPrimaryButton("처음으로", onClick = onDone, modifier = Modifier.weight(1f)) }
            )

            else -> MajiScaffold(
                accent = color,
                top = {
                    SoftAvatar(initial, color, size = 104.dp)
                    Headline(copy.title)
                    copy.subtitle?.let { SubText(it) }
                    if (step == MajiStep.HEADING) StepTrack(trackIndex(step), color, initial)
                    if (step == MajiStep.PREPARING && !fallback) {
                        Spacer(Modifier.height(16.dp))
                        SubText("ⓘ 조수석 문을 열면 시트가 바로 멈춰요")
                    }
                },
                content = {
                    when {
                        step == MajiStep.HEADING ->
                            ValueCard(climateRows(climate, profile?.seatMoveMs ?: 0), caption = "차를 세우면 ${profileName} 자리를 맞춰 둘게요")
                        step == MajiStep.PREPARING && fallback ->
                            ValueCard(climateRows(climate), mark = "!", markColor = c.warning)
                        step == MajiStep.PREPARING ->
                            ValueCard(climateRows(climate, profile?.seatMoveMs ?: 0))
                        step == MajiStep.RIDING_HOME && drivingRestricted -> {
                            val light = MoodLightColors.all.firstOrNull { it.second == color }?.first
                            ValueCard(buildList {
                                add("무드등" to listOfNotNull(light, "${profile?.lightBrightness ?: 30}%").joinToString(" · "))
                                add("창문" to if (profile?.closeWindow != false) "닫기" else "그대로")
                            })
                            SubText("차를 세우면 ‘다 내렸어요’ 버튼이 나와요")
                        }
                    }
                },
                actions = when {
                    step == MajiStep.RIDING_HOME && !drivingRestricted && !askFinish -> {
                        { MajiPrimaryButton("다 내렸어요", onClick = { askFinish = true }, modifier = Modifier.weight(1f)) }
                    }
                    (step == MajiStep.HEADING || step == MajiStep.PREPARING) && canCancel -> {
                        { MajiGlassButton("맞이 그만하기", onClick = onCancel, modifier = Modifier.weight(1f)) }
                    }
                    else -> null
                }
            )
        }

        if (askFinish && step == MajiStep.RIDING_HOME) {
            ConfirmSheet(
                title = "자리를 원래대로 돌릴까요?",
                body = "시트·열선·온도·창문·무드등이\n${josa(profileName, "이", "가")} 타기 전으로 돌아가요",
                confirm = "원래대로 돌리기",
                onDismiss = { askFinish = false },
                onConfirm = { askFinish = false; onFinish() }
            )
        }
    }
}

// ───────────── 미리보기 ─────────────

private val previewMom = Profile(id = 1, name = "엄마", winter = Climate(seatHeat = 2, temperature = 22f), seatMoveMs = 2400, lightColor = 0xFFF59AC2.toInt())

@Composable
private fun P(step: MajiStep, driving: Boolean = false, fallback: Boolean = false) =
    ProgressScreen(step, "엄마", driving, {}, {}, fallback = fallback, climate = previewMom.winter, profile = previewMom, greeting = "오늘도 고생 많았어요")

@Preview(name = "가는 중 · 넓은 창", widthDp = 1682, heightDp = 1120)
@Composable private fun HeadingPreview() = MajiTheme(ThemeMode.LIGHT) { P(MajiStep.HEADING, driving = true) }

@Preview(name = "맞이 준비 · 반쪽 창 · 밤", widthDp = 841, heightDp = 1120)
@Composable private fun PreparingPreview() = MajiTheme(ThemeMode.DARK) { P(MajiStep.PREPARING) }

@Preview(name = "탑승 · 반쪽 창", widthDp = 841, heightDp = 1120)
@Composable private fun BoardedPreview() = MajiTheme(ThemeMode.LIGHT) { P(MajiStep.BOARDED) }

@Preview(name = "차를 세움 · 넓은 창 · 밤", widthDp = 1682, heightDp = 1120)
@Composable private fun StoppedPreview() = MajiTheme(ThemeMode.DARK) { P(MajiStep.RIDING_HOME) }

@Preview(name = "완료 · 반쪽 창", widthDp = 841, heightDp = 1120)
@Composable private fun FinishedPreview() = MajiTheme(ThemeMode.LIGHT) { P(MajiStep.FINISHED) }

@Preview(name = "대체 모드 · 반쪽 창", widthDp = 841, heightDp = 1120)
@Composable private fun FallbackPreview() = MajiTheme(ThemeMode.LIGHT) { P(MajiStep.PREPARING, fallback = true) }
