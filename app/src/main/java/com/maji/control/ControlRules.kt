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

    // 이보다 작은 시트 차이는 타이머 오차로 보고 되돌리지 않아요 (에뮬레이터 오차 4~7ms)
    const val SEAT_RESTORE_TOLERANCE_MS = 100

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

    /**
     * 열선·통풍을 Contract 약속(-3..3, 음수 = 통풍) 하나로 합쳐요.
     * 통풍이 따로 있는 차: 열선 0 + 통풍 2 → -2. A의 PleosVehicleSignals와 같은 규칙이에요.
     */
    fun mergeHeatVent(heat: Int?, vent: Int?): Int? = when {
        heat == null && vent == null -> null
        heat != null && heat != 0 -> heat   // 열선 값이 있으면 그대로 (음수면 이미 통풍)
        vent != null && vent > 0 -> -vent   // 통풍이 따로 켜져 있으면 음수로
        else -> 0
    }

    /** 합친 값(-3..3)을 통풍이 따로 있는 차용으로 나눠요: (열선, 통풍) */
    fun splitHeatVent(level: Int): Pair<Int, Int> =
        if (level >= 0) level to 0 else 0 to -level
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
    private var currentToken = 0
    private var currentManual = false

    // 이미 기록한 이동 (출발이 나중에 거절되면 되돌리려고 기억해요). 출발 번호 → 기록한 시간
    private val recorded = mutableMapOf<Int, Int>()

    // ◀▶(직접 조절) 중에 움직인 시간. 문 열림·자동 정지로 먼저 멈춰도 뗄 때 한 번에 돌려줘요
    private var manualMs = 0

    val isMoving: Boolean get() = startedAt != null

    /** 출발. 번호를 돌려줘요 → SDK가 나중에 "출발 거절"을 알리면 그 번호로 reject해요 */
    fun start(forward: Boolean, now: Long, manual: Boolean = false): Int {
        this.forward = forward
        startedAt = now
        currentManual = manual
        if (manual) manualMs = 0
        return ++currentToken
    }

    /** 멈춘 순간 호출. 이번에 움직인 시간(+앞, -뒤)을 돌려주고 합계에 더해요. 안 움직이는 중이면 0 */
    fun stop(now: Long): Int {
        val started = startedAt ?: return 0
        startedAt = null
        val elapsed = (now - started).coerceAtLeast(0).toInt()
        val moved = if (forward) elapsed else -elapsed
        offsetMs = (offsetMs + moved).coerceIn(-maxMs, maxMs)
        recorded[currentToken] = moved
        if (currentManual) manualMs += moved
        return moved
    }

    /**
     * SDK가 출발을 거절했을 때. 실제로는 안 움직였으니 기록에서 빼요.
     * - 아직 움직이는 중으로 알고 있으면: 기록 없이 멈춘 걸로
     * - 이미 멈춰서 기록했으면: 기록한 만큼 되돌려요
     * - 다른(지난) 출발 번호면: 그 기록만 되돌려요
     */
    fun reject(token: Int) {
        if (token == currentToken && startedAt != null) {
            startedAt = null
            return
        }
        val moved = recorded.remove(token) ?: return
        offsetMs = (offsetMs - moved).coerceIn(-maxMs, maxMs)
        if (token == currentToken && currentManual) manualMs -= moved
    }

    /** ◀▶를 뗄 때. 이번 누름 동안 움직인 시간(+앞, -뒤)을 한 번만 돌려줘요 */
    fun takeManualMs(): Int {
        val ms = manualMs
        manualMs = 0
        currentManual = false
        return ms
    }

    /**
     * 원래 자리로 가려면 움직여야 하는 시간 (+앞, -뒤).
     * 남은 차이가 아주 작으면(타이머 오차 수준) 0 → restore를 여러 번 불러도 시트가 들썩이지 않아요.
     */
    fun restorePlanMs(): Int =
        if (kotlin.math.abs(offsetMs) < ControlRules.SEAT_RESTORE_TOLERANCE_MS) 0 else -offsetMs

    /**
     * 앱을 다시 켰을 때 저장해 둔 기록을 되살려요 (#26).
     * 지난 출발·직접 조절 기록은 이전 실행의 것이라 모두 비워요.
     */
    fun load(savedOffsetMs: Int) {
        offsetMs = savedOffsetMs.coerceIn(-maxMs, maxMs)
        startedAt = null
        recorded.clear()
        manualMs = 0
        currentManual = false
    }
}
