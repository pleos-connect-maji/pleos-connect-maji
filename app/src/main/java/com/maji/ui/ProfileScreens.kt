package com.maji.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maji.R
import com.maji.contract.Profile
import com.maji.contract.ProfileRepository
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

// D. 맞이할 사람 추가·수정 화면 (#39, 리디자인 #109). 운전자가 정차 중에 사람을 만들고 고쳐요.
// 자리(열선·온도·시트)는 여기서 안 정해요 → 그 사람이 탄 뒤 자리 설정 화면(#46)에서.
// 사람 목록 화면은 없어졌어요: 홈 카드의 ✎로 수정하고, 마지막 카드로 추가해요.
//
//   ...Route  = DB에서 읽고 저장하는 부분 (MainActivity가 불러요)
//   ...Screen = 화면만 그리는 부분 (미리보기에도 써요)


private const val BRIGHTNESS_STEP = 10

/** profileId가 null이면 새로 맞이할 사람, 있으면 그 사람 수정. 저장·삭제·닫기하면 onDone */
@Composable
fun ProfileEditRoute(
    repo: ProfileRepository,
    profileId: Long?,
    drivingRestricted: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(profileId == null) }
    var initial by remember { mutableStateOf<Profile?>(null) }

    LaunchedEffect(profileId) {
        if (profileId != null) {
            initial = repo.get(profileId)
            ready = true
        }
    }

    if (!ready) {
        MajiBackground(modifier) { SubText("불러오는 중…", Modifier.padding(48.dp)) }
    } else ProfileEditScreen(
        initial = initial,
        presets = stringArrayResource(R.array.greeting_presets).toList(),
        drivingRestricted = drivingRestricted,
        onSave = { profile ->
            scope.launch {
                repo.save(profile)
                onDone()
            }
        },
        onDelete = {
            scope.launch {
                initial?.let { repo.delete(it.id) }
                onDone()
            }
        },
        onCancel = onDone,
        modifier = modifier
    )
}

