package com.maji.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.maji.R
import com.maji.contract.Greeter
import com.maji.contract.Profile

/**
 * E. 음성 (#15 인사, #18 안내음)
 *
 * 공통: 문장을 TTS로 읽고, TTS가 초기화·말하기에 실패하거나 [startTimeoutMs] 안에 시작하지 않으면 파일을 틀어요.
 *       말하기가 시작됐는데 끝 신호가 안 오면 [maxSpeakMs] 뒤에 끝난 것으로 봐요.
 *       모든 함수는 메인 스레드에서 부르고, onDone도 메인 스레드에서 불러요.
 *
 * playGreeting (#15)
 * - 문장은 GreetingRules.textFor 순서, 대체 파일은 greetingAudio → 기본 파일 순서예요.
 * - 성공, 대체 파일, 전부 실패 어느 경우든 onDone을 딱 한 번 불러요. → C가 노래와 오늘만의 인사 비우기를 이어 가요.
 * - 재생 중이던 안내음이나 앞의 인사는 조용히 끊고(앞의 onDone 안 부름) 새 인사를 읽어요.
 *
 * playNotice (#18)
 * - audio는 assets 경로예요. Notices 목록에 있으면 그 문장을 TTS로 읽고, 실패하면 그 파일을 틀어요.
 *   목록에 없으면 파일만 틀어요.
 * - 인사 중에 불리면 건너뛰어요 (인사를 끊지 않아요). 안내음 중에 불리면 새 안내음으로 바꿔요.
 *
 * playSong (#16)
 * - songUri를 한 번만 재생해요 (반복 없음). null이거나 비어 있으면 건너뛰어요.
 * - 인사·안내음과 다른 플레이어(음악용)로 틀어요. 노래 중에 또 불리면 새 노래로 바꿔요.
 * - 새 인사가 시작되면 노래는 멈춰요. 안내음은 노래와 같이 나와요 (소리 낮추기는 #17).
 * - 실패해도 로그만 남겨요.
 *
 * stopAll(): 노래까지 전부 멈추고 onDone은 부르지 않아요. (맞이 취소 때 노래가 나오거나 오늘만의 인사가 지워지지 않게)
 *
 * 오디오 포커스(#17)는 아직 없어요.
 */
