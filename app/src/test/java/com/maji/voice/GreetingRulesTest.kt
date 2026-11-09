package com.maji.voice

import com.maji.contract.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GreetingRulesTest {

    private val defaults = listOf("오늘도 고생 많았어요", "어서 와요")
    private val mom = Profile(name = "엄마")

    @Test
    fun 오늘만의_인사가_제일_먼저() {
        val p = mom.copy(specialGreetingText = "생일 축하해!", greetingText = "다녀오셨어요")
        assertEquals("생일 축하해!", GreetingRules.textFor(p, defaults))
    }

    @Test
    fun 오늘만의_인사가_없으면_고른_인사() {
        val p = mom.copy(greetingText = "다녀오셨어요")
        assertEquals("다녀오셨어요", GreetingRules.textFor(p, defaults))
    }

    @Test
    fun 둘_다_없으면_기본_목록_첫_번째() {
        assertEquals("오늘도 고생 많았어요", GreetingRules.textFor(mom, defaults))
    }

    @Test
    fun 공백뿐인_칸은_건너뛰고_앞뒤_공백은_자름() {
        val p = mom.copy(specialGreetingText = "   ", greetingText = "  다녀오셨어요 ")
        assertEquals("다녀오셨어요", GreetingRules.textFor(p, defaults))
    }

    @Test
    fun 읽을_문장이_전혀_없으면_null() {
        assertNull(GreetingRules.textFor(mom, emptyList()))
    }

    @Test
    fun 대체_파일은_greetingAudio_다음_기본_파일() {
        val p = mom.copy(greetingAudio = "/data/mom.m4a")
        assertEquals(
            listOf("/data/mom.m4a", GreetingRules.DEFAULT_GREETING_AUDIO),
            GreetingRules.fallbackAudiosFor(p)
        )
    }

    @Test
    fun greetingAudio가_없거나_비면_기본_파일만() {
        assertEquals(listOf(GreetingRules.DEFAULT_GREETING_AUDIO), GreetingRules.fallbackAudiosFor(mom))
        assertEquals(
            listOf(GreetingRules.DEFAULT_GREETING_AUDIO),
            GreetingRules.fallbackAudiosFor(mom.copy(greetingAudio = " "))
        )
    }
}
