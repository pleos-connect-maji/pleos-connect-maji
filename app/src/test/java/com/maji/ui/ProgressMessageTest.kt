package com.maji.ui

import com.maji.contract.MajiStep
import org.junit.Assert.assertEquals
import org.junit.Test

// 진행 화면 문구 (#24). 단계와 주행 상태에 따라 맞는 문구가 나오는지 확인해요.
class ProgressMessageTest {

    @Test
    fun `주행 중에는 단계와 상관없이 한 줄 문구만 나와요`() {
        MajiStep.values().forEach { step ->
            assertEquals(
                "엄마 맞이 중 · 도착하면 자리를 준비할게요",
                progressMessage(step, "엄마", drivingRestricted = true)
            )
        }
    }

    @Test
    fun `정차 중에는 단계별 문구가 나와요`() {
        assertEquals("엄마 맞이 중 · 도착하면 자리를 준비할게요", progressMessage(MajiStep.HEADING, "엄마", false))
        assertEquals("엄마 자리 맞추는 중…", progressMessage(MajiStep.PREPARING, "엄마", false))
        assertEquals("엄마 탑승했어요 · 편안한 귀갓길 되세요", progressMessage(MajiStep.BOARDED, "엄마", false))
        assertEquals("엄마 귀갓길 중이에요", progressMessage(MajiStep.RIDING_HOME, "엄마", false))
        assertEquals("맞이가 끝났어요", progressMessage(MajiStep.FINISHED, "엄마", false))
    }
}
