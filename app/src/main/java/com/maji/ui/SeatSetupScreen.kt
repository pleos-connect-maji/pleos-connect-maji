package com.maji.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maji.contract.Climate
import com.maji.contract.Profile
import com.maji.contract.ProfileRepository
import com.maji.contract.Season
import com.maji.profile.FakeProfileRepository
import com.maji.profile.ProfileRules
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

// D. 자리 설정 화면 (#46). 탑승자가 탄 뒤 정차 중(P단)에 열선·온도를 맞추고 그 계절에 저장해요.
// 띄우는 때 (C가 정해요): climateFor(profile, 오늘 계절) == null 일 때
//   - 두 계절 다 비어 있으면 "처음 타셨네요?"
//   - 이번 계절만 비어 있으면 "계절이 바뀌었어요"
//
// 차와 연결되는 값 (C가 넘겨 줘요):
//   currentHeat / currentTemp  = 지금 차 상태 (A의 onPassengerHeatChanged / onPassengerTemperatureChanged)
//                                 탑승자가 차 버튼으로 바꿔도 화면이 따라 바뀌어요
//   onHeatChange / onTempChange = 화면 버튼을 누르면 차에 바로 반영 (B 함수 정해지면 C가 연결)
// 시트 ◀ ▶는 B 함수가 정해지면 추가해요.

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
    onHeatChange: (Int) -> Unit = {},
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
        onHeatChange = onHeatChange,
        onTempChange = onTempChange,
        onSave = { season, climate ->
            scope.launch {
                val latest = repo.get(profile.id) ?: profile  // 그사이 바뀐 이름·인사를 덮어쓰지 않게
                repo.save(ProfileRules.withClimate(latest, season, climate))
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
    onSave: (Season, Climate) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var season by remember { mutableStateOf(initialSeason) }
    val saved = ProfileRules.climateFor(profile, season)

    // 시작 값: 그 계절에 저장된 값 → 없으면 지금 차 상태 → 없으면 기본값
    var heat by remember { mutableIntStateOf(saved?.seatHeat ?: currentHeat ?: 0) }
    var temp by remember { mutableFloatStateOf(saved?.temperature ?: currentTemp ?: DEFAULT_TEMP) }
    var keepTemp by remember { mutableStateOf(saved != null && saved.temperature == null) }

    // 탑승자가 차 버튼으로 바꾸면 화면도 따라가요 (A 신호)
    LaunchedEffect(currentHeat) { currentHeat?.let { heat = it } }
    // 그대로 두기 중에도 지금 차 온도는 기억해 둬요 → 그 상태에서 + 누르면 지금 온도 + 0.5부터
    LaunchedEffect(currentTemp) { currentTemp?.let { temp = it } }

    // 계절을 바꾸면 그 계절에 저장된 값이 있을 때만 불러와요
    fun selectSeason(next: Season) {
        season = next
        ProfileRules.climateFor(profile, next)?.let { c ->
            c.seatHeat?.let { heat = it }
            val t = c.temperature
            if (t == null) {
                keepTemp = true
            } else {
                keepTemp = false
                temp = t
            }
        }
    }

    val title = when {
        profile.winter == null && profile.summer == null -> "${profile.name}, 처음 타셨네요? 편하게 맞춰 보세요"
        ProfileRules.climateFor(profile, initialSeason) == null -> "계절이 바뀌었어요. ${seasonName(initialSeason)} 자리를 맞춰 볼까요?"
        else -> "${profile.name} 자리 다시 맞추기"
    }
    val locked = drivingRestricted

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 맨 위: 제목 + 저장·다음에
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Button(
                onClick = { onSave(season, Climate(seatHeat = heat, temperature = if (keepTemp) null else temp)) },
                enabled = !locked,
                modifier = Modifier.heightIn(min = 56.dp)
            ) { Text("저장") }
            OutlinedButton(onClick = onSkip, modifier = Modifier.heightIn(min = 56.dp)) { Text("다음에") }
        }

        if (locked) {
            Text(text = "주행 중에는 바꿀 수 없어요. 정차 후에 해 주세요.", style = MaterialTheme.typography.bodyLarge)
        }

        // ── 계절
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "계절", style = MaterialTheme.typography.titleMedium)
            ChoiceButton("겨울", selected = season == Season.WINTER, enabled = !locked) { selectSeason(Season.WINTER) }
            ChoiceButton("여름", selected = season == Season.SUMMER, enabled = !locked) { selectSeason(Season.SUMMER) }
            Text(text = "지금 고른 계절에만 저장돼요", style = MaterialTheme.typography.bodyMedium)
        }

        // ── 열선 | 온도
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingCard(
                title = "시트 열선·통풍",
                now = "지금 차: " + (currentHeat?.let { heatLabel(it) } ?: "모름"),
                value = heatLabel(heat),
                canDown = !locked && heat > ProfileRules.HEAT_MIN,
                canUp = !locked && heat < ProfileRules.HEAT_MAX,
                onDown = { heat -= 1; onHeatChange(heat) },
                onUp = { heat += 1; onHeatChange(heat) },
                modifier = Modifier.weight(1f)
            )

            SettingCard(
                title = "조수석 온도",
                now = "지금 차: " + (currentTemp?.let { tempLabel(it) } ?: "모름"),
                value = if (keepTemp) "그대로 두기" else tempLabel(temp),
                // 그대로 두기 상태에서도 − + 를 누르면 바로 온도 조절로 바뀌어요
                canDown = !locked && temp > ProfileRules.TEMP_MIN,
                canUp = !locked && temp < ProfileRules.TEMP_MAX,
                onDown = { keepTemp = false; temp -= TEMP_STEP; onTempChange(temp) },
                onUp = { keepTemp = false; temp += TEMP_STEP; onTempChange(temp) },
                modifier = Modifier.weight(1f)
            ) {
                ChoiceButton("그대로 두기", selected = keepTemp, enabled = !locked) {
                    keepTemp = !keepTemp
                    onTempChange(if (keepTemp) null else temp)
                }
            }
        }
    }
}

