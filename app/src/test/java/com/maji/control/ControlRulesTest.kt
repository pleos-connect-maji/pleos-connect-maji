package com.maji.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// B 차량 제어 계산 규칙 단위 테스트. 에뮬레이터 없이 돌아가요.
class ControlRulesTest {

    // ───────── 시트 이동 기록 (#11, #14, #46) ─────────

    @Test
    fun `앞으로 움직인 시간을 재서 합계에 더한다`() {
        val t = SeatMoveTracker()
        t.start(forward = true, now = 1_000)
        assertEquals(1_500, t.stop(now = 2_500))
        assertEquals(1_500, t.offsetMs)
    }

    @Test
    fun `뒤로 움직이면 음수로 기록한다`() {
        val t = SeatMoveTracker()
        t.start(forward = false, now = 0)
        assertEquals(-800, t.stop(now = 800))
        assertEquals(-800, t.offsetMs)
    }

    @Test
    fun `에뮬레이터 시나리오 문열림 정지와 앞뒤 조절 후 정확히 되돌린다`() {
        // 자리 준비 3초 계획 → 1.794초에 문 열림으로 정지 → ▶ 1.501초 → ◀ 0.812초
        val t = SeatMoveTracker()
        t.start(true, 0); t.stop(1_794)
        t.start(true, 5_000); t.stop(6_501)
        t.start(false, 8_000); t.stop(8_812)
        assertEquals(2_483, t.offsetMs)
        assertEquals(-2_483, t.restorePlanMs()) // 계획값(3000)이 아니라 실제 이동 기준으로 되돌림
    }

    @Test
    fun `복구까지 하면 원래 자리로 돌아온다`() {
        val t = SeatMoveTracker()
        t.start(true, 0); t.stop(3_000)
        t.start(t.restorePlanMs() > 0, 10_000); t.stop(13_000)
        assertEquals(0, t.offsetMs)
    }

    @Test
    fun `움직이지 않을 때 정지하면 0이고 합계는 그대로다`() {
        val t = SeatMoveTracker()
        t.start(true, 0); t.stop(1_000)
        assertFalse(t.isMoving)
        assertEquals(0, t.stop(5_000)) // 문 열림 안전 정지가 한 번 더 와도 무해
        assertEquals(1_000, t.offsetMs)
    }

    @Test
    fun `합계는 끝에서 끝 범위를 넘지 않는다`() {
        val t = SeatMoveTracker(maxMs = 10_000)
        t.start(true, 0); t.stop(7_000)
        t.start(true, 10_000); t.stop(17_000) // 시트는 끝에 닿으면 멈추므로 10초 이상은 의미 없음
        assertEquals(10_000, t.offsetMs)
    }

    @Test
    fun `시계가 거꾸로 가도 음수 시간으로 기록하지 않는다`() {
        val t = SeatMoveTracker()
        t.start(true, 5_000)
        assertEquals(0, t.stop(4_000))
    }

    // ───────── 값 범위 (#10, #46) ─────────

    @Test
    fun `시트 이동 계획은 플러스마이너스 10초로 자른다`() {
        assertEquals(10_000, ControlRules.clampSeatMove(25_000))
        assertEquals(-10_000, ControlRules.clampSeatMove(-25_000))
        assertEquals(3_000, ControlRules.clampSeatMove(3_000))
    }

    @Test
    fun `열선은 마이너스3에서 3 사이로 자른다`() {
        assertEquals(3, ControlRules.clampHeat(5))
        assertEquals(-3, ControlRules.clampHeat(-9)) // 음수 = 통풍
        assertEquals(2, ControlRules.clampHeat(2))
    }

    @Test
    fun `온도는 15도에서 30도 사이로 자른다`() {
        assertEquals(30f, ControlRules.clampTemperature(35f), 0f)
        assertEquals(15f, ControlRules.clampTemperature(10f), 0f)
        assertEquals(22.5f, ControlRules.clampTemperature(22.5f), 0f)
    }

    // ───────── 무드등 밝기 (#12) ─────────

    @Test
    fun `밝기 퍼센트를 차 범위로 바꾼다`() {
        assertEquals(60, ControlRules.scaleBrightness(60, 0, 100))
        assertEquals(1, ControlRules.scaleBrightness(0, 1, 10))
        assertEquals(10, ControlRules.scaleBrightness(100, 1, 10))
        assertEquals(10, ControlRules.scaleBrightness(150, 1, 10)) // 100 넘으면 최대
    }

    // ───────── 대체 모드 (#13) ─────────

    @Test
    fun `하나라도 되면 제어 가능`() {
        assertTrue(ControlRules.isControlAvailable(listOf(false, true, false), allPrepareFailed = false))
    }

    @Test
    fun `확인 중이면 일단 시도한다`() {
        assertTrue(ControlRules.isControlAvailable(listOf(null, false, false), allPrepareFailed = false))
    }

    @Test
    fun `전부 안 되면 대체 모드`() {
        assertFalse(ControlRules.isControlAvailable(listOf(false, false, false), allPrepareFailed = false))
    }

    @Test
    fun `자리 준비 명령이 전부 실패했으면 대체 모드`() {
        assertFalse(ControlRules.isControlAvailable(listOf(true, true, true), allPrepareFailed = true))
    }
}
