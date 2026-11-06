package com.maji.profile

import com.maji.contract.Climate
import com.maji.contract.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// DB 저장 형식 바꾸기 테스트 (#37). Profile → DB 한 줄 → Profile 했을 때 똑같이 돌아오는지
class ProfileEntityTest {

    @Test
    fun `예시 탑승자 3명이 그대로 돌아옴`() {
        FakeProfileRepository.samples().forEachIndexed { i, p ->
            val withId = p.copy(id = i + 1L, place = "서울역", specialGreetingText = "생일 축하해")
            assertEquals(withId, withId.toEntity().toProfile())
        }
    }

    @Test
    fun `설정 안 한 계절은 null로 돌아옴`() {
        val p = Profile(name = "여자친구")
        val back = p.toEntity().toProfile()
        assertNull(back.winter)
        assertNull(back.summer)
    }

    @Test
    fun `열선 온도 둘 다 그대로 두기여도 설정한 계절로 남음`() {
        val p = Profile(name = "딸", winter = Climate(seatHeat = null, temperature = null))
        assertEquals(Climate(null, null), p.toEntity().toProfile().winter)
    }
}