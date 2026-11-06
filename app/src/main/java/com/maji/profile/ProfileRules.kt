package com.maji.profile

import com.maji.contract.Climate
import com.maji.contract.Profile
import com.maji.contract.Season

// 프로필 규칙 모음 (#3 계절, #4 검증, #5 학습).
// 저장 방식(Fake, Room)과 상관없이 똑같이 쓰려고 따로 뺐어요.

object ProfileRules {

    // ───────────── 값 범위 (#4) ─────────────
    const val HEAT_MIN = -3
    const val HEAT_MAX = 3
    const val TEMP_MIN = 15.0f
    const val TEMP_MAX = 30.0f
    const val BRIGHTNESS_MIN = 0
    const val BRIGHTNESS_MAX = 100

    // TODO(#11): 근희에게 시트 끝에서 끝까지 걸리는 시간 받아서 바꾸기
    const val SEAT_MOVE_MAX_MS = 10_000

    // ───────────── 계절 (#3) ─────────────

    /** 4..10월 SUMMER, 11..3월 WINTER */
    fun seasonFor(month: Int): Season {
        require(month in 1..12) { "month는 1..12여야 해요: $month" }
        return if (month in 4..10) Season.SUMMER else Season.WINTER
    }

    /** 그 계절 값. null이면 아직 그 계절 설정을 안 한 것 (기본값으로 채우지 않음) */
    fun climateFor(profile: Profile, season: Season): Climate? = when (season) {
        Season.SUMMER -> profile.summer
        Season.WINTER -> profile.winter
    }

    /** 그 계절 칸만 바꾼 새 프로필 (다른 계절은 그대로) */
    fun withClimate(profile: Profile, season: Season, climate: Climate?): Profile = when (season) {
        Season.SUMMER -> profile.copy(summer = climate)
        Season.WINTER -> profile.copy(winter = climate)
    }

    // ───────────── 검증 (#4) ─────────────

    /** 범위 밖이면 가장 가까운 값으로. null은 "모름/그대로 두기"라 그대로 */
    fun validate(climate: Climate): Climate = Climate(
        seatHeat = climate.seatHeat?.coerceIn(HEAT_MIN, HEAT_MAX),
        temperature = climate.temperature?.coerceIn(TEMP_MIN, TEMP_MAX)
    )

    /** 저장 전에 프로필 전체 검사. 계절 칸이 null이면 건너뜀 */
    fun validate(profile: Profile): Profile = profile.copy(
        name = profile.name.trim(),
        winter = profile.winter?.let { validate(it) },
        summer = profile.summer?.let { validate(it) },
        seatMoveMs = profile.seatMoveMs.coerceIn(-SEAT_MOVE_MAX_MS, SEAT_MOVE_MAX_MS),
        lightBrightness = profile.lightBrightness.coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX)
    )

    // ───────────── 학습 (#5) ─────────────

    /**
     * changed에서 null이 아닌 칸만 current에 덮어써요.
     * current가 null(그 계절 설정 전)이면 changed 값으로 새로 채워요.
     * changed가 전부 null이면 null을 돌려줘요 → 바꿀 게 없다는 뜻
     */
    fun merge(current: Climate?, changed: Climate): Climate? {
        if (changed.seatHeat == null && changed.temperature == null) return null
        val checked = validate(changed)
        val base = current ?: Climate()
        return Climate(
            seatHeat = checked.seatHeat ?: base.seatHeat,
            temperature = checked.temperature ?: base.temperature
        )
    }
}