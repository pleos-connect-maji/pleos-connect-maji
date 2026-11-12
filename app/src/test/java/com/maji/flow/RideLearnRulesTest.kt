package com.maji.flow

import com.maji.contract.AmbientLight
import com.maji.contract.Profile
import com.maji.control.ControlRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// 내릴 때 마지막 값 기억 규칙 (#111)
class RideLearnRulesTest {

    private val mom = Profile(id = 1, name = "엄마", seatMoveMs = 500, lightColor = 0xFFFFB36B.toInt(), lightBrightness = 30)

    @Test
    fun `탄 뒤 값은 준비 값이 없어도 기록하고, 준비 값으로 되돌리면 지워요`() {
        assertEquals(2, RideLearnRules.recordAfterBoarding(2, null))
        assertEquals(1, RideLearnRules.recordAfterBoarding(1, 2))
        assertNull(RideLearnRules.recordAfterBoarding(2, 2))
    }

    @Test
    fun `시트는 탈 때와 내릴 때 기록이 다를 때만`() {
        assertEquals(1200, RideLearnRules.seatToLearn(300, 1200))
        assertNull(RideLearnRules.seatToLearn(300, 300))
        assertNull(RideLearnRules.seatToLearn(null, 1200))  // 재시작으로 탈 때 기록을 모름
    }

    @Test
    fun `무드등은 색이 다르거나 밝기가 오차보다 크게 다를 때만 바꿔요`() {
        assertNull(RideLearnRules.withRide(mom, null, AmbientLight(0x00FFB36B, 31)))  // 알파 차이·반올림 차이
        assertEquals(mom.copy(lightColor = 0xFF6FB2FF.toInt()), RideLearnRules.withRide(mom, null, AmbientLight(0xFF6FB2FF.toInt(), 30)))
        assertEquals(mom.copy(lightBrightness = 60), RideLearnRules.withRide(mom, null, AmbientLight(0xFFFFB36B.toInt(), 60)))
        assertEquals(mom.copy(seatMoveMs = -800), RideLearnRules.withRide(mom, -800, null))
        assertNull(RideLearnRules.withRide(mom, 500, null))
    }

    @Test
    fun `차 밝기를 앱 기준으로 되돌리면 원래 퍼센트가 나와요`() {
        assertEquals(30, ControlRules.unscaleBrightness(ControlRules.scaleBrightness(30, 0, 255), 0, 255))
        // 단계가 거친 차: 30% → 3 → 그냥 되돌리면 22%. 앱이 쓴 값이면 30% 그대로
        assertEquals(30, ControlRules.readBrightness(ControlRules.scaleBrightness(30, 1, 10), 1, 10, lastWrittenPct = 30))
        // 차에서 직접 바꿨으면 읽은 값으로
        assertEquals(100, ControlRules.readBrightness(10, 1, 10, lastWrittenPct = 30))
        assertEquals(0, ControlRules.unscaleBrightness(5, 3, 3))
    }
}
