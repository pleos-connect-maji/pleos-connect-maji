package com.maji.voice

// 안내음 목록 (#18). playNotice(Notices.PREPARE)처럼 써요.
// 경로는 assets 기준이고, 같은 이름의 파일이 assets/audio/에 있어요 (TTS가 안 될 때 틀어요).
object Notices {
    /** 자리 준비 시작 (C가 PREPARING에서 부름) */
    const val PREPARE = "audio/prepare_notice.mp3"

    /** 자리 준비 끝 */
    const val PREPARE_DONE = "audio/prepare_done.mp3"

    private val texts = mapOf(
        PREPARE to "자리 맞춰 둘게요",
        PREPARE_DONE to "자리 준비 다 됐어요"
    )

    /** TTS로 읽을 문장. 목록에 없는 경로면 null → 파일만 틀어요 */
    fun textFor(audio: String): String? = texts[audio.trim()]
}
