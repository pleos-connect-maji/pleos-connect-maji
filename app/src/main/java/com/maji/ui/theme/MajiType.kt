package com.maji.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 맞이 글자 크기 (Pleos Typography 기준: Headline 60/56/48, Title 40/36/32, Body 30/28/26).
 * 글꼴은 시스템 기본이에요. Pleos 차에서는 기본 sans-serif가 Asta Sans라서 따로 넣지 않아요.
 * 바뀌는 숫자(시간·온도·단계)는 number 스타일을 써요: 숫자 폭이 같아서 바뀔 때 흔들리지 않아요(tnum).
 */
@Immutable
data class MajiType(
    val headline: TextStyle,  // 화면 제목 "엄마를 맞이하러 가는 중"
    val title: TextStyle,     // 카드 이름 "엄마"
    val body: TextStyle,      // 목록 줄 "열선 2단"
    val caption: TextStyle,   // 보조 설명
    val button: TextStyle,
    val number: TextStyle     // 숫자 강조 "22℃"
)

// 줄 높이는 Pleos 권장대로 글자 크기의 1.3~1.5배예요
private val Base = TextStyle(fontFamily = FontFamily.Default)

val DefaultMajiType = MajiType(
    headline = Base.copy(fontSize = 48.sp, lineHeight = 62.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    title = Base.copy(fontSize = 36.sp, lineHeight = 48.sp, fontWeight = FontWeight.ExtraBold),
    body = Base.copy(fontSize = 28.sp, lineHeight = 42.sp, fontWeight = FontWeight.Normal),
    caption = Base.copy(fontSize = 26.sp, lineHeight = 39.sp, fontWeight = FontWeight.Normal),
    button = Base.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    number = Base.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
)

val LocalMajiType = staticCompositionLocalOf { DefaultMajiType }
