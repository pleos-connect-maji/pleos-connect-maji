package com.maji.control

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.MoveControl
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.maji.contract.Climate
import com.maji.contract.PassengerState
import com.maji.contract.Profile
import com.maji.contract.VehicleControl

/**
 * B. 차량 제어 (Pleos Vehicle SDK 2.3.2)
 *
 * 조수석 한 자리만 다뤄요: 시트 앞뒤, 열선(-3..3, 음수 = 통풍), 설정 온도, 창문, 무드등.
 * SDK 함수는 모두 결과를 나중에 콜백으로 줘요(비동기). 콜백은 다른 스레드에서 올 수 있어서
 * C에게 알려줄 때는 항상 메인 스레드로 옮겨서 불러요.
 */
class PleosVehicleControl(context: Context) : VehicleControl {

    private val vehicle = Vehicle(context.applicationContext).apply { initialize() }
    private val main = Handler(Looper.getMainLooper())

    // 조수석 = 1열 오른쪽
    private val passengerSeat = SeatArea(SeatValue.ROW_1_RIGHT)
    private val passengerWindow = WindowArea(WindowValue.ROW_1_RIGHT)

    // ───────── #13 제어 가능 여부 (대체 모드 판단) ─────────
    // 실차에서는 Vehicle Control이 아직 공개 전이라 제어가 막혀 있을 수 있어요.
    // 판단 기준: 핵심 3가지(시트 이동·열선·온도) 중 하나라도 제어 권한이 있으면 "된다".
    //   1) 만들 때 SDK에 미리 물어봐요 (capability)
    //   2) 확인 전이면 일단 "된다"로 봐요 → 시도해 보고, 자리 준비 명령이 전부 실패하면 "안 된다"로 바꿔요
    // false면 C가 진행 화면에 "직접 맞춰 주세요 + 맞출 값"을 보여줘요 (대체 모드).
    private var capSeatMove: Boolean? = null
    private var capSeatHeat: Boolean? = null
    private var capTemperature: Boolean? = null
    private var allPrepareFailed = false
    private var commandSucceeded = false // 이번 자리 준비에서 성공한 명령이 하나라도 있는지

    init {
        checkControlCapabilities()
    }

    override fun isControlAvailable(): Boolean {
        if (allPrepareFailed) return false
        val caps = listOf(capSeatMove, capSeatHeat, capTemperature)
        if (caps.any { it == null }) return true // 아직 확인 중 → 일단 시도
        return caps.any { it == true }
    }

    private fun checkControlCapabilities() {
        fun canControl(config: CapabilityConfig) =
            config.accessType == AccessType.WRITE || config.accessType == AccessType.READ_WRITE

        fun failed(name: String, e: Exception) {
            Log.w(TAG, "[#13] $name 제어 가능 여부 확인 실패: ${e.message}")
        }
        try {
            vehicle.getSeat().checkMoveSeatForeAftCapabilities(
                { config, areas ->
                    main.post { capSeatMove = canControl(config) && passengerSeat.isIn(areas); logCaps() }
                },
                { e -> failed("시트 이동", e) }
            )
            vehicle.getSeat().checkSeatTemperatureCapabilities(
                { config, bounds ->
                    main.post { capSeatHeat = canControl(config) && passengerSeat.isIn(bounds.keys); logCaps() }
                },
                { e -> failed("열선", e) }
            )
            vehicle.getHVAC().checkHvacTemperatureCapabilities(
                { config, bounds, _ ->
                    main.post { capTemperature = canControl(config) && passengerSeat.isIn(bounds.keys); logCaps() }
                },
                { e -> failed("온도", e) }
            )
        } catch (e: Exception) {
            failed("전체", e)
        }
    }

    // 지원 영역 목록에 조수석이 들어 있는지 (영역이 "1열 오른쪽+왼쪽"처럼 묶여 있을 수도 있어요)
    private fun SeatArea.isIn(areas: Collection<SeatArea>) =
        areas.any { a -> values.all { it in a.values } }

    private fun logCaps() {
        Log.d(TAG, "[#13] 제어 가능: 시트=$capSeatMove 열선=$capSeatHeat 온도=$capTemperature → ${isControlAvailable()}")
    }