/** 카드 하나: 제목, 지금 차 상태, [-] 값 [+] */
@Composable
private fun SettingCard(
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
    OutlinedCard(shape = RoundedCornerShape(24.dp), modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(text = now, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onDown, enabled = canDown, modifier = Modifier.heightIn(min = 64.dp)) {
                    Text("−", style = MaterialTheme.typography.headlineMedium)
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 140.dp)
                )
                OutlinedButton(onClick = onUp, enabled = canUp, modifier = Modifier.heightIn(min = 64.dp)) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
            extra()
        }
    }
}

/** 고른 것은 꽉 찬 버튼, 안 고른 것은 테두리 버튼 */
@Composable
private fun ChoiceButton(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) { Text(text) }
    }
}

private fun heatLabel(level: Int): String = when {
    level < 0 -> "통풍 ${-level}단"
    level == 0 -> "끔"
    else -> "열선 ${level}단"
}

private fun tempLabel(c: Float): String = String.format(Locale.US, "%.1f℃", c)

private fun seasonName(s: Season): String = if (s == Season.WINTER) "겨울" else "여름"

// ───────────── 미리보기 ─────────────

private val seatPreviewProfiles = FakeProfileRepository.samples()
    .mapIndexed { i, p -> p.copy(id = i + 1L) }   // 1 엄마, 2 딸(여름 없음), 3 여자친구(둘 다 없음)

@Preview(name = "처음 탐 - 여자친구", showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun FirstRidePreview() {
    MaterialTheme {
        Surface {
            SeatSetupScreen(seatPreviewProfiles[2], Season.WINTER, 0, 21.5f, false, {}, {}, { _, _ -> }, {})
        }
    }
}

@Preview(name = "계절 바뀜 - 딸 여름", showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun SeasonChangedPreview() {
    MaterialTheme {
        Surface {
            SeatSetupScreen(seatPreviewProfiles[1], Season.SUMMER, -1, null, false, {}, {}, { _, _ -> }, {})
        }
    }
}

@Preview(name = "다시 맞추기 - 엄마", showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun AdjustPreview() {
    MaterialTheme {
        Surface {
            SeatSetupScreen(seatPreviewProfiles[0], Season.WINTER, 3, 24f, false, {}, {}, { _, _ -> }, {})
        }
    }
}

@Preview(name = "주행 중", showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun SeatDrivingPreview() {
    MaterialTheme {
        Surface {
            SeatSetupScreen(seatPreviewProfiles[2], Season.WINTER, 0, 22f, true, {}, {}, { _, _ -> }, {})
        }
    }
}