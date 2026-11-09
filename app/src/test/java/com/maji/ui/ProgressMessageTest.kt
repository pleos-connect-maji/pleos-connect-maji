package com.maji.ui

import com.maji.contract.Climate
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

    // ───────────── 대체 모드 (#13, 근희 문구) ─────────────

    @Test
    fun `대체 모드 준비 문구는 열선과 온도를 보여 줘요`() {
        assertEquals(
            "엄마 자리는 직접 맞춰 주세요\n열선 2단계, 22℃",
            progressMessage(MajiStep.PREPARING, "엄마", false, fallback = true, climate = Climate(seatHeat = 2, temperature = 22f))
        )
    }

    @Test
    fun `대체 모드에서 음수 열선은 통풍으로, 0은 열선 끔으로 보여 줘요`() {
        assertEquals(
            "엄마 자리는 직접 맞춰 주세요\n통풍 2단계, 22.5℃",
            progressMessage(MajiStep.PREPARING, "엄마", false, fallback = true, climate = Climate(seatHeat = -2, temperature = 22.5f))
        )
        assertEquals(
            "엄마 자리는 직접 맞춰 주세요\n열선 끔",
            progressMessage(MajiStep.PREPARING, "엄마", false, fallback = true, climate = Climate(seatHeat = 0))
        )
    }

    @Test
    fun `대체 모드에서 값이 없으면 첫 줄만 보여 줘요`() {
        assertEquals(
            "엄마 자리는 직접 맞춰 주세요",
            progressMessage(MajiStep.PREPARING, "엄마", false, fallback = true, climate = null)
        )
    }

    @Test
    fun `대체 모드 종료 문구와 나머지 단계 문구는 정해진 대로예요`() {
        assertEquals(
            "맞이가 끝났어요! 자리는 직접 원래대로 돌려 주세요!",
            progressMessage(MajiStep.FINISHED, "엄마", false, fallback = true)
        )
        assertEquals(
            "엄마 탑승했어요 · 편안한 귀갓길 되세요",
            progressMessage(MajiStep.BOARDED, "엄마", false, fallback = true)
        )
        assertEquals(
            "엄마 귀갓길 중이에요",
            progressMessage(MajiStep.RIDING_HOME, "엄마", false, fallback = true)
        )
    }
}