@Composable
fun ProfileEditScreen(
    initial: Profile?,
    presets: List<String>,
    drivingRestricted: Boolean,
    onSave: (Profile) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val customIndex = presets.size  // 마지막 선택지 = "직접 입력"

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var choice by remember {
        val saved = initial?.greetingText
        mutableIntStateOf(
            when {
                saved == null -> 0                        // 안 정했으면 기본 목록 첫 번째
                presets.contains(saved) -> presets.indexOf(saved)
                else -> customIndex                       // 목록에 없으면 직접 입력한 것
            }
        )
    }
    var custom by remember {
        mutableStateOf(initial?.greetingText?.takeIf { !presets.contains(it) } ?: "")
    }
    var special by remember { mutableStateOf(initial?.specialGreetingText ?: "") }
    // 함께 출발할 때 켜는 무드등: 지금 저장된 값에서 시작해요 (안 고쳤으면 그대로 둬요)
    var lightColor by remember { mutableStateOf(initial?.lightColor) }
    var brightness by remember { mutableIntStateOf(initial?.lightBrightness ?: 30) }
    var closeWindow by remember { mutableStateOf(initial?.closeWindow ?: true) }
    var askDelete by remember { mutableStateOf(false) }

    val greeting: String? = if (choice < customIndex) presets[choice] else custom.trim().ifBlank { null }
    val canSave = name.isNotBlank() && !drivingRestricted && (choice < customIndex || custom.isNotBlank())
    val locked = drivingRestricted
    val accent = MoodLightColors.of(lightColor)

    val save = {
        val base = initial ?: Profile(name = "")
        onSave(
            base.copy(
                name = name.trim(),
                greetingText = greeting,
                specialGreetingText = special.trim().ifBlank { null },
                lightColor = lightColor,
                lightBrightness = brightness,
                closeWindow = closeWindow
            )
        )
    }

    // ── 칸들
    val nameField: @Composable ColumnScope.() -> Unit = {
        Label("이름")
        InputField(name, { if (it.length <= 10) name = it }, "예: 엄마", enabled = !locked)
    }
    val greetingField: @Composable ColumnScope.() -> Unit = {
        Label("맞이할 때 들려줄 인사")
        MajiCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                presets.forEachIndexed { i, text ->
                    GreetingOption(text = text, selected = choice == i, enabled = !locked, onClick = { choice = i })
                }
                GreetingOption(text = "직접 입력", selected = choice == customIndex, enabled = !locked, onClick = { choice = customIndex })
                if (choice == customIndex) {
                    Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                        InputField(custom, { if (it.length <= 40) custom = it }, "인사 문장", enabled = !locked)
                    }
                }
            }
        }
    }
    val specialField: @Composable ColumnScope.() -> Unit = {
        Label("오늘만의 인사 (선택) · 다음 맞이 한 번만 나와요")
        InputField(special, { if (it.length <= 40) special = it }, "예: 생일 축하해!", enabled = !locked)
    }
    val lightField: @Composable ColumnScope.() -> Unit = {
        Label("함께 출발할 때 켜는 무드등")
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            MoodLightColors.all.forEach { (label, color) ->
                ColorChoice(
                    label = label,
                    color = color,
                    selected = MoodLightColors.of(lightColor) == color,
                    enabled = !locked,
                    onClick = { lightColor = color.toArgb() }
                )
            }
        }
        MajiCard(Modifier.fillMaxWidth()) {
            SettingLine("무드등 밝기") {
                SmallStep("−", !locked && brightness > ProfileRules.BRIGHTNESS_MIN) { brightness = (brightness - BRIGHTNESS_STEP).coerceAtLeast(ProfileRules.BRIGHTNESS_MIN) }
                Text("$brightness%", style = MajiTheme.type.number, color = MajiTheme.colors.text, modifier = Modifier.width(120.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                SmallStep("+", !locked && brightness < ProfileRules.BRIGHTNESS_MAX) { brightness = (brightness + BRIGHTNESS_STEP).coerceAtMost(ProfileRules.BRIGHTNESS_MAX) }
            }
            SettingLine("함께 출발하면 창문 닫기") {
                Toggle(closeWindow, enabled = !locked) { closeWindow = it }
            }
        }
    }

    MajiBackground(modifier = modifier, accent = accent) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth.value >= WIDE_MIN_DP
            val title = if (initial == null) "새로 맞이할 사람" else "${initial.name} 맞이 설정"
            Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 72.dp else 44.dp, vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Headline(title)
                        SubText(if (locked) "주행 중에는 바꿀 수 없어요. 차를 세운 뒤 해 주세요." else "열선·온도·시트는 처음 맞이할 때 차에서 맞춰요")
                    }
                    if (wide) {
                        MajiGlassButton("닫기", onClick = onCancel, modifier = Modifier.width(200.dp))
                        Spacer(Modifier.width(16.dp))
                        MajiPrimaryButton("저장", onClick = save, enabled = canSave, modifier = Modifier.width(260.dp))
                    }
                }
                if (wide) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            nameField(); Spacer(Modifier.height(8.dp)); lightField(); Spacer(Modifier.height(8.dp)); specialField()
                            if (initial != null) DeleteLink(enabled = !locked) { askDelete = true }
                        }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            greetingField()
                        }
                    }
                } else {
                    // 반쪽 창: 위에서 아래로 쌓고 스크롤해요. 인사 목록과 직접 입력은 모두 보여요
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        nameField(); Spacer(Modifier.height(8.dp)); greetingField(); Spacer(Modifier.height(8.dp)); lightField(); Spacer(Modifier.height(8.dp)); specialField()
                        if (initial != null) DeleteLink(enabled = !locked) { askDelete = true }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MajiGlassButton("닫기", onClick = onCancel, modifier = Modifier.weight(1f))
                        MajiPrimaryButton("저장", onClick = save, enabled = canSave, modifier = Modifier.weight(1.5f))
                    }
                }
            }
        }
        if (askDelete && initial != null) {
            ConfirmSheet(
                title = "${josa(initial.name, "을", "를")} 지울까요?",
                body = "자리 설정과 인사가 모두 지워져요",
                confirm = "지우기",
                onDismiss = { askDelete = false },
                onConfirm = { askDelete = false; onDelete() }
            )
        }
    }
}

@Composable
private fun Label(text: String) = SubText(text)

/** 한 줄 입력칸 (단색 카드 위) */
@Composable
private fun InputField(value: String, onChange: (String) -> Unit, placeholder: String, enabled: Boolean) {
    val c = MajiTheme.colors
    MajiCard(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().heightIn(min = MinTouch).padding(horizontal = 32.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = MajiTheme.type.body, color = c.textSub)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                enabled = enabled,
                singleLine = true,
                textStyle = MajiTheme.type.body.copy(color = c.text),
                cursorBrush = SolidColor(c.text),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 인사 선택지 한 줄 (줄 전체를 눌러도 선택돼요) */
@Composable
private fun GreetingOption(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = MajiTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .border(if (selected) 9.dp else 3.dp, if (selected) c.text else c.textSub.copy(alpha = 0.5f), CircleShape)
        )
        Spacer(Modifier.width(20.dp))
        Text(text = text, style = MajiTheme.type.body, color = c.text)
    }
}

