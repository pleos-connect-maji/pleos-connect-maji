package com.maji.control

/**
 * B 차량 제어의 계산 규칙만 모아 둔 곳 (SDK·안드로이드에 의존하지 않아서 단위 테스트로 확인할 수 있어요).
 * 실제 차 명령은 PleosVehicleControl이 보내고, "얼마나·어디까지"는 여기서 정해요.
 */
object ControlRules {
    // 값 범위 (Pleos 에뮬레이터 기준, D의 검증 범위와 같게)
    const val MAX_SEAT_MOVE_MS = 10_000 // 끝→끝 약 10초로 추정 (전동시트 약 25cm, 초당 2~2.5cm)
    const val MIN_HEAT = -3
    const val MAX_HEAT = 3
    const val MIN_TEMP = 15f
    const val MAX_TEMP = 30f

    fun clampSeatMove(ms: Int): Int = ms.coerceIn(-MAX_SEAT_MOVE_MS, MAX_SEAT_MOVE_MS)
    fun clampHeat(level: Int): Int = level.coerceIn(MIN_HEAT, MAX_HEAT)
    fun clampTemperature(celsius: Float): Float = celsius.coerceIn(MIN_TEMP, MAX_TEMP)

    /** 앱 기준 밝기 0..100 → 차가 알려준 min..max 범위로 비례 변환 */
    fun scaleBrightness(percent: Int, min: Int, max: Int): Int =
        min + (max - min) * percent.coerceIn(0, 100) / 100

    /**
     * 제어 가능 여부 (#13)
     * - 자리 준비 명령이 전부 실패한 적이 있으면 불가
     * - 아니면 시트·열선·온도 중 하나라도 "된다(true)" 또는 "아직 확인 중(null)"이면 일단 시도
     */
    fun isControlAvailable(caps: List<Boolean?>, allPrepareFailed: Boolean): Boolean =
        !allPrepareFailed && caps.any { it != false }
}

/**
 * 시트 이동 시간 기록 (#11, #14, #46)
 * SDK에 시트 "위치"가 없어서, 출발~정지 사이의 실제 시간을 재서 원래 자리에서 얼마나 움직였는지(offsetMs) 쌓아요.
 * 시간(now)은 밖에서 넣어 줘요 → 테스트에서 시계를 마음대로 정할 수 있어요.
 */
class SeatMoveTracker(private val maxMs: Int = ControlRules.MAX_SEAT_MOVE_MS) {

    /** 원래 자리에서 지금까지 움직인 합계 (+앞, -뒤) */
    var offsetMs = 0
        private set

    private var startedAt: Long? = null
    private var forward = true

    val isMoving: Boolean get() = startedAt != null

    fun start(forward: Boolean, now: Long) {
        this.forward = forward
        startedAt = now
    }

    /** 멈춘 순간 호출. 이번에 움직인 시간(+앞, -뒤)을 돌려주고 합계에 더해요. 안 움직이는 중이면 0 */
    fun stop(now: Long): Int {
        val started = startedAt ?: return 0
        startedAt = null
        val elapsed = (now - started).coerceAtLeast(0).toInt()
        val moved = if (forward) elapsed else -elapsed
        offsetMs = (offsetMs + moved).coerceIn(-maxMs, maxMs)
        return moved
    }

    /** 원래 자리로 가려면 움직여야 하는 시간 (+앞, -뒤) */
    fun restorePlanMs(): Int = -offsetMs
}
