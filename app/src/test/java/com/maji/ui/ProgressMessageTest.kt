package com.maji.ui

import com.maji.contract.Climate
import com.maji.contract.MajiStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// 진행 화면 문구 (#24, 리디자인 #109). 단계·주행·대체 모드에 따라 맞는 문구와 값이 나오는지 확인해요.
class ProgressMessageTest {

    @Test
    fun `받침에 맞춰 조사를 고른다`() {
        assertEquals("엄마를", josa("엄마", "을", "를"))
        assertEquals("동생을", josa("동생", "을", "를"))
        assertEquals("엄마와", josa("엄마", "과", "와"))
        assertEquals("동생과", josa("동생", "과", "와"))
        assertEquals("Mom를", josa("Mom", "을", "를"))   // 한글이 아니면 받침 없음으로
    }

    @Test
    fun `단계별 제목과 부제`() {
        assertEquals(ProgressCopy("엄마를\n맞이하러 가는 중", null), progressCopy(MajiStep.HEADING, "엄마", drivingRestricted = true))
        assertEquals(ProgressCopy("엄마를 맞이할\n준비 중이에요", "열선·온도·시트를 맞추고 있어요"), progressCopy(MajiStep.PREPARING, "엄마", false))
        assertEquals(ProgressCopy("엄마, 어서 와요", "자리를 맞춰 뒀어요"), progressCopy(MajiStep.BOARDED, "엄마", false))
        assertEquals(ProgressCopy("오늘도\n잘 다녀왔어요", "다음에도 엄마 자리를 준비해 둘게요"), progressCopy(MajiStep.FINISHED, "엄마", false))
    }

    @Test
    fun `함께 가는 길은 주행 중과 정차 중이 다르다`() {
        assertEquals(ProgressCopy("엄마와\n함께 가는 중", "엄마 무드등을 켜고 창문을 닫아요"), progressCopy(MajiStep.RIDING_HOME, "엄마", drivingRestricted = true))
        assertEquals("엄마 무드등을 켜요", progressCopy(MajiStep.RIDING_HOME, "엄마", true, closeWindow = false).subtitle)
        // "자리를 원래대로 돌려요"는 다 내렸어요 타일 안에 있어서 제목 아래에는 없어요
        assertEquals(ProgressCopy("엄마가 다 내리면\n눌러 주세요", null), progressCopy(MajiStep.RIDING_HOME, "엄마", drivingRestricted = false))
    }

    // ───────────── 대체 모드 (#13) ─────────────

    @Test
    fun `대체 모드 준비와 완료 문구`() {
        assertEquals(ProgressCopy("엄마 자리는\n직접 맞춰 주세요", "이 차에서는 앱이 자리를 바꿀 수 없어요"), progressCopy(MajiStep.PREPARING, "엄마", false, fallback = true))
        assertEquals("자리는 직접 원래대로 돌려 주세요", progressCopy(MajiStep.FINISHED, "엄마", false, fallback = true).subtitle)
        assertNull(progressCopy(MajiStep.BOARDED, "엄마", false, fallback = true).subtitle)
    }

    // ───────────── 값 목록 ─────────────

    @Test
    fun `열선은 음수면 통풍, 0이면 끔`() {
        assertEquals("열선" to "2단", heatRow(2))
        assertEquals("통풍" to "2단", heatRow(-2))
        assertEquals("열선" to "끔", heatRow(0))
    }

    @Test
    fun `온도와 시트 표시`() {
        assertEquals("22℃", tempText(22f))
        assertEquals("22.5℃", tempText(22.5f))
        assertEquals("앞으로 2.4초", seatText(2400))
        assertEquals("뒤로 0.8초", seatText(-800))
        assertEquals("원래 위치", seatText(0))
    }

    @Test
    fun `맞출 값 목록은 있는 칸만 보여 준다`() {
        assertEquals(
            listOf("열선" to "2단", "온도" to "22℃", "시트" to "앞으로 2.4초"),
            climateRows(Climate(seatHeat = 2, temperature = 22f), seatMoveMs = 2400)
        )
        assertEquals(listOf("열선" to "끔"), climateRows(Climate(seatHeat = 0)))
        assertEquals(emptyList<Pair<String, String>>(), climateRows(null))
    }
}
