package com.maji.control

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import android.content.Context
import android.os.Handler
import android.os.Looper
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

    override fun isControlAvailable(): Boolean = true // #13에서 구현

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

    override fun preparePassenger(climate: Climate, seatMoveMs: Int, onDone: () -> Unit) {
        onDone() // #10, #11에서 구현
    }

    override fun stopSeat() {} // #11에서 구현

    override fun applyRideHome(profile: Profile) {} // #12에서 구현

    override fun restore(state: PassengerState, onDone: () -> Unit) {
        onDone() // #14에서 구현
    }

    companion object {
        private const val TAG = "MajiControl"
        private const val READ_TIMEOUT_MS = 2000L
    }
}