class PleosGreeter(
    private val speaker: Speaker,
    private val player: AudioPlayer,
    private val songPlayer: AudioPlayer,
    private val scheduler: Scheduler,
    private val defaultGreetings: List<String>,
    private val log: (String) -> Unit = {},
    private val startTimeoutMs: Long = START_TIMEOUT_MS,
    private val maxSpeakMs: Long = MAX_SPEAK_MS
) : Greeter {

    private enum class Kind(val label: String) { GREETING("인사"), NOTICE("안내음") }

    // 재생할 때마다 1씩 올려요. 콜백이 왔을 때 번호가 다르면 이미 멈춘(지난) 재생이라 무시해요.
    private var session = 0
    private var playing: Kind? = null
    private var songSession = 0
    private var cancelTimer: (() -> Unit)? = null

    override fun playGreeting(profile: Profile, onDone: () -> Unit) {
        stopSong()
        speakOrPlay(
            kind = Kind.GREETING,
            text = GreetingRules.textFor(profile, defaultGreetings),
            files = GreetingRules.fallbackAudiosFor(profile),
            onFinish = { how ->
                log("인사 끝 ($how) → onDone")
                onDone()
            }
        )
    }

    override fun playSong(profile: Profile) {
        val uri = profile.songUri?.trim()
        if (uri.isNullOrEmpty()) {
            log("노래 없음 (songUri 비어 있음) → 건너뜀")
            return
        }
        val id = ++songSession
        log("노래 재생: $uri")
        songPlayer.play(
            uri,
            onDone = { if (id == songSession) log("노래 끝: $uri") },
            onError = { reason -> if (id == songSession) log("노래 재생 실패: $uri ($reason)") }
        )
    }

    override fun playNotice(audio: String) {
        val path = audio.trim()
        if (path.isEmpty()) {
            log("playNotice: 빈 경로라 건너뜀")
            return
        }
        if (playing == Kind.GREETING) {
            log("인사 중이라 안내음 건너뜀: $path")
            return
        }
        speakOrPlay(
            kind = Kind.NOTICE,
            text = Notices.textFor(path),
            files = listOf(path),
            onFinish = { how -> log("안내음 끝 ($how): $path") }
        )
    }

    /** 전부 멈춰요. 진행 중이던 인사의 onDone은 부르지 않아요 */
    override fun stopAll() {
        log("stopAll")
        startNewSession()
        stopSong()
    }

    // TTS로 읽고, 안 되면 files를 앞에서부터 틀어요. 어떤 길로 가든 onFinish는 한 번 (stopAll·새 재생으로 끊기면 안 불림)
    private fun speakOrPlay(kind: Kind, text: String?, files: List<String>, onFinish: (how: String) -> Unit) {
        val id = startNewSession()
        playing = kind
        var finished = false
        var usingFile = false // 파일로 넘어간 뒤에는 늦게 온 TTS 신호를 무시해요

        fun isCurrent() = id == session && !finished
        fun isSpeaking() = isCurrent() && !usingFile

        fun finish(how: String) {
            if (!isCurrent()) return
            finished = true
            playing = null
            clearTimer()
            onFinish(how)
        }

        fun playFile(index: Int) {
            if (!isCurrent()) return
            if (index >= files.size) {
                finish("파일도 전부 실패")
                return
            }
            val file = files[index]
            log("${kind.label} 파일 재생: $file")
            player.play(
                file,
                onDone = { if (id == session) finish("파일 $file") },
                onError = { reason ->
                    if (id == session) {
                        log("파일 실패: $file ($reason)")
                        playFile(index + 1)
                    }
                }
            )
        }

        fun fallBack(reason: String) {
            if (!isSpeaking()) return
            usingFile = true
            clearTimer()
            log("TTS 안 됨 ($reason) → 파일")
            speaker.stop()
            playFile(0)
        }

        if (text == null) {
            fallBack("읽을 문장 없음")
            return
        }

        log("TTS ${kind.label}: \"$text\"")
        setTimer(startTimeoutMs) { fallBack("${startTimeoutMs}ms 안에 시작 안 함") }
        speaker.speak(text, object : Speaker.Listener {
            override fun onStart() {
                if (!isSpeaking()) return
                // 시작했으니 '시작 안 함' 시간 제한을 '최대 길이' 제한으로 바꿔요
                setTimer(maxSpeakMs) {
                    log("${maxSpeakMs}ms 지나도 끝 신호 없음 → 끝난 것으로 봄")
                    speaker.stop()
                    finish("TTS 시간 초과")
                }
            }

            override fun onDone() {
                if (isSpeaking()) finish("TTS")
            }

            override fun onError(reason: String) {
                fallBack(reason)
            }
        })
    }

    /** 다 쓰고 나면 정리 (Contract에는 없는 추가 함수예요. 화면이 사라질 때 불러요) */
    fun release() {
        stopAll()
        speaker.release()
    }

    private fun stopSong() {
        songSession++
        songPlayer.stop()
    }

    // 지난 재생을 조용히 멈추고 새 번호를 받아요
    private fun startNewSession(): Int {
        session++
        playing = null
        clearTimer()
        speaker.stop()
        player.stop()
        return session
    }

    private fun setTimer(delayMs: Long, block: () -> Unit) {
        clearTimer()
        cancelTimer = scheduler.postDelayed(delayMs, block)
    }

    private fun clearTimer() {
        cancelTimer?.invoke()
        cancelTimer = null
    }

    companion object {
        private const val TAG = "MajiVoice"
        const val START_TIMEOUT_MS = 3_000L
        const val MAX_SPEAK_MS = 15_000L

        /** 앱에서 쓸 진짜 Greeter. 메인 스레드에서 만들어 주세요 */
        fun create(context: Context): PleosGreeter {
            val app = context.applicationContext
            val log: (String) -> Unit = { Log.d(TAG, it) }
            return PleosGreeter(
                speaker = PleosTtsSpeaker(app, log = log),
                player = MediaAudioPlayer(app, log = log),
                songPlayer = MediaAudioPlayer(
                    app,
                    usage = AudioAttributes.USAGE_MEDIA,
                    contentType = AudioAttributes.CONTENT_TYPE_MUSIC,
                    log = log
                ),
                scheduler = MainScheduler(),
                defaultGreetings = app.resources.getStringArray(R.array.greeting_presets).toList(),
                log = log
            )
        }
    }
}

/** 메인 스레드 시간 제한 */
class MainScheduler : Scheduler {
    private val handler = Handler(Looper.getMainLooper())

    override fun postDelayed(delayMs: Long, block: () -> Unit): () -> Unit {
        val r = Runnable { block() }
        handler.postDelayed(r, delayMs)
        return { handler.removeCallbacks(r) }
    }
}
