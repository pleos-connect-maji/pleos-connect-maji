package com.maji.signal

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.HvacSeatVentilationListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.maji.contract.Capabilities
import com.maji.contract.VehicleSignals

/**
 * A. 차량 신호 (Pleos Vehicle SDK 2.3.2)
 *
 * 되는 것: P단 onParkChanged (#28), 조수석 문 onPassengerDoorChanged (#29),
 *         조수석 열선 onPassengerHeatChanged · 온도 onPassengerTemperatureChanged (#31)
 * 주차 브레이크: Pleos SDK에 없어서 팀 논의 후 제외
 * 주행 제한: 다음 이슈에서 추가 (#30)
 */
@Suppress("DEPRECATION") // Pleos Gear API는 '지원 중단 예정'이지만 2.3.2에서는 동작해요
class PleosVehicleSignals(context: Context) : VehicleSignals {

    private val vehicle = Vehicle(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var listener: VehicleSignals.Listener? = null

    // 마지막으로 알려준 값 (같은 값이면 또 안 알려줌)
    private var lastIsPark: Boolean? = null
    private var lastDoorOpen: Boolean? = null
    private var lastHeatLevel: Int? = null
    private var lastCelsius: Float? = null

    // 열선·통풍 원래 값 (둘을 합쳐서 -3..3 하나로 만들어요)
    private var rawSeatHeat: Int? = null
    private var rawSeatVent: Int? = null

    // 재시도 횟수
    private var gearRetryCount = 0
    private var doorRetryCount = 0
    private var climateRetryCount = 0

    // 조수석 = 1열 오른쪽
    private val passengerDoor = DoorArea(DoorValue.ROW_1_RIGHT)
    private val passengerSeat = SeatArea(SeatValue.ROW_1_RIGHT)

    // 기어가 바뀌면 SDK가 불러주는 곳
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            Log.d(TAG, "기어 신호: $gear")
            updatePark(gear)
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "기어 구독 실패: ${e.message}")
            mainHandler.post { retryGear() }
        }
    }

    // 조수석 문이 바뀌면 SDK가 불러주는 곳
    private val doorListener = object : DoorPositionListener {
        override fun onPositionUpdated(pos: Int?) {
            Log.d(TAG, "조수석 문 신호: $pos")
            updateDoor(pos)
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "문 구독 실패: ${e.message}")
            mainHandler.post { retryDoor() }
        }
    }

    // 조수석 열선이 바뀌면
    private val seatHeatListener = object : HvacSeatTemperatureListener {
        override fun onHvacSeatTemperatureUpdated(temperature: Int?) {
            Log.d(TAG, "조수석 열선 신호(원래 값): $temperature")
            mainHandler.post {
                rawSeatHeat = temperature
                updateHeat()
            }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "열선 구독 실패: ${e.message}")
            mainHandler.post { retryClimate() }
        }
    }

    // 조수석 통풍이 바뀌면 (차에 통풍이 없을 수도 있어서 실패해도 재시도 안 함)
    private val seatVentListener = object : HvacSeatVentilationListener {
        override fun onHvacSeatVentilationUpdated(ventilation: Int?) {
            Log.d(TAG, "조수석 통풍 신호(원래 값): $ventilation")
            mainHandler.post {
                rawSeatVent = ventilation
                updateHeat()
            }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "통풍 구독 실패(통풍 없는 차일 수 있음): ${e.message}")
        }
    }

    // 조수석 설정 온도가 바뀌면
    private val temperatureListener = object : HvacTemperatureListener {
        override fun onHvacTemperatureUpdated(temperature: Float?) {
            Log.d(TAG, "조수석 온도 신호: $temperature")
            updateTemperature(temperature)
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "온도 구독 실패: ${e.message}")
            mainHandler.post { retryClimate() }
        }
    }

    override fun start(listener: VehicleSignals.Listener) {
        if (this.listener != null) {
            // 두 번 불러도 중복 등록 안 되게 (#33)
            Log.d(TAG, "이미 시작됨 → 리스너만 바꿈")
            this.listener = listener
            return
        }
        this.listener = listener
        Log.d(TAG, "start")
        vehicle.initialize()
        subscribeGear()
        subscribeDoor()
        subscribeClimate()
    }

    override fun stop() {
        if (listener == null) return
        Log.d(TAG, "stop")
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
        runCatching { vehicle.getDoor().unregisterDoorPosition(passengerDoor, doorListener) }
        unregisterClimate()
        vehicle.release()
        listener = null
        lastIsPark = null
        lastDoorOpen = null
        lastHeatLevel = null
        lastCelsius = null
        rawSeatHeat = null
        rawSeatVent = null
        gearRetryCount = 0
        doorRetryCount = 0
        climateRetryCount = 0
    }

    override fun capabilities(): Capabilities {
        // TODO(#32): 실제 차량 값으로 채우기. 지금은 전부 된다고 가정해요
        return Capabilities(
            seatMove = true,
            seatHeat = true,
            temperature = true,
            window = true,
            light = true
        )
    }

    // ───────── 기어 (#28) ─────────

    private fun subscribeGear() {
        try {
            val gear = vehicle.getGear()
            // 1) 지금 기어를 한 번 읽어서 처음 상태 알려주기
            gear.getCurrentGearSelectionState(
                onComplete = { current ->
                    Log.d(TAG, "현재 기어: $current")
                    updatePark(current)
                },
                onFailed = { e -> Log.w(TAG, "현재 기어 읽기 실패: ${e.message}") }
            )
            // 2) 앞으로 바뀔 때마다 알려달라고 등록
            gear.registerGearSelection(gearListener)
        } catch (e: Exception) {
            Log.w(TAG, "기어 등록 중 오류: ${e.message}")
            retryGear()
        }
    }

    // 차 서비스 연결 전이라 실패했을 수 있어서, 1초 뒤 다시 시도 (최대 5번)
    private fun retryGear() {
        if (listener == null || gearRetryCount >= MAX_RETRY) return
        gearRetryCount++
        mainHandler.postDelayed({
            if (listener == null) return@postDelayed
            Log.d(TAG, "기어 구독 다시 시도 ($gearRetryCount/$MAX_RETRY)")
            runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
            subscribeGear()
        }, RETRY_DELAY_MS)
    }

    // 기어 값 → P단인지 아닌지로 바꿔서, 바뀌었을 때만 알려주기
    private fun updatePark(gear: VehicleGear?) {
        if (gear == null || gear == VehicleGear.GEAR_UNKNOWN) return
        val isPark = gear == VehicleGear.GEAR_PARK
        mainHandler.post {
            gearRetryCount = 0
            if (isPark == lastIsPark) return@post
            lastIsPark = isPark
            Log.d(TAG, "→ onParkChanged($isPark)")
            listener?.onParkChanged(isPark)
        }
    }

    // ───────── 조수석 문 (#29) ─────────

    private fun subscribeDoor() {
        try {
            val door = vehicle.getDoor()
            // 1) 지금 문 상태를 한 번 읽어서 처음 상태 알려주기
            door.getCurrentDoorPosition(
                area = passengerDoor,
                onComplete = { pos ->
                    Log.d(TAG, "현재 조수석 문: $pos")
                    updateDoor(pos)
                },
                onFailed = { e -> Log.w(TAG, "현재 문 읽기 실패: ${e.message}") }
            )
            // 2) 앞으로 바뀔 때마다 알려달라고 등록 (조수석 문만)
            door.registerDoorPosition(passengerDoor, doorListener)
        } catch (e: Exception) {
            Log.w(TAG, "문 등록 중 오류: ${e.message}")
            retryDoor()
        }
    }

    private fun retryDoor() {
        if (listener == null || doorRetryCount >= MAX_RETRY) return
        doorRetryCount++
        mainHandler.postDelayed({
            if (listener == null) return@postDelayed
            Log.d(TAG, "문 구독 다시 시도 ($doorRetryCount/$MAX_RETRY)")
            runCatching { vehicle.getDoor().unregisterDoorPosition(passengerDoor, doorListener) }
            subscribeDoor()
        }, RETRY_DELAY_MS)
    }

    // 문 위치 숫자 → 열림/닫힘으로 바꿔서, 바뀌었을 때만 알려주기
    // 0 = 닫힘, 0보다 크면 = 열림 (조금만 열려도 열림)
    private fun updateDoor(pos: Int?) {
        if (pos == null) return
        val isOpen = pos > 0
        mainHandler.post {
            doorRetryCount = 0
            if (isOpen == lastDoorOpen) return@post
            lastDoorOpen = isOpen
            Log.d(TAG, "→ onPassengerDoorChanged($isOpen)")
            listener?.onPassengerDoorChanged(isOpen)
        }
    }

    // ───────── 열선·온도 (#31) ─────────

    private fun subscribeClimate() {
        try {
            val seat = vehicle.getSeat()
            seat.registerHvacSeatTemperature(passengerSeat, seatHeatListener)
            seat.registerHvacSeatVentilation(passengerSeat, seatVentListener)
            vehicle.getHVAC().registerHvacTemperature(passengerSeat, temperatureListener)
        } catch (e: Exception) {
            Log.w(TAG, "열선·온도 등록 중 오류: ${e.message}")
            retryClimate()
        }
    }

    private fun unregisterClimate() {
        runCatching { vehicle.getSeat().unregisterHvacSeatTemperature(passengerSeat, seatHeatListener) }
        runCatching { vehicle.getSeat().unregisterHvacSeatVentilation(passengerSeat, seatVentListener) }
        runCatching { vehicle.getHVAC().unregisterHvacTemperature(passengerSeat, temperatureListener) }
    }

    private fun retryClimate() {
        if (listener == null || climateRetryCount >= MAX_RETRY) return
        climateRetryCount++
        mainHandler.postDelayed({
            if (listener == null) return@postDelayed
            Log.d(TAG, "열선·온도 구독 다시 시도 ($climateRetryCount/$MAX_RETRY)")
            unregisterClimate()
            subscribeClimate()
        }, RETRY_DELAY_MS)
    }

    // 열선·통풍 원래 값 → Contract 약속(-3..3, 음수 = 통풍)으로 합쳐서, 바뀌었을 때만 알려주기
    // (메인 줄에서만 불려요)
    private fun updateHeat() {
        val heat = rawSeatHeat
        val vent = rawSeatVent
        if (heat == null && vent == null) return
        climateRetryCount = 0
        val level = when {
            heat != null && heat != 0 -> heat   // 열선 값이 있으면 그대로 (음수면 이미 통풍)
            vent != null && vent > 0 -> -vent   // 통풍이 따로 켜져 있으면 음수로
            else -> 0                           // 둘 다 꺼짐
        }
        if (level == lastHeatLevel) return
        lastHeatLevel = level
        Log.d(TAG, "→ onPassengerHeatChanged($level)  (열선=$heat, 통풍=$vent)")
        listener?.onPassengerHeatChanged(level)
    }

    // 온도는 숫자 그대로, 바뀌었을 때만 알려주기
    private fun updateTemperature(celsius: Float?) {
        if (celsius == null) return
        mainHandler.post {
            climateRetryCount = 0
            if (celsius == lastCelsius) return@post
            lastCelsius = celsius
            Log.d(TAG, "→ onPassengerTemperatureChanged($celsius)")
            listener?.onPassengerTemperatureChanged(celsius)
        }
    }

    companion object {
        private const val TAG = "MajiSignal"
        private const val MAX_RETRY = 5
        private const val RETRY_DELAY_MS = 1000L
    }
}