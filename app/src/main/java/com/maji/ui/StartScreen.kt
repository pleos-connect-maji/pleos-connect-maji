package com.maji.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.style.TextOverflow
import com.maji.R
import com.maji.contract.MajiFlow
import com.maji.voice.GreetingRules
import com.maji.contract.Profile
import com.maji.contract.ProfileRepository
import com.maji.profile.FakeProfileRepository
import com.maji.profile.ProfileRules
import com.maji.ui.theme.MajiBackground
import com.maji.ui.theme.MajiGlassButton
import com.maji.ui.theme.MajiPrimaryButton
import com.maji.ui.theme.MajiTheme
import com.maji.ui.theme.MoodLightColors
import com.maji.ui.theme.SoftAvatar
import com.maji.ui.theme.SquircleShape
import com.maji.ui.theme.ThemeMode
import java.time.LocalDate

// 맞이 시작 화면 = 홈 (#23, 리디자인 #109).
// 사람마다 카드 한 장 (캐러셀). 카드를 누르면 바로 맞이를 시작하고, ✎로 수정, 마지막 카드로 사람을 추가해요.
// 넓은 창에서는 카드가 다 보이고, 반쪽 창에서는 옆으로 넘겨요. 가운데 카드의 무드등 색이 화면 빛이 돼요.
// 주행 중에는 시작·수정을 누를 수 없어요.

/** 프로필을 DB에서 읽어 와서 시작 화면에 넘겨요. flow는 누른 프로필로 맞이를 시작해요. */
@Composable
fun StartRoute(
    profiles: ProfileRepository,
    flow: MajiFlow,
    drivingRestricted: Boolean,
    onEdit: (Profile) -> Unit = {},
    modifier: Modifier = Modifier,
    onAdd: () -> Unit = {}
) {
    // 불러오기 전(null)과 "사람이 없음"(빈 목록)을 구분해요.
    // 홈으로 돌아올 때마다 DB를 읽는 짧은 순간에 다른 화면이 끼어 보이지 않도록,
    // 마지막으로 읽은 목록을 먼저 보여 주고 새로 읽은 값으로 조용히 바꿔요
    val list by produceState(initialValue = lastProfiles, profiles) {
        value = profiles.getAll().also { lastProfiles = it }
    }
    val loaded = list
    if (loaded == null) {
        MajiBackground(modifier = modifier) {}  // 불러오는 동안은 바탕만
        return
    }
    StartScreen(
        profiles = loaded,
        drivingRestricted = drivingRestricted,
        onStart = { flow.start(it) },
        onEdit = onEdit,
        onAdd = onAdd,
        modifier = modifier
    )
}

/** 화면만 그려요. 값과 이벤트를 밖에서 받아서 미리보기와 테스트에도 쓸 수 있어요. */
@Composable
fun StartScreen(
    profiles: List<Profile>,
    drivingRestricted: Boolean,
    onStart: (Profile) -> Unit,
    onEdit: (Profile) -> Unit = {},
    modifier: Modifier = Modifier,
    onAdd: () -> Unit = {},
    month: Int = LocalDate.now().monthValue
) {
    // 사람이 없어도 따로 빈 화면을 두지 않고, 같은 캐러셀에 "맞이할 사람 추가" 카드 한 장만 보여요 (#118)
    val empty = profiles.isEmpty()
    val listState = rememberLazyListState()
    // 화면 가운데에 가장 가까운 카드 = 지금 보고 있는 사람. 그 사람 무드등 색이 화면 빛이 돼요
    val centered by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .filter { it.index < profiles.size }
                .minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - mid) }?.index ?: 0
        }
    }
    val accent = if (empty) null else MoodLightColors.of(profiles[centered.coerceIn(0, profiles.lastIndex)].lightColor)
    val season = ProfileRules.seasonFor(month)
    val presets = stringArrayResource(R.array.greeting_presets).toList()

    MajiBackground(modifier = modifier, accent = accent) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth.value >= WIDE_MIN_DP
            val side = if (wide) 72.dp else 44.dp
            val cardWidth: Dp = if (wide) 360.dp else (maxWidth * 0.62f)
            // 제목과 카드를 한 덩어리로 묶어 화면 가운데보다 살짝 위(시각적 중심)에 둬요.
            // 정확히 가운데면 눈에는 처져 보여서 위:아래 여백을 4:6으로 나눠요
            Column(Modifier.fillMaxSize().padding(vertical = 56.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
                Spacer(Modifier.weight(0.4f))
                Column(Modifier.padding(horizontal = side), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Headline(if (empty) "맞이할 사람을 추가해 보세요" else "누구를 맞이하러 갈까요?")
                    SubText(
                        when {
                            drivingRestricted -> "주행 중에는 바꿀 수 없어요. 차를 세운 뒤 눌러 주세요."
                            empty -> "한 번 맞춰 두면 맞이할 때마다 자리와 불빛을 준비해요"
                            else -> "카드를 누르면 바로 출발해요"
                        }
                    )
                }
                LazyRow(
                    state = listState,
                    flingBehavior = rememberSnapFlingBehavior(listState),
                    contentPadding = PaddingValues(horizontal = side),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(profiles, key = { it.id }) { p ->
                        PersonCard(
                            profile = p,
                            detail = ProfileRules.climateFor(p, season)?.let { c ->
                                val parts = listOfNotNull(
                                    seasonLabel(season),
                                    c.seatHeat?.let { heatRow(it).let { (k, v) -> if (v == "끔") "$k 끔" else "$k $v" } },
                                    c.temperature?.let { tempText(it) }
                                )
                                parts.joinToString(" · ")
                            } ?: "처음 맞이할 때 자리를 맞춰요",
                            greeting = GreetingRules.textFor(p, presets),
                            enabled = !drivingRestricted,
                            onStart = { onStart(p) },
                            onEdit = { onEdit(p) },
                            width = cardWidth
                        )
                    }
                    // 사람 수 제한은 없어요. 마지막 카드는 항상 "맞이할 사람 추가"
                    item { AddPersonCard(enabled = !drivingRestricted, onAdd = onAdd, width = cardWidth) }
                }
                // 카드가 화면을 넘칠 때만(넓은 창도 4명 이상이면) 점 표시로 몇 번째인지 알려 줘요
                if (listState.canScrollForward || listState.canScrollBackward) {
                    Dots(count = profiles.size + 1, current = centered, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                Spacer(Modifier.weight(0.6f))
            }
        }
    }
}