    // ───────── #9 바꾸기 전 상태 읽기 ─────────
    // 열선·온도·창문 3개를 동시에 읽고, 3개가 다 끝나면 한 번만 알려줘요.
    // 실패한 칸은 null (에뮬레이터는 온도 읽기가 실패해요 → C가 A의 온도 신호로 채워요).
    // SDK가 콜백을 안 주는 경우를 대비해 READ_TIMEOUT_MS가 지나면 모인 값만으로 알려줘요.
    override fun readPassengerState(onResult: (PassengerState) -> Unit) {
        var heat: Int? = null
        var temperature: Float? = null
        var window: Int? = null
        var finished = 0
        var delivered = false

        fun deliver(reason: String) {
            if (delivered) return
            delivered = true
            val state = PassengerState(heat, temperature, window)
            Log.d(TAG, "[#9] 상태 읽기 끝($reason): $state")
            onResult(state)
        }

        // 읽기 하나가 끝날 때마다 메인 스레드에서 불러요
        fun oneDone() {
            finished++
            if (finished == 3) deliver("3개 완료")
        }

        main.postDelayed({ deliver("시간 초과") }, READ_TIMEOUT_MS)

        try {
            vehicle.getSeat().getHvacSeatTemperature(
                passengerSeat,
                { v -> main.post { heat = v; oneDone() } },
                { e -> main.post { Log.w(TAG, "[#9] 열선 읽기 실패: ${e.message}"); oneDone() } }
            )
            vehicle.getHVAC().getHvacTemperature(
                passengerSeat, TemperatureUnit.CELSIUS,
                { v -> main.post { temperature = v; oneDone() } },
                { e -> main.post { Log.w(TAG, "[#9] 온도 읽기 실패: ${e.message}"); oneDone() } }
            )
            vehicle.getWindow().getCurrentWindowPosition(
                passengerWindow,
                { v -> main.post { window = v; oneDone() } },
                { e -> main.post { Log.w(TAG, "[#9] 창문 읽기 실패: ${e.message}"); oneDone() } }
            )
        } catch (e: Exception) {
            // 차 서비스 연결 전 등으로 호출 자체가 실패하면 모인 값만으로 바로 알려줘요
            Log.w(TAG, "[#9] 상태 읽기 호출 실패: ${e.message}")
            main.post { deliver("호출 실패") }
        }
    }

    // ───────── #10 #11 자리 준비 ─────────
    // 명령 순서: 시트 → 열선 → 온도 (Contract 약속).
    // 시트는 움직이는 데 몇 초 걸리므로 멈출 때까지 기다리지 않고 열선·온도도 바로 보내요
    // (열선은 데워지는 데 시간이 걸려서 일찍 켤수록 좋아요).
    // onDone은 시트가 멈추고 + 열선·온도 명령이 끝났을 때 한 번만 불러요.
    override fun preparePassenger(climate: Climate, seatMoveMs: Int, onDone: () -> Unit) {
        Log.d(TAG, "[#10] 자리 준비 시작: $climate, seatMoveMs=$seatMoveMs")
        var waiting = 3 // 시트, 열선, 온도
        val tried = seatMoveMs != 0 || climate.seatHeat != null || climate.temperature != null
        commandSucceeded = false
        val partDone = {
            waiting--
            if (waiting == 0) {
                // 보낸 명령이 있는데 하나도 성공 못 했으면 → 제어가 막힌 차로 보고 대체 모드 (#13)
                if (tried && !commandSucceeded) {
                    allPrepareFailed = true
                    Log.w(TAG, "[#13] 자리 준비 명령이 전부 실패 → 대체 모드")
                }
                Log.d(TAG, "[#10] 자리 준비 끝")
                onDone()
            }
        }
        moveSeatFor(seatMoveMs, onStopped = partDone)
        applyHeat(climate.seatHeat, onDone = partDone)
        applyTemperature(climate.temperature, onDone = partDone)
    }

    // ───────── #11 시트 이동 · 안전 정지 ─────────
    // SDK에는 "어디로 가라"가 없어서 앞/뒤로 출발 → ms만큼 기다림 → STOP 으로 움직여요.
    // 실제로 움직인 시간을 seatOffsetMs에 쌓아 두고, restore(#14)에서 그만큼 반대로 움직여요.

