package com.maji.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.haze
import kotlin.math.max

// 맞이 공통 부품 (#107). 모양·색 규칙은 docs/ui/research.md 7-0을 따라요.
// - 유리(리퀴드 글래스)는 떠 있는 조작 층(보조 버튼)에만 써요. 내용은 단색 카드에 올려요.
// - 누르는 곳은 높이 76dp 이상이에요 (Android for Cars).

/** 버튼 최소 높이 (Android for Cars 터치 기준) */
val MinTouch: Dp = 76.dp

/**
 * 화면 바탕. 라이트는 흰 바탕 모서리마다 옅은 파스텔, 다크는 Pleos 바탕에 살구빛.
 * accent(그 사람 무드등 색)를 주면 오른쪽 위에서 그 색 빛이 은은하게 번져요.
 * 글자가 놓이는 곳은 빛이 옅게 남도록 가장 진한 곳을 화면 모서리 바깥에 둬요.
 */
@Composable
fun MajiBackground(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val c = MajiTheme.colors
    val haze = remember { HazeState() }
    Box(modifier = modifier.fillMaxSize()) {
        // 바탕 빛을 그리는 층. 유리 버튼이 이 층을 흐리게 비춰요 (haze)
        Box(
            Modifier
                .matchParentSize()
                .haze(haze)
                .background(c.background)
                .drawBehind {
                    val corners = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
                    val radius = max(size.width, size.height) * 0.6f
                    corners.zip(c.pastel).forEach { (at, color) ->
                        if (color.alpha > 0f) {
                            drawRect(Brush.radialGradient(listOf(color, Color.Transparent), center = at, radius = radius))
                        }
                    }
                    if (accent != null) {
                        val strength = if (c.isDark) 0.30f else 0.35f
                        drawRect(
                            Brush.radialGradient(
                                listOf(accent.copy(alpha = strength), Color.Transparent),
                                center = Offset(size.width * 0.85f, -size.height * 0.05f),
                                radius = max(size.width, size.height) * 0.55f
                            )
                        )
                    }
                }
        )
        CompositionLocalProvider(LocalHazeState provides haze) { content() }
    }
}

/** 바탕 층(흐림 원본). MajiBackground 안에서만 있어요 */
private val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** 내용 카드 (단색 면, 스쿼클 모서리) */
@Composable
fun MajiCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = MajiTheme.colors
    val shape = SquircleShape(28.dp)
    Column(
        modifier = modifier
            .then(if (c.isDark) Modifier else Modifier.shadow(16.dp, shape, ambientColor = ShadowTint, spotColor = ShadowTint))
            .clip(shape)
            .background(c.surface),
        content = content
    )
}

/** 주 버튼: 화면에서 가장 중요한 하나. 꽉 찬 캡슐이에요 */
@Composable
fun MajiPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = MajiTheme.colors
    CapsuleButton(text, onClick, modifier, enabled, fill = c.primary, textColor = c.onPrimary, glass = false)
}

/** 보조 버튼: 리퀴드 글래스 캡슐. 바탕 빛이 비치고, 위쪽에 옅은 빛이 맺혀 두께가 느껴져요 */
@Composable
fun MajiGlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = MajiTheme.colors
    CapsuleButton(text, onClick, modifier, enabled, fill = c.glass, textColor = c.text, glass = true)
}

@Composable
private fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    fill: Color,
    textColor: Color,
    glass: Boolean
) {
    val c = MajiTheme.colors
    val haze = LocalHazeState.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = MinTouch)
            .alpha(if (enabled) 1f else 0.4f)
            // 누름 효과가 버튼 전체(그림자·배경·글자)에 걸리도록 clickable을 앞에 둬요
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (glass) Modifier.shadow(20.dp, CircleShape, ambientColor = ShadowTint, spotColor = ShadowTint) else Modifier)
            .clip(CircleShape)
            .then(
                // 유리: 뒤 바탕을 흐리게 비추고 반투명 색을 얹어요. 바탕 층이 없으면 반투명 색만
                if (glass && haze != null) Modifier.hazeChild(
                    state = haze,
                    style = HazeStyle(backgroundColor = c.background, tints = listOf(HazeTint(fill)), blurRadius = 28.dp, noiseFactor = 0f)
                ) else Modifier.background(fill)
            )
            .then(
                if (glass) Modifier.drawWithContent {
                    // 위쪽 40%에만 옅은 빛 → 선을 긋지 않고 두께를 표현해요
                    drawRect(
                        Brush.verticalGradient(
                            0f to c.glassHighlight.copy(alpha = c.glassHighlight.alpha * 0.55f),
                            0.4f to Color.Transparent
                        )
                    )
                    drawContent()
                } else Modifier
            )
            .padding(horizontal = 36.dp)
    ) {
        Text(text = text, style = MajiTheme.type.button, color = textColor, textAlign = TextAlign.Center)
    }
}