@Composable
private fun PersonCard(profile: Profile, detail: String, greeting: String?, enabled: Boolean, onStart: () -> Unit, onEdit: () -> Unit, width: Dp) {
    val c = MajiTheme.colors
    val color = MoodLightColors.of(profile.lightColor)
    val shape = SquircleShape(44.dp)
    Box(
        Modifier
            .width(width)
            .height(CardHeight)
            .clickable(enabled = enabled, onClick = onStart)  // 카드 전체가 살짝 눌려요
            .clip(shape)
            .background(c.surface)
            .drawBehind {
                // 카드 위쪽에 그 사람 무드등 빛
                drawRect(Brush.radialGradient(listOf(color.copy(alpha = if (c.isDark) 0.45f else 0.55f), Color.Transparent), center = Offset(size.width * 0.5f, -size.height * 0.1f), radius = size.width * 0.95f))
            }
            .padding(32.dp)
    ) {
        // 위: 사람 원 + 수정 / 가운데: 이름·자리·들려줄 인사 / 아래: 출발 버튼
        SoftAvatar(initialOf(profile.name), color, size = 104.dp)
        RoundGlassButton("✎", onClick = onEdit, enabled = enabled, modifier = Modifier.align(Alignment.TopEnd))
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(profile.name, style = MajiTheme.type.headline, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            SubText(detail)
            if (greeting != null) {
                Spacer(Modifier.height(12.dp))
                Text("“$greeting”", style = MajiTheme.type.body, color = c.text.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        MajiGlassButton("맞이하러 가기", onClick = onStart, enabled = enabled, modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth())
    }
}

@Composable
private fun AddPersonCard(enabled: Boolean, onAdd: () -> Unit, width: Dp) {
    val c = MajiTheme.colors
    val shape = SquircleShape(44.dp)
    Box(
        Modifier
            .width(width)
            .height(CardHeight)
            .clickable(enabled = enabled, onClick = onAdd)
            .clip(shape)
            .border(BorderStroke(3.dp, c.textSub.copy(alpha = 0.3f)), shape)
            .padding(32.dp)
    ) {
        Box(Modifier.size(104.dp).clip(CircleShape).background(c.glass), contentAlignment = Alignment.Center) {
            Text("+", style = MajiTheme.type.headline, color = c.text)
        }
        Column(Modifier.align(Alignment.CenterStart).padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("맞이할 사람 추가", style = MajiTheme.type.title, color = c.text)
            SubText("자주 맞이하는 사람을\n넣어 보세요")
        }
    }
}

@Composable
private fun Dots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val c = MajiTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) { i ->
            Box(Modifier.height(10.dp).width(if (i == current) 30.dp else 10.dp).clip(CircleShape).background(if (i == current) c.text else c.textSub.copy(alpha = 0.3f)))
        }
    }
}

/** 마지막으로 읽은 사람 목록 (앱이 켜져 있는 동안만). 홈을 다시 열 때 바로 보여 줘요 */
private var lastProfiles: List<Profile>? = null

/** 카드 높이: 내용(원·이름·자리·인사·버튼)에 맞춘 고정 높이. 화면 높이를 꽉 채우면 가운데가 비어 보여요 */
private val CardHeight = 560.dp

private fun seasonLabel(s: com.maji.contract.Season) = if (s == com.maji.contract.Season.WINTER) "겨울" else "여름"

// ───────────── 미리보기 (넓은 창 1682×1120, 반쪽 창 841×1120) ─────────────

private val homePreviewProfiles = FakeProfileRepository.samples().mapIndexed { i, p ->
    p.copy(id = i + 1L, lightColor = listOf(0xFFF59AC2.toInt(), 0xFF6FB2FF.toInt(), 0xFFF2D66B.toInt())[i % 3])
}

@Preview(name = "홈 · 넓은 창 · 낮", widthDp = 1682, heightDp = 1120)
@Composable
private fun HomeWidePreview() = MajiTheme(ThemeMode.LIGHT) { StartScreen(homePreviewProfiles, false, {}) }

@Preview(name = "홈 · 반쪽 창 · 밤", widthDp = 841, heightDp = 1120)
@Composable
private fun HomeHalfPreview() = MajiTheme(ThemeMode.DARK) { StartScreen(homePreviewProfiles, false, {}) }

@Preview(name = "홈 · 첫 실행", widthDp = 841, heightDp = 1120)
@Composable
private fun HomeEmptyPreview() = MajiTheme(ThemeMode.LIGHT) { StartScreen(emptyList(), false, {}) }