    private var moveStartedAt = 0L        // 출발 시각 (SystemClock 기준), 0 = 안 움직이는 중
    private var moveForward = true
    private var seatOffsetMs = 0          // 원래 자리에서 지금까지 움직인 합계 (+앞, -뒤)
    private var afterSeatStop: (() -> Unit)? = null
    private val stopRunnable = Runnable { stopSeat() }

    private fun moveSeatFor(ms: Int, onStopped: () -> Unit) {
        val target = ms.coerceIn(-MAX_SEAT_MOVE_MS, MAX_SEAT_MOVE_MS)
        if (target == 0) {
            main.post(onStopped)
            return
        }
        if (!startSeat(forward = target > 0)) {
            main.post(onStopped) // 출발 실패 → 기다릴 것 없음
            return
        }
        afterSeatStop = onStopped
        main.postDelayed(stopRunnable, kotlin.math.abs(target).toLong())
    }

    /** 앞/뒤로 출발. 이미 움직이는 중이면 먼저 멈춰서 시간을 정산해요. */
    private fun startSeat(forward: Boolean): Boolean {
        if (moveStartedAt != 0L) stopSeat()
        return try {
            vehicle.getSeat().moveSeatForeAft(
                passengerSeat,
                if (forward) MoveControl.FORWARD else MoveControl.BACKWARD,
                { _, c -> main.post { commandSucceeded = true }; Log.d(TAG, "[#11] 시트 출발: $c") },
                { e -> Log.w(TAG, "[#11] 시트 출발 실패: ${e.message}") }
            )
            moveForward = forward
            moveStartedAt = SystemClock.elapsedRealtime()
            true
        } catch (e: Exception) {
            Log.w(TAG, "[#11] 시트 출발 호출 실패: ${e.message}")
            false
        }
    }

    /**
     * 시트 즉시 정지. 문이 열리면 C가 바로 불러요.
     * 움직이는 중이 아니어도 STOP을 보내요 (안전 명령은 한 번 더 보내도 손해가 없어요).
     */
    override fun stopSeat() {
        stopSeatAndMeasure()
    }

    /** STOP을 보내고, 이번에 움직인 시간(+앞, -뒤, 안 움직였으면 0)을 재서 돌려줘요. */
    private fun stopSeatAndMeasure(): Int {
        main.removeCallbacks(stopRunnable)
        runCatching {
            vehicle.getSeat().moveSeatForeAft(
                passengerSeat, MoveControl.STOP,
                { _, c -> Log.d(TAG, "[#11] 시트 정지: $c") },
                { e -> Log.w(TAG, "[#11] 시트 정지 실패: ${e.message}") }
            )
        }.onFailure { Log.w(TAG, "[#11] 시트 정지 호출 실패: ${it.message}") }

        var moved = 0
        if (moveStartedAt != 0L) {
            val elapsed = (SystemClock.elapsedRealtime() - moveStartedAt).toInt()
            moved = if (moveForward) elapsed else -elapsed
            seatOffsetMs = (seatOffsetMs + moved).coerceIn(-MAX_SEAT_MOVE_MS, MAX_SEAT_MOVE_MS)
            moveStartedAt = 0L
            Log.d(TAG, "[#11] 이번에 움직인 시간 ${moved}ms, 원래 자리에서 ${seatOffsetMs}ms")
        }
        afterSeatStop?.let { afterSeatStop = null; it() }
        return moved
    }

    // ───────── #10 열선 · 온도 ─────────
    // null = 건너뜀 (아직 설정 안 한 칸, 온도 "그대로 두기")

    private fun applyHeat(level: Int?, onDone: () -> Unit) {
        if (level == null) {
            main.post(onDone)
            return
        }
        val v = level.coerceIn(MIN_HEAT, MAX_HEAT)
        try {
            vehicle.getSeat().setHvacSeatTemperature(
                passengerSeat, v,
                { _, set -> main.post { commandSucceeded = true; Log.d(TAG, "[#10] 열선 → $set"); onDone() } },
                { e -> main.post { Log.w(TAG, "[#10] 열선 실패: ${e.message}"); onDone() } }
            )
        } catch (e: Exception) {
            Log.w(TAG, "[#10] 열선 호출 실패: ${e.message}")
            main.post(onDone)
        }
    }

