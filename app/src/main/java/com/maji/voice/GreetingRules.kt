package com.maji.voice

import com.maji.contract.Profile

// 인사 규칙 (#15). 안드로이드와 상관없는 계산만 모아서 단위 테스트로 확인해요.
object GreetingRules {

    /** TTS가 안 될 때 트는 기본 인사 파일 (assets 기준 경로) */
    const val DEFAULT_GREETING_AUDIO = "audio/greeting_default.mp3"

    /**
     * 읽을 인사 문장.
     * 오늘만의 인사(specialGreetingText) → 고른 인사(greetingText) → 기본 인사 목록 첫 번째 순서예요.
     * 비어 있거나 공백뿐인 칸은 건너뛰어요. 전부 없으면 null → 바로 파일로 가요.
     */
    fun textFor(profile: Profile, defaults: List<String>): String? =
        sequenceOf(profile.specialGreetingText, profile.greetingText, defaults.firstOrNull())
            .mapNotNull { it?.trim() }
            .firstOrNull { it.isNotEmpty() }

    /**
     * TTS 실패 때 틀 파일들 (앞에서부터 시도).
     * 프로필의 greetingAudio가 있으면 먼저, 그다음 기본 파일이에요.
     */
    fun fallbackAudiosFor(profile: Profile): List<String> =
        listOfNotNull(profile.greetingAudio?.trim()?.takeIf { it.isNotEmpty() }, DEFAULT_GREETING_AUDIO)
            .distinct()
}