/** 무드등 색 하나: 원 + 이름. 고른 색은 테두리 고리 */
@Composable
internal fun ColorChoice(label: String, color: Color, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = MajiTheme.colors
    // 누를 때: 회색 네모 물결 대신 원이 살짝 눌렸다가 돌아와요 (손가락 아래에서 바로 반응)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    // 고르면: 검은 테두리 대신 같은 색 고리 + 은은한 빛, 안쪽 원은 살짝 작아져서 고리와 틈이 생겨요
    val ring by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "ring")
    val ringColor = if (c.isDark) lerp(color, Color.White, 0.15f) else lerp(color, Color.Black, 0.12f)
    val light = lerp(color, Color.White, 0.4f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .selectable(
                selected = selected, enabled = enabled, role = Role.RadioButton,
                interactionSource = interaction, indication = null, onClick = onClick
            )
    ) {
        Box(
            Modifier
                .size(MinTouch)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .drawBehind {
                    val full = size.minDimension / 2f
                    if (ring > 0f) {
                        // 고른 색 주변에 번지는 빛 → 고리 → 틈 → 색 원
                        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.45f * ring), Color.Transparent), radius = full * 1.25f), radius = full * 1.25f)
                        drawCircle(ringColor.copy(alpha = ring), radius = full - 1.5.dp.toPx(), style = Stroke(width = 3.dp.toPx()))
                    }
                    val inner = full * (0.82f - 0.12f * ring)
                    drawCircle(
                        Brush.radialGradient(listOf(light, color), center = Offset(center.x - inner * 0.3f, center.y - inner * 0.35f), radius = inner * 1.4f),
                        radius = inner
                    )
                }
        )
        Text(
            label,
            style = MajiTheme.type.caption.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) c.text else c.textSub
        )
    }
}

@Composable
private fun SettingLine(label: String, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MajiTheme.type.body, color = MajiTheme.colors.text, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) { control() }
    }
}

@Composable
private fun SmallStep(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val c = MajiTheme.colors
    Box(
        Modifier.size(MinTouch).alpha(if (enabled) 1f else 0.35f).clickable(enabled = enabled, onClick = onClick).clip(CircleShape).background(c.glass),
        contentAlignment = Alignment.Center
    ) { Text(symbol, style = MajiTheme.type.title, color = c.text) }
}

/** 켜고 끄는 스위치. 켜짐 색은 Pleos switch_on (informative_active) */
@Composable
private fun Toggle(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val c = MajiTheme.colors
    Box(
        Modifier
            .size(width = 112.dp, height = MinTouch)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(width = 96.dp, height = 56.dp).clip(CircleShape)
                .background(if (checked) Color(0xFF02C265) else c.textSub.copy(alpha = 0.3f))
                .padding(6.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
private fun DeleteLink(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = MinTouch).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text("이 사람 지우기", style = MajiTheme.type.button, color = MajiTheme.colors.warning)
    }
}

// ───────────── 미리보기 ─────────────

private val previewPresets = listOf(
    "오늘도 고생 많았어요",
    "어서 와요, 자리 맞춰 뒀어요",
    "기다렸어요! 편하게 앉아요",
    "오늘 하루 어땠어요?"
)

private val previewProfiles = FakeProfileRepository.samples()
    .mapIndexed { i, p -> p.copy(id = i + 1L) }

@Preview(name = "새로 맞이할 사람 · 넓은 창", widthDp = 1682, heightDp = 1120)
@Composable
private fun NewPreview() = MajiTheme(ThemeMode.LIGHT) { ProfileEditScreen(null, previewPresets, false, {}, {}, {}) }

@Preview(name = "엄마 맞이 설정 · 반쪽 창 · 밤", widthDp = 841, heightDp = 1120)
@Composable
private fun EditPreview() = MajiTheme(ThemeMode.DARK) {
    ProfileEditScreen(previewProfiles[0].copy(lightColor = 0xFFF59AC2.toInt()), previewPresets, false, {}, {}, {})
}

@Preview(name = "주행 중 · 반쪽 창", widthDp = 841, heightDp = 1120)
@Composable
private fun DrivingPreview() = MajiTheme(ThemeMode.LIGHT) { ProfileEditScreen(previewProfiles[1], previewPresets, true, {}, {}, {}) }
