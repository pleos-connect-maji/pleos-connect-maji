package com.maji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maji.contract.Climate
import com.maji.contract.Profile
import com.maji.contract.ProfileRepository
import com.maji.contract.Season
import com.maji.contract.VehicleControl
import com.maji.profile.FakeProfileRepository
import com.maji.profile.ProfileRules
import com.maji.ui.theme.MajiBackground
import com.maji.ui.theme.MajiCard
import com.maji.ui.theme.MajiGlassButton
import com.maji.ui.theme.MajiPrimaryButton
import com.maji.ui.theme.MajiTheme
import com.maji.ui.theme.MinTouch
import com.maji.ui.theme.MoodLightColors
import com.maji.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

// D. 자리 설정 화면 (#46, 리디자인 #109). 탑승자가 탄 뒤 정차 중(P단)에 열선·온도를 맞추고 그 계절에 저장해요.
// 띄우는 때 (C가 정해요): climateFor(profile, 오늘 계절) == null 일 때
//   - 두 계절 다 비어 있으면 "처음 맞이하네요"
//   - 이번 계절만 비어 있으면 "겨울이 왔어요" 처럼
//
// 차와 연결되는 값 (C가 넘겨 줘요):
//   currentHeat / currentTemp  = 지금 차 상태 (A의 onPassengerHeatChanged / onPassengerTemperatureChanged)
//                                 탑승자가 차 버튼으로 바꿔도 화면이 따라 바뀌어요
//   control = B의 VehicleControl. 흐름(C)이 쓰는 것과 "같은 것 하나"를 넘겨 줘야 해요
//             (따로 만들면 시트 움직인 기록이 나뉘어서 하차 때 원위치가 안 돼요)
//     열선 버튼 → control.setSeatHeat(-3..3), 온도 버튼 → control.setTemperature (그대로 두기면 안 보냄)
//     시트 ◀ ▶ → 누르는 동안 control.startSeatMove(forward), 떼면 control.stopSeatMove()가 움직인 ms를 알려 줘요
//   control이 null이면(미리보기·테스트) 화면만 바뀌고 차에는 안 보내요

const val DEFAULT_TEMP = 22.0f
private const val TEMP_STEP = 0.5f

/** 저장하면 그 계절 칸에만 들어가요 (다른 계절, 이름, 인사는 그대로) */
@Composable
fun SeatSetupRoute(
    repo: ProfileRepository,
    profile: Profile,
    currentHeat: Int?,
    currentTemp: Float?,
    drivingRestricted: Boolean,
    onSaved: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    control: VehicleControl? = null,
    onHeatChange: (Int) -> Unit = {},      // control 대신 직접 연결하고 싶을 때만 (control이 있으면 안 써요)
    onTempChange: (Float?) -> Unit = {},
    month: Int = LocalDate.now().monthValue
) {
    val scope = rememberCoroutineScope()
    SeatSetupScreen(
        profile = profile,
        initialSeason = repo.seasonFor(month),
        currentHeat = currentHeat,
        currentTemp = currentTemp,
        drivingRestricted = drivingRestricted,
        onHeatChange = { level -> if (control != null) control.setSeatHeat(level) else onHeatChange(level) },
        onTempChange = { t ->
            if (control != null) t?.let { control.setTemperature(it) }  // null(그대로 두기)이면 안 보냄
            else onTempChange(t)
        },
        onSeatMoveStart = { forward -> control?.startSeatMove(forward) },
        onSeatMoveStop = { control?.stopSeatMove() ?: 0 },
        onSave = { season, climate, seatMoveMs ->
            scope.launch {
                val latest = repo.get(profile.id) ?: profile  // 그사이 바뀐 이름·인사를 덮어쓰지 않게
                repo.save(ProfileRules.withClimate(latest, season, climate).copy(seatMoveMs = seatMoveMs))
                onSaved()
            }
        },
        onSkip = onSkip,
        modifier = modifier
    )
}

