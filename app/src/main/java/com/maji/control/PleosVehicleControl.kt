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
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.maji.contract.AmbientLight
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

    private val appContext = context.applicationContext
    private val vehicle = Vehicle(appContext).apply { initialize() }
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
    private var ventIsSeparate: Boolean? = null // null = 아직 모름 → 열선 값 하나로 보내요 (에뮬레이터 방식)

    // ───────── #97 테스트용 "제어 불가 강제" (debug 빌드에서만) ─────────
    // 에뮬레이터에서는 제어가 막힌 차(대체 모드)를 재현할 수 없어서, 확인 화면의 스위치로 흉내 내요.
    // 켜면 차에 명령을 보내지 않고 "실패"로 처리해요 → isControlAvailable()=false, allPrepareFailed까지 실제처럼 지나가요.
    // 설정은 앱 저장소에 있어서 확인 화면에서 켜면 실제 앱 화면에도 바로 반영돼요.
    private val debuggable = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private val debugPrefs: SharedPreferences = debugPrefs(appContext)
    private val forceOffListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_FORCE_OFF && !forcedOff()) {
            allPrepareFailed = false // 강제를 끄면 강제 때문에 쌓인 실패 기록도 비워요
            Log.d(TAG, "[#97] 제어 불가 강제 OFF → 정상")
        }
    }

    private fun forcedOff(): Boolean = debuggable && debugPrefs.getBoolean(KEY_FORCE_OFF, false)

    /** 강제 중이면 명령을 보내지 않고 실패로 처리해요. true면 호출한 쪽은 여기서 멈춰요 */
    private fun blockedByForce(name: String, onDone: () -> Unit = {}): Boolean {
        if (!forcedOff()) return false
        Log.w(TAG, "[#97] 제어 불가 강제 중 → $name 실패 처리")
        main.post(onDone)
        return true
    }

    init {
        checkControlCapabilities()
        if (debuggable) debugPrefs.registerOnSharedPreferenceChangeListener(forceOffListener)
    }

    override fun isControlAvailable(): Boolean {
        return ControlRules.isControlAvailable(
            listOf(capSeatMove, capSeatHeat, capTemperature), allPrepareFailed, forcedOff = forcedOff()
        )
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
                    // 열선 범위가 음수까지면(-3..3) 열선 값 하나로 통풍까지, 0부터면 통풍이 따로 있는 차
                    val min = bounds.entries.firstOrNull { passengerSeat.isIn(listOf(it.key)) }?.value?.minValue
                    main.post {
                        capSeatHeat = canControl(config) && passengerSeat.isIn(bounds.keys)
                        if (min != null) ventIsSeparate = min >= 0
                        logCaps()
                    }
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
    // 열선·통풍·온도·창문 4개를 동시에 읽고, 다 끝나면 한 번만 알려줘요.
    // 실패한 칸은 null (에뮬레이터는 온도 읽기가 실패해요 → C가 A의 온도 신호로 채워요).
    // SDK가 콜백을 안 주는 경우를 대비해 READ_TIMEOUT_MS가 지나면 모인 값만으로 알려줘요.
    override fun readPassengerState(onResult: (PassengerState) -> Unit) {
        var heat: Int? = null
        var vent: Int? = null
        var temperature: Float? = null
        var window: Int? = null
        var finished = 0
        var delivered = false

        fun deliver(reason: String) {
            if (delivered) return
            delivered = true
            // 통풍이 따로 있는 차도 Contract 약속(-3..3, 음수 = 통풍) 하나로 합쳐요
            val state = PassengerState(ControlRules.mergeHeatVent(heat, vent), temperature, window)
            Log.d(TAG, "[#9] 상태 읽기 끝($reason): $state")
            onResult(state)
        }

        // 읽기 하나가 끝날 때마다 메인 스레드에서 불러요
        fun oneDone() {
            finished++
            if (finished == 4) deliver("4개 완료")
        }

        main.postDelayed({ deliver("시간 초과") }, READ_TIMEOUT_MS)

        try {
            vehicle.getSeat().getHvacSeatTemperature(
                passengerSeat,
                { v -> main.post { heat = v; oneDone() } },
                { e -> main.post { Log.w(TAG, "[#9] 열선 읽기 실패: ${e.message}"); oneDone() } }
            )
            vehicle.getSeat().getHvacSeatVentilationState(
                passengerSeat,
                { v -> main.post { vent = v; oneDone() } },
                { e -> main.post { Log.d(TAG, "[#9] 통풍 읽기 실패(통풍 없는 차일 수 있음): ${e.message}"); oneDone() } }
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
    // 실제로 움직인 시간을 실제로 움직인 시간을 seatTracker에 쌓아 두고, restore(#14)에서 그만큼 반대로 움직여요.

    private val seatTracker = SeatMoveTracker() // 실제로 움직인 시간 기록 (계산은 ControlRules.kt)
    private var afterSeatStop: (() -> Unit)? = null
    private val stopRunnable = Runnable { stopSeat() }

    private fun moveSeatFor(ms: Int, onStopped: () -> Unit) {
        val target = ControlRules.clampSeatMove(ms)
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

    /**
     * 앞/뒤로 출발. 이미 움직이는 중이면 먼저 멈춰서 시간을 정산해요.
     * SDK가 나중에 "출발 거절"을 알려 오면 그 출발은 기록에서 빼요 (안 움직였는데 복구 때 되돌리는 사고 방지).
     */
    private fun startSeat(forward: Boolean, manual: Boolean = false): Boolean {
        if (seatTracker.isMoving) stopSeat()
        if (blockedByForce("시트 출발")) return false
        val token = seatTracker.start(forward, SystemClock.elapsedRealtime(), manual)
        return try {
            vehicle.getSeat().moveSeatForeAft(
                passengerSeat,
                if (forward) MoveControl.FORWARD else MoveControl.BACKWARD,
                { _, c -> main.post { commandSucceeded = true }; Log.d(TAG, "[#11] 시트 출발: $c") },
                { e ->
                    main.post {
                        Log.w(TAG, "[#11] 시트 출발 거절 → 이동 기록에서 뺌: ${e.message}")
                        seatTracker.reject(token)
                        notifySeatMoved()
                    }
                }
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "[#11] 시트 출발 호출 실패: ${e.message}")
            seatTracker.reject(token)
            notifySeatMoved()
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
        sendStop(attempt = 1)

        val wasMoving = seatTracker.isMoving
        val moved = seatTracker.stop(SystemClock.elapsedRealtime())
        if (wasMoving) {
            Log.d(TAG, "[#11] 이번에 움직인 시간 ${moved}ms, 원래 자리에서 ${seatTracker.offsetMs}ms")
            notifySeatMoved()
        }
        afterSeatStop?.let { afterSeatStop = null; it() }
        return moved
    }

    /**
     * STOP 전송. 실패하면 잠깐 뒤 다시 보내요 (최대 STOP_RETRY번).
     * 시트가 계속 움직이면 위험하므로 정지는 "성공 확인"까지 재시도해요.
     */
    private fun sendStop(attempt: Int) {
        fun retry(reason: String?) {
            if (attempt >= STOP_RETRY) {
                Log.e(TAG, "[#11] 시트 정지 ${attempt}번 모두 실패: $reason")
                return
            }
            Log.w(TAG, "[#11] 시트 정지 실패($attempt/$STOP_RETRY) → 다시 보냄: $reason")
            main.postDelayed({ sendStop(attempt + 1) }, STOP_RETRY_DELAY_MS)
        }
        try {
            vehicle.getSeat().moveSeatForeAft(
                passengerSeat, MoveControl.STOP,
                { _, c -> Log.d(TAG, "[#11] 시트 정지: $c") },
                { e -> main.post { retry(e.message) } }
            )
        } catch (e: Exception) {
            retry(e.message)
        }
    }

    // ───────── #26 재시작 복원: 시트 이동 기록 저장·불러오기 ─────────
    // B는 기록을 들고만 있고, 저장은 C가 flow_snapshot.seatMovedMs에 해요 (설계 문서 8.7).

    private var onSeatMovedChanged: ((Int) -> Unit)? = null

    override fun seatMovedMs(): Int = seatTracker.offsetMs

    override fun restoreSeatMovedMs(ms: Int) {
        if (seatTracker.isMoving) stopSeat()
        seatTracker.load(ms)
        Log.d(TAG, "[#26] 시트 이동 기록 불러옴: ${seatTracker.offsetMs}ms")
    }

    override fun setOnSeatMovedChanged(listener: ((Int) -> Unit)?) {
        onSeatMovedChanged = listener
    }

    // 기록이 바뀔 때마다 C에게 알려줘요 (메인 스레드에서만 불려요)
    private fun notifySeatMoved() {
        onSeatMovedChanged?.invoke(seatTracker.offsetMs)
    }

    // ───────── #10 열선 · 온도 ─────────
    // null = 건너뜀 (아직 설정 안 한 칸, 온도 "그대로 두기")

    private fun applyHeat(level: Int?, onDone: () -> Unit) {
        if (level == null) {
            main.post(onDone)
            return
        }
        if (blockedByForce("열선", onDone)) return
        val v = ControlRules.clampHeat(level)
        if (ventIsSeparate == true) {
            // 통풍이 따로 있는 차: 2 → 열선 2 + 통풍 0, -2 → 열선 0 + 통풍 2
            val (h, vt) = ControlRules.splitHeatVent(v)
            var left = 2
            val partDone = { left--; if (left == 0) onDone() }
            sendSeatValue("열선", h, vehicle.getSeat()::setHvacSeatTemperature, partDone)
            sendSeatValue("통풍", vt, vehicle.getSeat()::setHvacSeatVentilation, partDone)
            return
        }
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

    private fun sendSeatValue(
        name: String,
        value: Int,
        setter: (SeatArea, Int, (SeatArea, Int?) -> Unit, (Exception) -> Unit) -> Unit,
        onDone: () -> Unit
    ) {
        try {
            setter(passengerSeat, value,
                { _, set -> main.post { commandSucceeded = true; Log.d(TAG, "[#10] $name → $set"); onDone() } },
                { e -> main.post { Log.w(TAG, "[#10] $name 실패: ${e.message}"); onDone() } })
        } catch (e: Exception) {
            Log.w(TAG, "[#10] $name 호출 실패: ${e.message}")
            main.post(onDone)
        }
    }

    private fun applyTemperature(celsius: Float?, onDone: () -> Unit) {
        if (celsius == null) {
            main.post(onDone)
            return
        }
        if (blockedByForce("온도", onDone)) return
        val v = ControlRules.clampTemperature(celsius)
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
    // ◀ ▶ 누르는 동안만 시트 이동. 움직인 시간은 자동 준비와 같은 seatTracker에 쌓여서
    // 복구(#14) 때 자동 준비 + 직접 조절한 만큼이 함께 되돌아가요.

    override fun startSeatMove(forward: Boolean) {
        Log.d(TAG, "[#46] 시트 ${if (forward) "앞으로" else "뒤로"} 누름")
        if (!startSeat(forward, manual = true)) return
        // 떼는 신호를 놓쳐도(화면 꺼짐 등) 최대 시간이 지나면 알아서 멈춰요
        main.postDelayed(stopRunnable, ControlRules.MAX_SEAT_MOVE_MS.toLong())
    }

    // 문 열림·10초 자동 정지가 뗌보다 먼저 와도, 이번 누름 동안 움직인 시간을 그대로 돌려줘요
    override fun stopSeatMove(): Int {
        stopSeatAndMeasure()
        val moved = seatTracker.takeManualMs()
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
    // 바꾸기 전 밝기·색·켜짐 상태를 기억해 두고 restore(#14)에서 되돌려요.

    /** 바꾸기 전 무드등 상태. 못 읽은 값은 null (그 값은 복구 때 건드리지 않아요) */
    private data class LightSnapshot(val brightness: Int?, val color: Int?, val switch: LightSwitch?)

    private val lightBefore = mutableMapOf<SeatArea, LightSnapshot>()

    // 무드등 작업 회차. 복구·종료하면 올려서, 그 전에 시작된 귀갓길 작업이 늦게 도착해도 무시해요
    private var lightRound = 0

    override fun applyRideHome(profile: Profile) {
        Log.d(TAG, "[#12] 귀갓길: 창문 닫기=${profile.closeWindow}, 무드등 색=${profile.lightColor}, 밝기=${profile.lightBrightness}")
        if (profile.closeWindow) setWindow(0) {}
        applyAmbientLight(profile.lightBrightness, profile.lightColor ?: DEFAULT_LIGHT_COLOR)
    }

    private fun setWindow(position: Int, onDone: () -> Unit) {
        if (blockedByForce("창문", onDone)) return
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

    /**
     * brightness: 0..100 (앱 기준) → 차가 알려준 최소~최대 범위로 바꿔서 적용.
     * 바꾸기 전 밝기·색·켜짐 상태를 읽어 두되, 읽기가 실패해도 무드등은 켜요 (못 읽은 값만 null로 기억).
     */
    // 마지막으로 쓴 밝기(앱 기준 %). 차 밝기 단계가 거칠면(예: 1..10) 읽어서 되돌린 %가 달라져서(30 → 3 → 22)
    // 내릴 때 같은 값이면 이 %를 그대로 돌려줘요. 안 그러면 탈 때마다 밝기가 조금씩 내려가요 (#111)
    private var lastBrightnessPct: Int? = null

    private fun applyAmbientLight(brightness: Int, argb: Int) {
        if (blockedByForce("무드등")) return
        lastBrightnessPct = brightness
        val light = vehicle.getLight()
        val round = lightRound
        fun stale() = round != lightRound // 그사이 복구됐으면 더 진행하지 않아요

        fun write(area: SeatArea, value: Int) {
            if (stale()) return
            runCatching {
                light.setAmbientLightSwitch(area, LightSwitch.ON, { _, _ -> },
                    { e -> Log.w(TAG, "[#12] 무드등 켜기 실패: ${e.message}") })
                light.setAmbientLightBrightness(area, value,
                    { a, v -> Log.d(TAG, "[#12] 무드등 밝기 ${a.values.joinToString()} → $v") },
                    { e -> Log.w(TAG, "[#12] 무드등 밝기 실패: ${e.message}") })
                light.setAmbientLightBrightnessColor(area, argb,
                    { a, v -> Log.d(TAG, "[#12] 무드등 색 ${a.values.joinToString()} → ${Integer.toHexString(v)}") },
                    { e -> Log.w(TAG, "[#12] 무드등 색 실패: ${e.message}") })
            }.onFailure { Log.w(TAG, "[#12] 무드등 호출 실패: ${it.message}") }
        }

        // 밝기 → 색 → 켜짐 순서로 읽고(실패해도 다음으로), 기억한 뒤 바꿔요
        fun snapshotThenWrite(area: SeatArea, value: Int) {
            fun done(b: Int?, c: Int?, sw: LightSwitch?) = main.post {
                if (stale()) return@post
                lightBefore.putIfAbsent(area, LightSnapshot(b, c, sw))
                write(area, value)
            }
            fun readSwitch(b: Int?, c: Int?) = runCatching {
                light.getAmbientLightSwitch(area, { sw -> done(b, c, sw) }, { done(b, c, null) })
            }.onFailure { done(b, c, null) }
            fun readColor(b: Int?) = runCatching {
                light.getAmbientLightBrightnessColor(area, { c -> readSwitch(b, c) }, { readSwitch(b, null) })
            }.onFailure { readSwitch(b, null) }
            runCatching {
                light.getAmbientLightBrightness(area, { b -> readColor(b) }, { readColor(null) })
            }.onFailure { readColor(null) }
        }

        try {
            light.checkAmbientLightBrightnessCapabilities(
                { _, bounds ->
                    bounds.forEach { (area, b) ->
                        snapshotThenWrite(area, ControlRules.scaleBrightness(brightness, b.minValue, b.maxValue))
                    }
                },
                { e -> Log.w(TAG, "[#12] 무드등 지원 영역 확인 실패: ${e.message}") }
            )
        } catch (e: Exception) {
            Log.w(TAG, "[#12] 무드등 호출 실패: ${e.message}")
        }
    }

    // ───────── #111 함께 가는 동안 무드등 바꾸기 · 내릴 때 읽기 ─────────

    // 귀갓길과 같은 길로 바꿔요. 바꾸기 전 값은 처음 한 번만 기억해서(putIfAbsent) 여러 번 바꿔도 복구는 탑승 전 값으로 가요
    override fun setAmbientLight(light: AmbientLight) {
        Log.d(TAG, "[#111] 무드등 바꿈: 색=${Integer.toHexString(light.color)}, 밝기=${light.brightness}")
        applyAmbientLight(light.brightness, light.color)
    }

    // 지원 영역 중 첫 영역의 색·밝기를 읽어요 (귀갓길에 모든 영역을 같은 값으로 맞추니까 하나면 충분해요).
    // 콜백이 안 오거나 실패해도 READ_TIMEOUT_MS 안에 꼭 한 번 알려줘요
    override fun readAmbientLight(onResult: (AmbientLight?) -> Unit) {
        var delivered = false
        fun deliver(result: AmbientLight?) = main.post {
            if (delivered) return@post
            delivered = true
            Log.d(TAG, "[#111] 무드등 읽음: $result")
            onResult(result)
        }
        main.postDelayed({ deliver(null) }, READ_TIMEOUT_MS)
        if (blockedByForce("무드등 읽기")) { deliver(null); return }
        val light = vehicle.getLight()
        try {
            light.checkAmbientLightBrightnessCapabilities(
                { _, bounds ->
                    val (area, b) = bounds.entries.firstOrNull()?.toPair() ?: run { deliver(null); return@checkAmbientLightBrightnessCapabilities }
                    runCatching {
                        light.getAmbientLightBrightnessColor(area, { color ->
                            runCatching {
                                light.getAmbientLightBrightness(area, { value ->
                                    // 알파가 빠져서 올 수 있어서 불투명으로 맞춰요. 값이 비어 오면 못 읽은 걸로
                                    if (color == null || value == null) deliver(null)
                                    else deliver(AmbientLight(color or 0xFF000000.toInt(), ControlRules.readBrightness(value, b.minValue, b.maxValue, lastBrightnessPct)))
                                }, { deliver(null) })
                            }.onFailure { deliver(null) }
                        }, { deliver(null) })
                    }.onFailure { deliver(null) }
                },
                { deliver(null) }
            )
        } catch (e: Exception) {
            Log.w(TAG, "[#111] 무드등 읽기 실패: ${e.message}")
            deliver(null)
        }
    }

    // ───────── #14 원래 상태로 복구 ─────────
    // 최종 하차·취소 때 C가 #9에서 읽어 둔 상태를 넘겨줘요.
    //   시트: 위치를 못 읽으니 "실제로 움직인 시간(seatTracker)"만큼 반대로 움직여요
    //   열선·온도·창문: 읽어 둔 값으로 (null이면 모르는 값이라 그대로 둬요)
    //   무드등: #12에서 기억해 둔 값으로
    // onDone은 시트·열선·온도·창문이 다 끝나면 한 번만 불러요.
    override fun restore(state: PassengerState, onDone: () -> Unit) {
        Log.d(TAG, "[#14] 복구 시작: $state, 시트 되돌릴 시간=${seatTracker.restorePlanMs()}ms")
        if (seatTracker.isMoving) stopSeat() // 움직이는 중이면 먼저 멈추고 정산
        var waiting = 4
        val partDone = {
            waiting--
            if (waiting == 0) {
                Log.d(TAG, "[#14] 복구 끝 (남은 시트 차이 ${seatTracker.offsetMs}ms)")
                onDone()
            }
        }
        moveSeatFor(seatTracker.restorePlanMs(), onStopped = partDone)
        applyHeat(state.seatHeat, onDone = partDone)
        applyTemperature(state.temperature, onDone = partDone)
        val window = state.windowPosition
        if (window != null) setWindow(window, partDone) else main.post(partDone)
        restoreAmbientLight()
    }

    private fun restoreAmbientLight() {
        lightRound++ // 아직 진행 중인 귀갓길 무드등 작업은 무시
        val light = vehicle.getLight()
        lightBefore.forEach { (area, before) ->
            runCatching {
                before.brightness?.let { light.setAmbientLightBrightness(area, it, { _, _ -> },
                    { e -> Log.w(TAG, "[#14] 무드등 밝기 복구 실패: ${e.message}") }) }
                before.color?.let { light.setAmbientLightBrightnessColor(area, it, { _, _ -> },
                    { e -> Log.w(TAG, "[#14] 무드등 색 복구 실패: ${e.message}") }) }
                // 원래 꺼져 있었으면 다시 꺼요
                before.switch?.let { light.setAmbientLightSwitch(area, it, { _, _ -> },
                    { e -> Log.w(TAG, "[#14] 무드등 켜짐 복구 실패: ${e.message}") }) }
            }
        }
        lightBefore.clear()
    }

    /** 앱이 꺼질 때. 움직이는 시트를 멈추고 SDK 연결을 풀어요 */
    fun release() {
        lightRound++
        if (debuggable) debugPrefs.unregisterOnSharedPreferenceChangeListener(forceOffListener)
        if (seatTracker.isMoving) stopSeat()
        main.removeCallbacksAndMessages(null)
        vehicle.release()
    }

    companion object {
        private const val TAG = "MajiControl"
        private const val READ_TIMEOUT_MS = 2000L
        private const val STOP_RETRY = 3
        private const val STOP_RETRY_DELAY_MS = 300L

        private const val DEBUG_PREFS = "maji_control_debug"
        private const val KEY_FORCE_OFF = "force_control_off"

        private fun debugPrefs(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(DEBUG_PREFS, Context.MODE_PRIVATE)

        /** 테스트용 "제어 불가 강제" 켜기/끄기 (#97). debug 빌드에서만 효과가 있어요 */
        fun setForceControlOff(context: Context, on: Boolean) {
            debugPrefs(context).edit().putBoolean(KEY_FORCE_OFF, on).apply()
        }

        fun isForceControlOff(context: Context): Boolean = debugPrefs(context).getBoolean(KEY_FORCE_OFF, false)

        // 프로필에 무드등 색이 없을 때 귀갓길 기본 색 (따뜻한 주황빛, ARGB)
        const val DEFAULT_LIGHT_COLOR = 0xFFFFB36B.toInt()
    }
}
