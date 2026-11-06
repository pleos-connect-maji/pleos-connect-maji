package com.maji.signal

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.maji.contract.Capabilities
import com.maji.contract.VehicleSignals

/**
 * A. 차량 신호 (Pleos Vehicle SDK 2.3.2)
 *
 * 지금 되는 것: P단 감지 onParkChanged (#28)
 * 주차 브레이크: Pleos SDK에 없음 → 팀 결정 대기
 * 문·주행 제한·열선·온도: 다음 이슈에서 추가 (#29, #30, #31)
 */
@Suppress("DEPRECATION") // Pleos Gear API는 '지원 중단 예정'이지만 2.3.2에서는 동작해요
class PleosVehicleSignals(context: Context) : VehicleSignals {

    private val vehicle = Vehicle(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var listener: VehicleSignals.Listener? = null
    private var lastIsPark: Boolean? = null   // 마지막으로 알려준 값 (같은 값이면 또 안 알려줌)
    private var gearRetryCount = 0

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
    }

    override fun stop() {
        if (listener == null) return
        Log.d(TAG, "stop")
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
        vehicle.release()
        listener = null
        lastIsPark = null
        gearRetryCount = 0
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

    // ───────── 기어 ─────────

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

    companion object {
        private const val TAG = "MajiSignal"
        private const val MAX_RETRY = 5
        private const val RETRY_DELAY_MS = 1000L
    }
}

