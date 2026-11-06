package com.maji.profile

import com.maji.contract.Climate
import com.maji.contract.Profile
import com.maji.contract.Season
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// profile 모듈 단위 테스트 (#6). 함수 왼쪽 ▶ 버튼으로 하나씩, 클래스 옆 ▶로 전부 돌려요.
class ProfileRepositoryTest {

    private fun repo() = FakeProfileRepository(FakeProfileRepository.samples())  // 1 엄마, 2 딸, 3 여자친구

    // ───────────── 계절 (#3) ─────────────

    @Test
    fun `4월부터 10월은 여름`() {
        listOf(4, 7, 10).forEach { assertEquals(Season.SUMMER, ProfileRules.seasonFor(it)) }
    }

    @Test
    fun `11월부터 3월은 겨울`() {
        listOf(11, 12, 1, 3).forEach { assertEquals(Season.WINTER, ProfileRules.seasonFor(it)) }
    }

    @Test
    fun `설정 안 한 계절은 null`() = runBlocking {
        val daughter = repo().get(2)!!
        assertNull(ProfileRules.climateFor(daughter, Season.SUMMER))
    }

    // ───────────── 검증 (#4) ─────────────

    @Test
    fun `범위 밖 값은 끝값으로 보정`() = runBlocking {
        val r = repo()
        val id = r.save(
            Profile(
                name = " 친구 ",
                winter = Climate(seatHeat = -9, temperature = 99f),
                seatMoveMs = 99_999,
                lightBrightness = 150
            )
        )
        val p = r.get(id)!!
        assertEquals("친구", p.name)
        assertEquals(Climate(seatHeat = -3, temperature = 30f), p.winter)
        assertEquals(ProfileRules.SEAT_MOVE_MAX_MS, p.seatMoveMs)
        assertEquals(100, p.lightBrightness)
    }

    @Test
    fun `null 칸은 보정하지 않고 그대로`() {
        assertEquals(Climate(seatHeat = 2, temperature = null), ProfileRules.validate(Climate(seatHeat = 2)))
    }

    // ───────────── 학습 (#5) ─────────────

    @Test
    fun `7월에 21도로 바꾸면 여름 온도만 바뀜`() = runBlocking {
        val r = repo()
        val before = r.get(1)!!
        r.learn(1, Season.SUMMER, Climate(temperature = 21f))
        val after = r.get(1)!!
        assertEquals(Climate(seatHeat = -2, temperature = 21f), after.summer)
        assertEquals(before.winter, after.winter)
    }

    @Test
    fun `바꾼 칸이 없으면 아무것도 안 바뀜`() = runBlocking {
        val r = repo()
        val before = r.get(1)!!
        r.learn(1, Season.SUMMER, Climate())
        assertEquals(before, r.get(1))
    }

    @Test
    fun `빈 계절 칸은 학습한 값으로 새로 채움`() = runBlocking {
        val r = repo()
        r.learn(2, Season.SUMMER, Climate(temperature = 23f))
        assertEquals(Climate(seatHeat = null, temperature = 23f), r.get(2)!!.summer)
    }

    // ───────────── 저장 (#2) ─────────────

    @Test
    fun `같은 id로 저장하면 덮어쓰기`() = runBlocking {
        val r = repo()
        r.save(r.get(1)!!.copy(specialGreetingText = null, greetingText = "다녀오셨어요"))
        assertEquals("다녀오셨어요", r.get(1)!!.greetingText)
        assertEquals(3, r.getAll().size)
    }

    @Test
    fun `삭제하면 사라짐`() = runBlocking {
        val r = repo()
        r.delete(3)
        assertNull(r.get(3))
    }
}