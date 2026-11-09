package com.maji.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

/**
 * 음성 파일 재생 (MediaPlayer). 한 번에 하나만 재생하고, 새로 틀면 앞의 것은 조용히 멈춰요.
 * 메인 스레드에서 불러 주세요. (MediaPlayer 콜백도 메인 스레드로 와요)
 */
class MediaAudioPlayer(
    context: Context,
    private val usage: Int = AudioAttributes.USAGE_ASSISTANT,
    private val contentType: Int = AudioAttributes.CONTENT_TYPE_SPEECH,
    private val log: (String) -> Unit = {}
) : AudioPlayer {

    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null
    private var prepared = false
    private var volume = 1f // 준비 중에는 볼륨을 못 바꿔서 기억해 뒀다가 준비되면 적용해요

    override fun play(source: String, onDone: () -> Unit, onError: (String) -> Unit) {
        stop()
        val mp = MediaPlayer()
        player = mp
        prepared = false
        volume = 1f
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder().setUsage(usage).setContentType(contentType).build()
            )
            setSource(mp, source)
            mp.isLooping = false // 한 번만 재생 (#16)
            mp.setOnPreparedListener {
                if (player === mp) {
                    prepared = true
                    mp.setVolume(volume, volume)
                    mp.start()
                }
            }
            mp.setOnCompletionListener {
                if (player === mp) {
                    releasePlayer()
                    onDone()
                }
            }
            mp.setOnErrorListener { _, what, extra ->
                if (player === mp) {
                    releasePlayer()
                    onError("MediaPlayer 오류 what=$what extra=$extra")
                }
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            log("파일 열기 실패: $source (${e.javaClass.simpleName}: ${e.message})")
            releasePlayer()
            onError(e.javaClass.simpleName)
        }
    }

    override fun stop() {
        releasePlayer()
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        val mp = player ?: return
        if (!prepared) return
        try {
            mp.setVolume(this.volume, this.volume)
        } catch (_: IllegalStateException) {
        }
    }

    private fun setSource(mp: MediaPlayer, source: String) {
        when {
            source.startsWith("content://") || source.startsWith("file://") ||
                source.startsWith("http://") || source.startsWith("https://") ->
                mp.setDataSource(appContext, Uri.parse(source))
            source.startsWith("/") -> mp.setDataSource(source)
            else -> appContext.assets.openFd(source).use { fd ->
                mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
        }
    }

    private fun releasePlayer() {
        val mp = player ?: return
        player = null
        prepared = false
        try {
            mp.reset()
        } catch (_: Exception) {
        }
        mp.release()
    }
}
