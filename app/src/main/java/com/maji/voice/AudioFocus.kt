package com.maji.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

// 오디오 포커스 (#17). 진짜는 AndroidAudioFocus, 테스트에서는 가짜.
interface AudioFocus {
    /** 인사·안내음: 다른 앱 소리를 잠깐 낮춰요. 거절되면 false (그래도 재생은 해요) */
    fun requestSpeech(): Boolean
    fun abandonSpeech()

    /** 노래: 다른 앱 음악을 잠깐 멈춰요. 전화·내비 등에 포커스를 뺏기면 onLost (메인 스레드) */
    fun requestSong(onLost: () -> Unit): Boolean
    fun abandonSong()
}

/**
 * AudioManager 포커스.
 * - 인사·안내음: AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK → 다른 앱 음악이 작아졌다가 반환하면 돌아와요
 * - 노래: AUDIOFOCUS_GAIN_TRANSIENT → 다른 앱 음악이 멈췄다가 노래가 끝나면 다시 나와요
 */
class AndroidAudioFocus(
    context: Context,
    private val log: (String) -> Unit = {}
) : AudioFocus {

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private var speechHeld = false
    private var songHeld = false
    private var onSongLost: (() -> Unit)? = null

    private val speechRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener({ change -> log("인사 포커스 변화: $change") }, main)
        .build()

    private val songRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setWillPauseWhenDucked(false) // 잠깐 낮춰야 할 때는 시스템이 알아서 낮추게 둬요
        .setOnAudioFocusChangeListener({ change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    log("노래 포커스 잃음 ($change)")
                    songHeld = false
                    onSongLost?.also { onSongLost = null }?.invoke()
                }
                else -> log("노래 포커스 변화: $change")
            }
        }, main)
        .build()

    override fun requestSpeech(): Boolean {
        val granted = audioManager.requestAudioFocus(speechRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        speechHeld = granted
        log("인사 포커스 요청 → ${if (granted) "받음" else "거절"}")
        return granted
    }

    override fun abandonSpeech() {
        if (!speechHeld) return
        speechHeld = false
        audioManager.abandonAudioFocusRequest(speechRequest)
        log("인사 포커스 반환")
    }

    override fun requestSong(onLost: () -> Unit): Boolean {
        onSongLost = onLost
        val granted = audioManager.requestAudioFocus(songRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        songHeld = granted
        log("노래 포커스 요청 → ${if (granted) "받음" else "거절"}")
        return granted
    }

    override fun abandonSong() {
        onSongLost = null
        if (!songHeld) return
        songHeld = false
        audioManager.abandonAudioFocusRequest(songRequest)
        log("노래 포커스 반환")
    }
}