    private fun applyTemperature(celsius: Float?, onDone: () -> Unit) {
        if (celsius == null) {
            main.post(onDone)
            return
        }
        val v = celsius.coerceIn(MIN_TEMP, MAX_TEMP)
        try {
            vehicle.getHVAC().setHvacTemperature(
                passengerSeat, v, TemperatureUnit.CELSIUS,
                { _, set -> main.post { commandSucceeded = true; Log.d(TAG, "[#10] 온도 → $set"); onDone() } },
                { e -> main.post { Log.w(TAG, "[#10] 온도 실패: ${e.message}"); onDone() } }
            )
        } catch (e: Exception) {
            Log.w(TAG, "[#10] 온도 호출 실패: ${e.message}")
            main.post(onDone)
        }
    }

    // ───────── 자리 설정 화면(#46)용 ─────────
    // ◀ ▶ 누르는 동안만 시트 이동. 움직인 시간은 자동 준비와 같은 seatOffsetMs에 쌓여서
    // 복구(#14) 때 자동 준비 + 직접 조절한 만큼이 함께 되돌아가요.

    override fun startSeatMove(forward: Boolean) {
        Log.d(TAG, "[#46] 시트 ${if (forward) "앞으로" else "뒤로"} 누름")
        if (!startSeat(forward)) return
        // 떼는 신호를 놓쳐도(화면 꺼짐 등) 최대 시간이 지나면 알아서 멈춰요
        main.postDelayed(stopRunnable, MAX_SEAT_MOVE_MS.toLong())
    }

    override fun stopSeatMove(): Int {
        val moved = stopSeatAndMeasure()
        Log.d(TAG, "[#46] 시트 뗌 → ${moved}ms")
        return moved
    }

    override fun setSeatHeat(level: Int) {
        applyHeat(level) {}
    }

    override fun setTemperature(celsius: Float) {
        applyTemperature(celsius) {}
    }

    // ───────── #12 귀갓길: 창문 닫기 · 무드등 ─────────
    // 무드등은 영역이 차마다 달라서 SDK가 알려주는 지원 영역 전체에 적용해요.
    // 바꾸기 전 밝기·색을 기억해 두고 restore(#14)에서 되돌려요.

    private val lightBefore = mutableMapOf<SeatArea, Pair<Int?, Int?>>() // 영역 → (밝기, 색)

    override fun applyRideHome(profile: Profile) {
        Log.d(TAG, "[#12] 귀갓길: 창문 닫기=${profile.closeWindow}, 무드등 색=${profile.lightColor}, 밝기=${profile.lightBrightness}")
        if (profile.closeWindow) setWindow(0) {}
        applyAmbientLight(profile.lightBrightness, profile.lightColor ?: DEFAULT_LIGHT_COLOR)
    }

    private fun setWindow(position: Int, onDone: () -> Unit) {
        try {
            vehicle.getWindow().setWindowPosition(
                passengerWindow, position,
                { _, p -> main.post { Log.d(TAG, "창문 → $p"); onDone() } },
                { e -> main.post { Log.w(TAG, "창문 실패: ${e.message}"); onDone() } }
            )
        } catch (e: Exception) {
            Log.w(TAG, "창문 호출 실패: ${e.message}")
            main.post(onDone)
        }
    }

