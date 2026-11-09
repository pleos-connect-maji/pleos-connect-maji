package com.maji.ui

import com.maji.contract.Climate
import org.junit.Assert.assertEquals
import org.junit.Test

// 자리 설정 화면을 띄우는 조건 (#46). 원영과 조건을 정하기 전까지는 지금 규칙을 고정해 둬요.
class SeatSetupRulesTest {

    private val noClimate: Climate? = null
    private val hasClimate: Climate? = Climate(seatHeat = 2, temperature = 22f)

    @Test
    fun `맞이 중이고 정차 중이고 문이 닫혀 있고 오늘 값이 없으면 띄워요`() {
        assertEquals(true, needsSeatSetup(active = true, seatSetupDone = false, isPark = true, doorOpen = false, climateToday = noClimate))
    }

    @Test
    fun `맞이 중이 아니면 띄우지 않아요`() {
        assertEquals(false, needsSeatSetup(active = false, seatSetupDone = false, isPark = false, doorOpen = false, climateToday = noClimate))
    }

    @Test
    fun `이미 끝냈으면 다시 띄우지 않아요`() {
        assertEquals(false, needsSeatSetup(active = true, seatSetupDone = true, isPark = false, doorOpen = false, climateToday = noClimate))
    }

    @Test
    fun `주행 중(P단이 아님)이면 띄우지 않아요`() {
        assertEquals(false, needsSeatSetup(active = true, seatSetupDone = false, isPark = false, doorOpen = false, climateToday = noClimate))
    }

    @Test
    fun `조수석 문이 열려 있으면 띄우지 않아요`() {
        assertEquals(false, needsSeatSetup(active = true, seatSetupDone = false, isPark = false, doorOpen = true, climateToday = noClimate))
    }

    @Test
    fun `오늘 계절 값이 이미 있으면 띄우지 않아요`() {
        assertEquals(false, needsSeatSetup(active = true, seatSetupDone = false, isPark = false, doorOpen = false, climateToday = hasClimate))
    }
}
