package com.maji.profile

import com.maji.contract.Climate
import com.maji.contract.Profile
import org.junit.Assert.assertEquals
import org.junit.Test

// 범위 밖 값 보정 (#4, 통합 테스트 S8). 저장 전에 값을 안전한 범위로 맞춰요.
class ProfileValidateTest {

    @Test
    fun `범위 위로 벗어난 값은 가장 가까운 최대값으로 맞춰요`() {
        val p = ProfileRules.validate(
            Profile(
                id = 1, name = "엄마",
                winter = Climate(seatHeat = 5, temperature = 40f),
                seatMoveMs = 20_000,
                lightBrightness = 150
            )
        )
        assertEquals(Climate(seatHeat = 3, temperature = 30f), p.winter)
        assertEquals(10_000, p.seatMoveMs)
        assertEquals(100, p.lightBrightness)
    }

    @Test
    fun `범위 아래로 벗어난 값은 가장 가까운 최소값으로 맞춰요`() {
        val p = ProfileRules.validate(
            Profile(
                id = 1, name = "엄마",
                summer = Climate(seatHeat = -9, temperature = 10f),
                seatMoveMs = -20_000,
                lightBrightness = -5
            )
        )
        assertEquals(Climate(seatHeat = -3, temperature = 15f), p.summer)
        assertEquals(-10_000, p.seatMoveMs)
        assertEquals(0, p.lightBrightness)
    }

    @Test
    fun `범위 안의 값과 이름 앞뒤 공백 정리는 그대로 두고, 그대로 두기(null)도 유지해요`() {
        val p = ProfileRules.validate(
            Profile(id = 1, name = "  엄마  ", winter = Climate(seatHeat = 2, temperature = null), seatMoveMs = 500)
        )
        assertEquals("엄마", p.name)
        assertEquals(Climate(seatHeat = 2, temperature = null), p.winter)
        assertEquals(500, p.seatMoveMs)
    }
}