@Composable
fun SeatSetupScreen(
    profile: Profile,
    initialSeason: Season,
    currentHeat: Int?,
    currentTemp: Float?,
    drivingRestricted: Boolean,
    onHeatChange: (Int) -> Unit,
    onTempChange: (Float?) -> Unit,
    onSeatMoveStart: (forward: Boolean) -> Unit,
    onSeatMoveStop: () -> Int,             // 이번에 움직인 ms (앞 +, 뒤 -)
    onSave: (Season, Climate, Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 계절은 날짜로 정해져요 (흐름도 seasonFor(오늘)로 씀). 고르는 버튼은 뺐어요 (원영 의견, #111)
    val season = initialSeason
    val saved = ProfileRules.climateFor(profile, season)

    // 시작 값: 그 계절에 저장된 값 → 없으면 지금 차 상태 → 없으면 기본값
    var heat by remember { mutableIntStateOf(saved?.seatHeat ?: currentHeat ?: 0) }
    var temp by remember { mutableFloatStateOf(saved?.temperature ?: currentTemp ?: DEFAULT_TEMP) }
    var keepTemp by remember { mutableStateOf(saved != null && saved.temperature == null) }
    // 시트: 원래 위치에서 얼마나 움직였는지 (맞이 준비 때 이미 profile.seatMoveMs만큼 움직여 둔 상태에서 시작)
    var seatMs by remember { mutableIntStateOf(profile.seatMoveMs) }

    // 탑승자가 차 버튼으로 바꾸면 화면도 따라가요 (A 신호)
    LaunchedEffect(currentHeat) { currentHeat?.let { heat = it } }
    // 그대로 두기 중에도 지금 차 온도는 기억해 둬요 → 그 상태에서 + 누르면 지금 온도 + 0.5부터
    LaunchedEffect(currentTemp) { currentTemp?.let { temp = it } }

    val firstTime = profile.winter == null && profile.summer == null
    val seasonChanged = !firstTime && ProfileRules.climateFor(profile, initialSeason) == null
    val title = when {
        firstTime -> "${josa(profile.name, "을", "를")} 처음 맞이하네요"
        seasonChanged -> "${seasonName(initialSeason)}이 왔어요"
        else -> "${profile.name} 자리 다시 맞추기"
    }
    val subtitle = when {
        seasonChanged -> "${profile.name} ${seasonName(initialSeason)} 자리를 맞춰 볼까요? 누르면 차에 바로 반영돼요"
        else -> "자리를 맞춰 볼까요? 누르면 차에 바로 반영돼요"
    }
    val locked = drivingRestricted
    val c = MajiTheme.colors

    val heatCard: @Composable (Modifier) -> Unit = { m ->
        HeatCard(
            level = heat,
            now = "지금 차 " + (currentHeat?.let { heatText(it) } ?: "모름"),
            enabled = !locked,
            onChange = { heat = it; onHeatChange(it) },
            modifier = m
        )
    }
    val tempCard: @Composable (Modifier) -> Unit = { m ->
        SettingCard(
            title = "조수석 온도",
            now = "지금 차 " + (currentTemp?.let { tempLabel(it) } ?: "모름"),
            value = if (keepTemp) "그대로 두기" else tempLabel(temp),
            // 그대로 두기 상태에서도 − + 를 누르면 바로 온도 조절로 바뀌어요
            canDown = !locked && temp > ProfileRules.TEMP_MIN,
            canUp = !locked && temp < ProfileRules.TEMP_MAX,
            onDown = { keepTemp = false; temp -= TEMP_STEP; onTempChange(temp) },
            onUp = { keepTemp = false; temp += TEMP_STEP; onTempChange(temp) },
            modifier = m
        ) {
            Chip("그대로 두기", selected = keepTemp, enabled = !locked) {
                keepTemp = !keepTemp
                onTempChange(if (keepTemp) null else temp)
            }
        }
    }
    // 시트: 누르고 있는 동안만 움직여요 (떼는 신호를 놓쳐도 10초면 B가 알아서 멈춰요)
    val seatCard: @Composable (Modifier) -> Unit = { m ->
        MajiCard(m) {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SubText("시트 앞뒤 · 누르는 동안 움직여요")
                Text(
                    seatText(seatMs), style = MajiTheme.type.title.copy(fontFeatureSettings = "tnum"), color = c.text,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HoldButton(
                        text = "◀ 뒤",
                        enabled = !locked && seatMs > -ProfileRules.SEAT_MOVE_MAX_MS,
                        onPress = { onSeatMoveStart(false) },
                        onRelease = { seatMs = (seatMs + onSeatMoveStop()).coerceIn(-ProfileRules.SEAT_MOVE_MAX_MS, ProfileRules.SEAT_MOVE_MAX_MS) },
                        modifier = Modifier.weight(1f)
                    )
                    HoldButton(
                        text = "앞 ▶",
                        enabled = !locked && seatMs < ProfileRules.SEAT_MOVE_MAX_MS,
                        onPress = { onSeatMoveStart(true) },
                        onRelease = { seatMs = (seatMs + onSeatMoveStop()).coerceIn(-ProfileRules.SEAT_MOVE_MAX_MS, ProfileRules.SEAT_MOVE_MAX_MS) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    MajiBackground(modifier = modifier, accent = MoodLightColors.of(profile.lightColor)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth.value >= WIDE_MIN_DP
            Column(
                Modifier.fillMaxSize().padding(horizontal = if (wide) 72.dp else 44.dp, vertical = 48.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 넓은 창: 제목·계절·카드·버튼을 한 덩어리로 시각적 중심(위:아래 4:6)에 둬요
                if (wide) Spacer(Modifier.weight(0.4f))
                Headline(title)
                SubText(if (locked) "주행 중에는 바꿀 수 없어요. 차를 세운 뒤 해 주세요." else subtitle)
                // 계절은 고르지 않고 알려만 줘요
                SubText("지금은 ${seasonName(season)}이라 ${seasonName(season)} 자리로 저장돼요")
                // ── 열선 | 온도 | 시트 (넓은 창은 나란히, 반쪽 창은 위아래)
                if (wide) {
                    // 카드 세 장은 내용 높이에 맞추고 서로 같은 높이로 (화면 높이를 꽉 채우면 가운데가 비어 보여요)
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        heatCard(Modifier.weight(1f).fillMaxHeight())
                        tempCard(Modifier.weight(1f).fillMaxHeight())
                        seatCard(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    // 반쪽 창은 세로가 모자랄 수 있어서 카드만 스크롤해요 (버튼은 항상 보여요)
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        heatCard(Modifier.fillMaxWidth())
                        tempCard(Modifier.fillMaxWidth())
                        seatCard(Modifier.fillMaxWidth())
                    }
                }
                // 버튼은 카드 줄과 같은 격자에 맞춰요: [다음에] = 카드 한 장 폭, [저장] = 카드 두 장 폭.
                // 한쪽 끝으로 쏠리지 않고 카드 바로 아래 꽉 차게 놓여서, 운전석 쪽에서도 저장이 가까워요
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (wide) 24.dp else 16.dp)) {
                    MajiGlassButton("다음에", onClick = onSkip, modifier = Modifier.weight(1f))
                    MajiPrimaryButton(
                        "저장",
                        onClick = { onSave(season, Climate(seatHeat = heat, temperature = if (keepTemp) null else temp), seatMs) },
                        enabled = !locked,
                        modifier = Modifier.weight(if (wide) 2f else 1.5f)
                    )
                }
                if (wide) Spacer(Modifier.weight(0.6f))
            }
        }
    }
}

/** 누르는 순간 onPress, 손을 떼면(또는 화면이 닫히면) onRelease. 유리 캡슐 모양 */
@Composable
internal fun HoldButton(text: String, enabled: Boolean, onPress: () -> Unit, onRelease: () -> Unit, modifier: Modifier = Modifier) {
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    var pressed by remember { mutableStateOf(false) }
    val c = MajiTheme.colors

    Box(
        modifier = modifier
            .heightIn(min = MinTouch)
            .alpha(if (enabled) 1f else 0.4f)
            .graphicsLayer { val s = if (pressed) 0.95f else 1f; scaleX = s; scaleY = s }  // 누르는 동안 살짝 작아져요
            .clip(CircleShape)
            .background(if (pressed) c.primary else c.glass)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    pressed = true
                    press()
                    try {
                        tryAwaitRelease()
                    } finally {
                        pressed = false
                        release()
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MajiTheme.type.button, color = if (pressed) c.onPrimary else c.text)
    }
}

/** 카드 하나: 제목·지금 차 상태, 가운데 값, [−] [+] */
@Composable
internal fun SettingCard(
    title: String,
    now: String,
    value: String,
    canDown: Boolean,
    canUp: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {}
) {
    val c = MajiTheme.colors
    MajiCard(modifier) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SubText("$title · $now")
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton("−", canDown, onDown)
                Text(
                    text = value,
                    style = MajiTheme.type.title.copy(fontFeatureSettings = "tnum"),
                    color = c.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).widthIn(min = 120.dp)
                )
                StepButton("+", canUp, onUp)
            }
            extra()
        }
    }
}

@Composable
internal fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val c = MajiTheme.colors
    Box(
        Modifier
            .size(MinTouch)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(c.glass),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, style = MajiTheme.type.headline, color = c.text)
    }
}

