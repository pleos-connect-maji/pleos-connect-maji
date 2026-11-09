package com.maji.voice

// 문장을 소리로 읽는 부분. 진짜는 PleosTtsSpeaker, 테스트에서는 가짜로 바꿔 끼워요.
// 리스너는 항상 메인 스레드에서 불러야 해요.
interface Speaker {
    fun speak(text: String, listener: Listener)

    /** 지금 읽는 것을 멈춰요. 멈춘 뒤에는 리스너를 부르지 않아요 */
    fun stop()

    /** 다 쓰고 나면 정리 */
    fun release()

    interface Listener {
        fun onStart()
        fun onDone()
        fun onError(reason: String)
    }
}

// 음성 파일 재생. 진짜는 MediaAudioPlayer, 테스트에서는 가짜.
// 콜백은 메인 스레드에서 불러야 해요.
interface AudioPlayer {
    /** source: assets 경로("audio/x.mp3"), 파일 경로("/sdcard/..."), 또는 "content://", "file://" 주소 */
    fun play(source: String, onDone: () -> Unit, onError: (String) -> Unit)

    /** 멈춘 뒤에는 콜백을 부르지 않아요 */
    fun stop()
}

// 시간 제한용. 테스트에서는 시간을 직접 넘겨요.
fun interface Scheduler {
    /** delayMs 뒤에 block 실행. 돌려받은 함수를 부르면 취소 */
    fun postDelayed(delayMs: Long, block: () -> Unit): () -> Unit
}