    /** brightness: 0..100 (앱 기준) → 차가 알려준 최소~최대 범위로 바꿔서 적용 */
    private fun applyAmbientLight(brightness: Int, argb: Int) {
        val light = vehicle.getLight()
        try {
            light.checkAmbientLightBrightnessCapabilities(
                { _, bounds ->
                    bounds.forEach { (area, b) ->
                        val min = b.minValue
                        val max = b.maxValue
                        val value = min + (max - min) * brightness.coerceIn(0, 100) / 100
                        // 바꾸기 전 값을 먼저 읽고 나서 바꿔요
                        light.getAmbientLightBrightness(area, { oldB ->
                            light.getAmbientLightBrightnessColor(area, { oldC ->
                                main.post { lightBefore.putIfAbsent(area, oldB to oldC) }
                                runCatching { light.setAmbientLightSwitch(area, LightSwitch.ON, { _, _ -> }, { }) }
                                light.setAmbientLightBrightness(area, value,
                                    { a, v -> Log.d(TAG, "[#12] 무드등 밝기 ${a.values} → $v") },
                                    { e -> Log.w(TAG, "[#12] 무드등 밝기 실패: ${e.message}") })
                                light.setAmbientLightBrightnessColor(area, argb,
                                    { a, v -> Log.d(TAG, "[#12] 무드등 색 ${a.values} → ${Integer.toHexString(v)}") },
                                    { e -> Log.w(TAG, "[#12] 무드등 색 실패: ${e.message}") })
                            }, { e -> Log.w(TAG, "[#12] 무드등 원래 색 읽기 실패: ${e.message}") })
                        }, { e -> Log.w(TAG, "[#12] 무드등 원래 밝기 읽기 실패: ${e.message}") })
                    }
                },
                { e -> Log.w(TAG, "[#12] 무드등 지원 영역 확인 실패: ${e.message}") }
            )
        } catch (e: Exception) {
            Log.w(TAG, "[#12] 무드등 호출 실패: ${e.message}")
        }
    }

    // ───────── #14 원래 상태로 복구 ─────────
    // 최종 하차·취소 때 C가 #9에서 읽어 둔 상태를 넘겨줘요.
    //   시트: 위치를 못 읽으니 "실제로 움직인 시간(seatOffsetMs)"만큼 반대로 움직여요
    //   열선·온도·창문: 읽어 둔 값으로 (null이면 모르는 값이라 그대로 둬요)
    //   무드등: #12에서 기억해 둔 값으로
    // onDone은 시트·열선·온도·창문이 다 끝나면 한 번만 불러요.
    override fun restore(state: PassengerState, onDone: () -> Unit) {
        Log.d(TAG, "[#14] 복구 시작: $state, 시트 되돌릴 시간=${-seatOffsetMs}ms")
        if (moveStartedAt != 0L) stopSeat() // 움직이는 중이면 먼저 멈추고 정산
        var waiting = 4
        val partDone = {
            waiting--
            if (waiting == 0) {
                Log.d(TAG, "[#14] 복구 끝 (남은 시트 차이 ${seatOffsetMs}ms)")
                onDone()
            }
        }
        moveSeatFor(-seatOffsetMs, onStopped = partDone)
        applyHeat(state.seatHeat, onDone = partDone)
        applyTemperature(state.temperature, onDone = partDone)
        val window = state.windowPosition
        if (window != null) setWindow(window, partDone) else main.post(partDone)
        restoreAmbientLight()
    }

    private fun restoreAmbientLight() {
        val light = vehicle.getLight()
        lightBefore.forEach { (area, before) ->
            val (brightness, color) = before
            runCatching {
                if (brightness != null) light.setAmbientLightBrightness(area, brightness, { _, _ -> },
                    { e -> Log.w(TAG, "[#14] 무드등 밝기 복구 실패: ${e.message}") })
                if (color != null) light.setAmbientLightBrightnessColor(area, color, { _, _ -> },
                    { e -> Log.w(TAG, "[#14] 무드등 색 복구 실패: ${e.message}") })
            }
        }
        lightBefore.clear()
    }

    /** 앱이 꺼질 때. 움직이는 시트를 멈추고 SDK 연결을 풀어요 */
    fun release() {
        if (moveStartedAt != 0L) stopSeat()
        main.removeCallbacksAndMessages(null)
        vehicle.release()
    }

    companion object {
        private const val TAG = "MajiControl"
        private const val READ_TIMEOUT_MS = 2000L

        // 값 범위 (Pleos 에뮬레이터 기준, D의 검증 범위와 같게)
        const val MAX_SEAT_MOVE_MS = 10_000 // 끝→끝 약 10초로 추정 (전동시트 약 25cm, 초당 2~2.5cm)
        const val MIN_HEAT = -3
        const val MAX_HEAT = 3
        const val MIN_TEMP = 15f
        const val MAX_TEMP = 30f

        // 프로필에 무드등 색이 없을 때 귀갓길 기본 색 (따뜻한 주황빛, ARGB)
        const val DEFAULT_LIGHT_COLOR = 0xFFFFB36B.toInt()
    }
}