/**
 * 열선·통풍 카드 (#111). 한 줄 −/+ 로 음수까지 내려가면 통풍이 되는 걸 알 수가 없어서
 * [끔] [열선] [통풍]을 먼저 고르고, 세기(1~3단)는 따로 −/+ 로 맞춰요.
 */
@Composable
internal fun HeatCard(level: Int, now: String?, enabled: Boolean, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MajiTheme.colors
    val mode = HeatMode.of(level)
    val strength = abs(level)
    MajiCard(modifier) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SubText(if (now != null) "열선·통풍 · $now" else "열선·통풍")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeatMode.entries.forEach { m ->
                    Chip(m.label, selected = mode == m, enabled = enabled, modifier = Modifier.weight(1f)) {
                        onChange(HeatMode.levelOf(m, strength))
                    }
                }
            }
            HeatStrength(level, enabled, onChange)
        }
    }
}

/** 세기 [−] 2단 [+]. 끔이면 숫자 대신 "끔"이고 버튼은 쉬어요 */
@Composable
internal fun HeatStrength(level: Int, enabled: Boolean, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MajiTheme.colors
    val mode = HeatMode.of(level)
    val strength = abs(level)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", enabled && mode != HeatMode.OFF && strength > 1) { onChange(HeatMode.levelOf(mode, strength - 1)) }
        Text(
            text = if (mode == HeatMode.OFF) "끔" else "${strength}단",
            style = MajiTheme.type.title.copy(fontFeatureSettings = "tnum"),
            color = c.text.copy(alpha = if (mode == HeatMode.OFF) 0.5f else 1f),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).widthIn(min = 96.dp)
        )
        StepButton("+", enabled && mode != HeatMode.OFF && strength < ProfileRules.HEAT_MAX) { onChange(HeatMode.levelOf(mode, strength + 1)) }
    }
}

