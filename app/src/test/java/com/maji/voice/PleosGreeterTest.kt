package com.maji.voice

import com.maji.contract.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// 인사 재생 규칙 (#15): onDone은 딱 한 번, stopAll 뒤에는 안 불림, 시간 제한, 대체 파일 순서
class PleosGreeterTest {

    private val speaker = FakeSpeaker()
    private val player = FakePlayer()
    private val clock = FakeScheduler()
    private val greeter = PleosGreeter(
        speaker = speaker,
        player = player,
        scheduler = clock,
        defaultGreetings = listOf("오늘도 고생 많았어요"),
        startTimeoutMs = 3_000,
        maxSpeakMs = 15_000
    )
    private val mom = Profile(id = 1, name = "엄마", greetingText = "다녀오셨어요")
    private var doneCount = 0
    private val onDone: () -> Unit = { doneCount++ }

    @Test
    fun TTS가_끝나면_onDone_한_번() {
        greeter.playGreeting(mom, onDone)
        assertEquals("다녀오셨어요", speaker.lastText)
        speaker.start()
        speaker.done()
        speaker.done() // 끝 신호가 두 번 와도
        clock.advance(60_000)
        assertEquals(1, doneCount)
        assertTrue(player.played.isEmpty())
    }

    @Test
    fun TTS_오류면_기본_파일을_틀고_끝나면_onDone() {
        greeter.playGreeting(mom, onDone)
        speaker.error("ON_DEVICE_ENGINE_ERROR")
        assertEquals(listOf(GreetingRules.DEFAULT_GREETING_AUDIO), player.played)
        assertEquals(0, doneCount)
        player.complete()
        assertEquals(1, doneCount)
    }

    @Test
    fun TTS가_3초_안에_시작_안_하면_파일로() {
        greeter.playGreeting(mom, onDone)
        clock.advance(2_999)
        assertTrue(player.played.isEmpty())
        clock.advance(1)
        assertEquals(listOf(GreetingRules.DEFAULT_GREETING_AUDIO), player.played)
        assertTrue(speaker.stopCount > 0)
        // 늦게 TTS가 끝났다고 와도 무시
        speaker.done()
        assertEquals(0, doneCount)
        player.complete()
        assertEquals(1, doneCount)
    }

    @Test
    fun 시작했는데_끝_신호가_없으면_15초_뒤_onDone() {
        greeter.playGreeting(mom, onDone)
        speaker.start()
        clock.advance(14_999)
        assertEquals(0, doneCount)
        clock.advance(1)
        assertEquals(1, doneCount)
        assertTrue(player.played.isEmpty())
    }

    @Test
    fun greetingAudio가_실패하면_기본_파일_그것도_실패하면_그래도_onDone() {
        greeter.playGreeting(mom.copy(greetingAudio = "/data/mom.m4a"), onDone)
        speaker.error("UNKNOWN")
        assertEquals(listOf("/data/mom.m4a"), player.played)
        player.fail()
        assertEquals(listOf("/data/mom.m4a", GreetingRules.DEFAULT_GREETING_AUDIO), player.played)
        player.fail()
        assertEquals(1, doneCount)
    }

    @Test
    fun stopAll하면_onDone_안_불림() {
        greeter.playGreeting(mom, onDone)
        speaker.start()
        greeter.stopAll()
        speaker.done()
        clock.advance(60_000)
        assertEquals(0, doneCount)
        assertTrue(speaker.stopCount > 0)
    }

    @Test
    fun 파일_재생_중_stopAll해도_onDone_안_불림() {
        greeter.playGreeting(mom, onDone)
        speaker.error("UNKNOWN")
        greeter.stopAll()
        assertTrue(player.stopCount > 0)
        player.complete()
        assertEquals(0, doneCount)
    }

    @Test
    fun 인사_중에_다시_부르면_앞의_onDone은_안_불리고_새_인사만() {
        var firstDone = 0
        greeter.playGreeting(mom) { firstDone++ }
        greeter.playGreeting(mom.copy(specialGreetingText = "생일 축하해!"), onDone)
        assertEquals("생일 축하해!", speaker.lastText)
        speaker.start()
        speaker.done()
        assertEquals(0, firstDone)
        assertEquals(1, doneCount)
    }

    @Test
    fun 읽을_문장이_없으면_바로_파일() {
        val silent = PleosGreeter(speaker, player, clock, defaultGreetings = emptyList())
        silent.playGreeting(Profile(name = "아빠"), onDone)
        assertEquals(null, speaker.lastText)
        assertEquals(listOf(GreetingRules.DEFAULT_GREETING_AUDIO), player.played)
        player.complete()
        assertEquals(1, doneCount)
    }

    // ───────────── 가짜들 ─────────────

    private class FakeSpeaker : Speaker {
        var lastText: String? = null
        var stopCount = 0
        private var listener: Speaker.Listener? = null

        override fun speak(text: String, listener: Speaker.Listener) {
            lastText = text
            this.listener = listener
        }
        override fun stop() { stopCount++ }
        override fun release() {}

        // 진짜 SDK처럼 stop 뒤에도 늦은 콜백이 올 수 있다고 보고 리스너를 지우지 않아요
        fun start() = listener?.onStart()
        fun done() = listener?.onDone()
        fun error(reason: String) = listener?.onError(reason)
    }

    private class FakePlayer : AudioPlayer {
        val played = mutableListOf<String>()
        var stopCount = 0
        private var onDone: (() -> Unit)? = null
        private var onError: ((String) -> Unit)? = null

        override fun play(source: String, onDone: () -> Unit, onError: (String) -> Unit) {
            played.add(source)
            this.onDone = onDone
            this.onError = onError
        }
        override fun stop() { stopCount++ }

        fun complete() = onDone?.invoke()
        fun fail() = onError?.invoke("가짜 실패")
    }

    private class FakeScheduler : Scheduler {
        private var now = 0L
        private val tasks = mutableListOf<Pair<Long, () -> Unit>>()

        override fun postDelayed(delayMs: Long, block: () -> Unit): () -> Unit {
            val task = (now + delayMs) to block
            tasks.add(task)
            return { tasks.remove(task) }
        }

        fun advance(ms: Long) {
            val target = now + ms
            while (true) {
                val next = tasks.filter { it.first <= target }.minByOrNull { it.first } ?: break
                tasks.remove(next)
                now = next.first
                next.second()
            }
            now = target
        }
    }
}
