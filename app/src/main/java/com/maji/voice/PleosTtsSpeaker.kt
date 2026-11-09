package com.maji.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.Locale

/**
 * Pleos TextToSpeech SDK 3.3.1로 문장 읽기.
 *
 * - [modes]를 앞에서부터 시도해요. 초기화에 실패하거나 말하기 시작 전에 오류가 나면 다음 모드로 넘어가요.
 * - 실패한 모드는 앱이 켜져 있는 동안 다시 시도하지 않아요. 모든 모드가 실패하면 바로 onError를 불러서
 *   PleosGreeter가 대체 파일로 넘어가게 해요.
 * - SERVER 모드는 registerApp 인증이 필요할 수 있어서 기본 목록에서 뺐어요.
 * - SDK 콜백은 다른 스레드에서 올 수 있어서 전부 메인 스레드로 옮겨서 리스너를 불러요.
 */
class PleosTtsSpeaker(
    context: Context,
    private val modes: List<Mode> = listOf(Mode.ON_DEVICE, Mode.HYBRID),
    private val log: (String) -> Unit = {}
) : Speaker {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var ready = false
    private var modeIndex = 0

    // 지금 읽는 요청
    private var listener: Speaker.Listener? = null
    private var text: String? = null
    private var started = false

    private val events = object : EventListener {
        override fun onReady() {}
        override fun onUpdatedRms(rms: Double) {}

        override fun onStart() {
            main.post {
                val l = listener ?: return@post
                if (started) return@post
                started = true
                log("TTS 시작 (${currentModeName()})")
                l.onStart()
            }
        }

        override fun onDone() {
            main.post {
                val l = listener ?: return@post
                clearRequest()
                log("TTS 끝 (${currentModeName()})")
                l.onDone()
            }
        }

        override fun onError(error: ErrorType) {
            main.post { handleError(error.name) }
        }

        override fun onStop() {}
    }

    override fun speak(text: String, listener: Speaker.Listener) {
        this.listener = listener
        this.text = text
        started = false
        attempt()
    }

    override fun stop() {
        val wasSpeaking = listener != null
        clearRequest()
        if (wasSpeaking) {
            try {
                tts?.stop()
            } catch (e: Exception) {
                log("TTS stop 예외: ${e.message}")
            }
        }
    }

    override fun release() {
        clearRequest()
        releaseEngine()
    }

    // 지금 모드로 읽기를 시도해요. 엔진이 없으면 만들고 초기화부터 해요.
    private fun attempt() {
        val l = listener ?: return
        val t = text ?: return
        if (modeIndex >= modes.size) {
            clearRequest()
            l.onError("TTS 모드 전부 실패")
            return
        }
        val engine = tts
        if (engine != null && ready) {
            try {
                engine.speak(t, true)
            } catch (e: Exception) {
                nextMode("speak 예외 ${e.javaClass.simpleName}: ${e.message}")
            }
            return
        }
        if (engine != null) return // 초기화 중이에요. 끝나면 다시 attempt()

        val mode = modes[modeIndex]
        try {
            val created = TextToSpeech(appContext, mode)
            tts = created
            ready = false
            log("TTS 초기화 시작 ($mode)")
            // SDK 내부 엔진은 initialize()에서 만들어져요. 그래서 리스너 등록과 언어 설정은 그 뒤에 해요.
            created.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        if (tts !== created) return@post
                        log("TTS 초기화 성공 ($mode)")
                        try {
                            created.setLocale(Locale.KOREA) // "ko-KR". 국가 없이 "ko"만 주면 엔진이 "unsupported locale: ko-" 오류를 내요
                        } catch (e: Exception) {
                            log("TTS 언어 설정 실패, 기본 언어로 읽어요: ${e.message}")
                        }
                        ready = true
                        attempt()
                    }
                }

                override fun onFailure(error: ErrorType) {
                    main.post {
                        if (tts === created) nextMode("초기화 실패 $error")
                    }
                }
            })
            created.addEventListener(events)
        } catch (e: Exception) {
            nextMode("초기화 예외 ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun handleError(reason: String) {
        val l = listener ?: return
        if (!started) {
            // 아직 소리가 안 났으면 다음 모드로 다시 시도
            nextMode("말하기 오류 $reason")
        } else {
            // 말하다가 오류 → 이번에는 대체 파일로 넘기고, 다음부터는 다음 모드를 써요
            clearRequest()
            log("말하는 중 오류 $reason (${currentModeName()}) → 다음부터 다음 모드")
            releaseEngine()
            modeIndex++
            l.onError(reason)
        }
    }

    private fun nextMode(reason: String) {
        log("TTS ${currentModeName()} 실패: $reason")
        releaseEngine()
        modeIndex++
        started = false
        attempt()
    }

    private fun clearRequest() {
        listener = null
        text = null
        started = false
    }

    private fun releaseEngine() {
        val engine = tts ?: return
        tts = null
        ready = false
        try {
            engine.removeEventListener(events)
            engine.release()
        } catch (e: Exception) {
            log("TTS release 예외: ${e.message}")
        }
    }

    private fun currentModeName(): String = modes.getOrNull(modeIndex)?.name ?: "없음"
}