/**
 * 보송한 프로필 원. 가장자리가 반지름 절반쯤부터 서서히 옅어져서, 안쪽 원과 테두리가 한 덩어리로 이어져요.
 * 이니셜 글자는 선명하게 두고, 글자색은 그 사람 색을 아주 진하게 한 색이에요.
 */
@Composable
fun SoftAvatar(initial: String, color: Color, size: Dp = 88.dp, modifier: Modifier = Modifier) {
    val light = lerp(color, Color.White, 0.45f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithCache {
                val r = this.size.minDimension / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val fill = Brush.radialGradient(
                    0f to light, 0.55f to lerp(color, Color.White, 0.18f), 1f to color,
                    center = Offset(center.x * 0.84f, center.y * 0.72f), radius = r * 1.1f
                )
                val feather = Brush.radialGradient(
                    0f to Color.Black, 0.52f to Color.Black, 0.66f to Color.Black.copy(alpha = 0.9f),
                    0.80f to Color.Black.copy(alpha = 0.6f), 0.92f to Color.Black.copy(alpha = 0.25f), 1f to Color.Transparent,
                    center = center, radius = r
                )
                onDrawBehind {
                    drawCircle(fill, radius = r, center = center)
                    drawRect(feather, blendMode = BlendMode.DstIn)
                }
            }
    ) {
        Text(
            text = initial,
            color = lerp(color, Color.Black, 0.78f),
            style = MajiTheme.type.title.copy(fontSize = (size.value * 0.4f).sp, lineHeight = (size.value * 0.4f).sp)
        )
    }
}

/** 카드 안 한 줄: "열선   2단" */
@Composable
fun MajiRow(label: String, value: String, modifier: Modifier = Modifier) {
    val c = MajiTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MajiTheme.type.body, color = c.text)
        Text(value, style = MajiTheme.type.number, color = c.text)
    }
}

// 그림자: 라이트에서는 차가운 검정 대신 옅은 보랏빛
private val ShadowTint = Color(0x40786E8C)

// ───────────── 미리보기 (넓은 창 1682×1120, 반쪽 창 841×1120 · 에뮬레이터 실측) ─────────────

@Composable
private fun ThemeSample() {
    val c = MajiTheme.colors
    val mom = MoodLightColors.all[1].second
    MajiBackground(accent = mom) {
        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            SoftAvatar("엄", mom)
            Text("엄마를 맞이할\n준비 중이에요", style = MajiTheme.type.headline, color = c.text)
            Text("열선·온도·시트를 맞추고 있어요", style = MajiTheme.type.caption, color = c.textSub)
            MajiCard(Modifier.fillMaxWidth()) {
                MajiRow("열선", "2단")
                MajiRow("온도", "22℃")
                MajiRow("시트", "앞으로 2.4초")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MajiGlassButton("맞이 그만하기", onClick = {}, modifier = Modifier.weight(1f))
                MajiPrimaryButton("다 내렸어요", onClick = {}, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Preview(name = "라이트 · 반쪽 창", widthDp = 841, heightDp = 1120)
@Composable
private fun LightHalfPreview() = MajiTheme(ThemeMode.LIGHT) { ThemeSample() }

@Preview(name = "다크 · 반쪽 창", widthDp = 841, heightDp = 1120)
@Composable
private fun DarkHalfPreview() = MajiTheme(ThemeMode.DARK) { ThemeSample() }

@Preview(name = "라이트 · 넓은 창", widthDp = 1682, heightDp = 1120)
@Composable
private fun LightWidePreview() = MajiTheme(ThemeMode.LIGHT) { ThemeSample() }
