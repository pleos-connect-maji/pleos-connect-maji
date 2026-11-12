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
import com.maji.contract.MajiFlow
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
    val list by produceState(initialValue = emptyList<Profile>(), profiles) {
        value = profiles.getAll()
    }
    StartScreen(
        profiles = list,
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
    if (profiles.isEmpty()) {
        EmptyHome(drivingRestricted, onAdd, modifier)
        return
    }
    val listState = rememberLazyListState()
    val centered by remember { derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, profiles.lastIndex) } }
    val accent = MoodLightColors.of(profiles[centered].lightColor)
    val season = ProfileRules.seasonFor(month)
    val canAdd = profiles.size < MAX_PROFILES

    MajiBackground(modifier = modifier, accent = accent) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth.value >= WIDE_MIN_DP
            val side = if (wide) 72.dp else 44.dp
            val cardWidth: Dp = if (wide) 360.dp else (maxWidth * 0.62f)
            Column(Modifier.fillMaxSize().padding(vertical = 56.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.padding(horizontal = side).padding(end = 280.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Headline("누구를 맞이하러 갈까요?")
                    SubText(if (drivingRestricted) "주행 중에는 시작할 수 없어요. 차를 세운 뒤 눌러 주세요." else "카드를 누르면 바로 출발해요")
                }
                LazyRow(
                    state = listState,
                    flingBehavior = rememberSnapFlingBehavior(listState),
                    contentPadding = PaddingValues(horizontal = side),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
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
                            } ?: "처음 맞이할 때\n자리를 맞춰요",
                            enabled = !drivingRestricted,
                            onStart = { onStart(p) },
                            onEdit = { onEdit(p) },
                            width = cardWidth
                        )
                    }
                    if (canAdd) item { AddPersonCard(enabled = !drivingRestricted, onAdd = onAdd, width = cardWidth) }
                }
                if (!wide) Dots(count = profiles.size + if (canAdd) 1 else 0, current = listState.firstVisibleItemIndex, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun PersonCard(profile: Profile, detail: String, enabled: Boolean, onStart: () -> Unit, onEdit: () -> Unit, width: Dp) {
    val c = MajiTheme.colors
    val color = MoodLightColors.of(profile.lightColor)
    val shape = SquircleShape(44.dp)
    Box(
        Modifier
            .width(width)
            .fillMaxSize()
            .clickable(enabled = enabled, onClick = onStart)  // 카드 전체가 살짝 눌려요
            .clip(shape)
            .background(c.surface)
            .drawBehind {
                // 카드 위쪽에 그 사람 무드등 빛
                drawRect(Brush.radialGradient(listOf(color.copy(alpha = if (c.isDark) 0.45f else 0.55f), Color.Transparent), center = Offset(size.width * 0.5f, -size.height * 0.1f), radius = size.width * 0.95f))
            }
            .padding(32.dp)
    ) {
        SoftAvatar(initialOf(profile.name), color, size = 104.dp)
        RoundGlassButton("✎", onClick = onEdit, enabled = enabled, modifier = Modifier.align(Alignment.TopEnd))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(profile.name, style = MajiTheme.type.headline, color = c.text)
            SubText(detail)
            Spacer(Modifier.height(8.dp))
            MajiGlassButton("맞이하러 가기", onClick = onStart, enabled = enabled, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AddPersonCard(enabled: Boolean, onAdd: () -> Unit, width: Dp) {
    val c = MajiTheme.colors
    val shape = SquircleShape(44.dp)
    Box(
        Modifier
            .width(width)
            .fillMaxSize()
            .clickable(enabled = enabled, onClick = onAdd)
            .clip(shape)
            .border(BorderStroke(3.dp, c.textSub.copy(alpha = 0.3f)), shape)
            .padding(32.dp)
    ) {
        Box(Modifier.size(104.dp).clip(CircleShape).background(c.glass), contentAlignment = Alignment.Center) {
            Text("+", style = MajiTheme.type.headline, color = c.text)
        }
        Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("맞이할 사람 추가", style = MajiTheme.type.title, color = c.text)
            SubText("자주 맞이하는 사람을\n넣어 보세요")
        }
    }
}

@Composable
private fun EmptyHome(drivingRestricted: Boolean, onAdd: () -> Unit, modifier: Modifier) {
    MajiScaffold(
        accent = null,
        modifier = modifier,
        top = {
            Spacer(Modifier.height(80.dp))
            Box(Modifier.size(104.dp).clip(CircleShape).background(MajiTheme.colors.glass), contentAlignment = Alignment.Center) {
                Text("+", style = MajiTheme.type.headline, color = MajiTheme.colors.text)
            }
            Headline("맞이할 사람을\n추가해 보세요")
            SubText("한 번 맞춰 두면 맞이할 때마다\n자리와 불빛을 준비해요")
        },
        actions = { MajiPrimaryButton("맞이할 사람 추가", onClick = onAdd, enabled = !drivingRestricted, modifier = Modifier.weight(1f)) }
    )
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
