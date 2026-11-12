package com.maji.flow

import com.maji.contract.AmbientLight
import com.maji.contract.Profile
import kotlin.math.abs

// 내릴 때 마지막 값 기억하기 규칙 (#111). 흐름과 떼어 둬서 단위 테스트로 확인해요.
object RideLearnRules {

    // 차 밝기 범위를 앱 기준(0..100)으로 바꾸며 생기는 반올림 차이는 바뀐 걸로 보지 않아요
    private const val BRIGHTNESS_TOLERANCE = 2

    /**
     * 탄 뒤에 바뀐 열선·온도 기록 값.
     * 처음 맞이(준비 값 없음)여도 탄 뒤 바뀐 마지막 값을 기록하고,
     * 준비 값이 있는데 그 값으로 되돌렸으면 기록을 지워요(null).
     */
    fun <T> recordAfterBoarding(value: T, prepared: T?): T? =
        if (prepared != null && value == prepared) null else value

    /**
     * 기억할 시트 값. 탈 때(문 열림) 기록과 내릴 때 기록이 다르면 탄 뒤에 앱으로 움직인 것이라
     * 내릴 때 위치(원래 자리에서 움직인 시간)를 저장해요. 탈 때 기록을 모르면(앱 재시작 등) 저장하지 않아요.
     */
    fun seatToLearn(atBoarding: Int?, atEnd: Int): Int? =
        if (atBoarding != null && atBoarding != atEnd) atEnd else null

    /** 시트·무드등을 반영한 프로필. 바뀐 게 없으면 null → 저장할 필요 없음 */
    fun withRide(profile: Profile, seatMs: Int?, light: AmbientLight?): Profile? {
        var p = profile
        if (seatMs != null && seatMs != p.seatMoveMs) p = p.copy(seatMoveMs = seatMs)
        if (light != null) {
            val sameColor = p.lightColor != null && (p.lightColor!! and 0xFFFFFF) == (light.color and 0xFFFFFF)
            if (!sameColor) p = p.copy(lightColor = light.color)
            if (abs(light.brightness - p.lightBrightness) > BRIGHTNESS_TOLERANCE) p = p.copy(lightBrightness = light.brightness)
        }
        return if (p == profile) null else p
    }
}
