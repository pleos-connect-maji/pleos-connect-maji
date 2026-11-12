package com.maji.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 맞이 색 (docs/ui/research.md 7-0).
 * 라이트 = 흰 바탕 + 옅은 파스텔, 다크 = Pleos 다크 바탕 + 따뜻한 빛.
 * 글자 대비는 4.5:1 이상으로 맞춘 값이에요 (Android for Cars 기준).
 */
@Immutable
data class MajiColors(
    val isDark: Boolean,
    val background: Color,       // 화면 바탕
    val text: Color,             // 본문 글자
    val textSub: Color,          // 보조 글자
    val surface: Color,          // 내용 카드 (단색 면)
    val divider: Color,          // 카드 안 구분선
    val glass: Color,            // 유리 버튼 채움 (떠 있는 조작 층에만)
    val glassHighlight: Color,   // 유리 위쪽 빛 (두께감)
    val primary: Color,          // 주 버튼
    val onPrimary: Color,
    val scrim: Color,            // 시트 뒤 어둡게
    val sheet: Color,            // 확인 시트
    val success: Color,          // 끝남 (Pleos informative_active)
    val toggleOn: Color,         // 켜진 토글. 민트빛 Pleos 초록 대신 노란기 도는 따뜻한 잎 초록 (흰 손잡이 대비 3.3:1)
    val warning: Color,          // 직접 해야 함 (Pleos informative_negative)
    val pastel: List<Color>      // 바탕 빛: 왼쪽 위, 오른쪽 위, 왼쪽 아래, 오른쪽 아래
)

val LightMajiColors = MajiColors(
    isDark = false,
    background = Color(0xFFFBFBFA),
    text = Color(0xFF191F28),
    textSub = Color(0xFF4E5968),      // 파스텔 위에서도 6:1 이상 (#6B7684는 부족했어요)
    surface = Color(0xBDFFFFFF),      // 흰색 74%
    divider = Color(0x12191F28),
    glass = Color(0x73FFFFFF),        // 흰색 45%
    glassHighlight = Color(0xF2FFFFFF),
    primary = Color(0xFF191F28),
    onPrimary = Color.White,
    scrim = Color(0x47282432),
    sheet = Color(0xEBFFFFFF),
    success = Color(0xFF02A055),      // 흰 바탕 아이콘용으로 한 단계 진하게
    toggleOn = Color(0xFF4E9E57),
    warning = Color(0xFFE5361A),
    pastel = listOf(
        Color(0xCCCDE4F5),            // 하늘
        Color(0xBFF6C8D6),            // 분홍
        Color(0xD9F4F0CD),            // 노랑
        Color(0xB3D4E8F6)             // 하늘
    )
)

val DarkMajiColors = MajiColors(
    isDark = true,
    background = Color(0xFF131417),   // Pleos basic_900
    text = Color.White,
    textSub = Color(0xFFC9CCD3),
    surface = Color(0xFF1E1D22),
    divider = Color(0x0FFFFFFF),
    glass = Color(0x17FFECE4),        // 살짝 따뜻한 흰색 9%
    glassHighlight = Color(0x61FFE1D7),
    primary = Color(0xFFF7F3F1),      // 아이보리
    onPrimary = Color(0xFF151418),
    scrim = Color(0x80000000),
    sheet = Color(0xFF25242A),
    success = Color(0xFF00E074),
    toggleOn = Color(0xFF4E9E57),     // 어두운 바탕에서도 5.6:1이라 같은 색
    warning = Color(0xFFFE6A4A),
    pastel = listOf(
        Color(0x21FFBE8C),            // 살구빛
        Color(0x00000000),
        Color(0x00000000),
        Color(0x00000000)
    )
)

val LocalMajiColors = staticCompositionLocalOf { LightMajiColors }

/**
 * 함께 출발할 때(탄 뒤 P 해제) 켜는 무드등 색 (Profile.lightColor에 저장하는 값).
 * 첫 번째는 B의 기본값(DEFAULT_LIGHT_COLOR)과 같아요. 나머지는 현대 아이오닉 6 무드램프 테마의 구성 색에서 골랐어요.
 */
object MoodLightColors {
    val Default = Color(0xFFFFB36B)
    val all: List<Pair<String, Color>> = listOf(
        "기본" to Default,
        "핑크" to Color(0xFFF59AC2),
        "바이올렛" to Color(0xFFA98BF5),
        "옐로" to Color(0xFFF2D66B),
        "블루" to Color(0xFF6FB2FF),
        "터콰이즈" to Color(0xFF43D6D0)
    )

    /** 저장된 ARGB(없으면 기본값)를 화면 색으로 */
    fun of(argb: Int?): Color = argb?.let { Color(it) } ?: Default
}