/** 고른 것은 꽉 찬 캡슐, 안 고른 것은 유리 캡슐 */
@Composable
internal fun Chip(text: String, selected: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = MajiTheme.colors
    Box(
        modifier
            .heightIn(min = MinTouch)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(if (selected) c.primary else c.glass)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MajiTheme.type.button, color = if (selected) c.onPrimary else c.text)
    }
}

internal fun tempLabel(c: Float): String = String.format(Locale.US, "%.1f℃", c)


// ───────────── 미리보기 ─────────────

private val seatPreviewProfiles = FakeProfileRepository.samples()
    .mapIndexed { i, p -> p.copy(id = i + 1L) }   // 1 엄마, 2 딸(여름 없음), 3 여자친구(둘 다 없음)

@Preview(name = "처음 맞이 · 넓은 창", widthDp = 1682, heightDp = 1120)
@Composable
private fun FirstRidePreview() = MajiTheme(ThemeMode.LIGHT) {
    SeatSetupScreen(seatPreviewProfiles[2], Season.WINTER, 0, 21.5f, false, {}, {}, {}, { 0 }, { _, _, _ -> }, {})
}

@Preview(name = "계절 바뀜 · 반쪽 창 · 밤", widthDp = 841, heightDp = 1120)
@Composable
private fun SeasonChangedPreview() = MajiTheme(ThemeMode.DARK) {
    SeatSetupScreen(seatPreviewProfiles[1], Season.SUMMER, -1, null, false, {}, {}, {}, { 0 }, { _, _, _ -> }, {})
}

@Preview(name = "주행 중 · 반쪽 창", widthDp = 841, heightDp = 1120)
@Composable
private fun SeatDrivingPreview() = MajiTheme(ThemeMode.LIGHT) {
    SeatSetupScreen(seatPreviewProfiles[2], Season.WINTER, 0, 22f, true, {}, {}, {}, { 0 }, { _, _, _ -> }, {})
}
