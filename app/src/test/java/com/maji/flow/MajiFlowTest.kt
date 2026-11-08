package com.maji.flow

import com.maji.contract.Climate
import com.maji.contract.Greeter
import com.maji.contract.MajiStep
import com.maji.contract.PassengerState
import com.maji.contract.Profile
import com.maji.contract.VehicleControl
import com.maji.profile.FakeProfileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// flow 모듈 단위 테스트 (#22). 실제 차 없이 가짜 B·E로 단계 흐름만 확인해요.
class MajiFlowTest {

    private val mom = Profile(
        id = 1,
        name = "엄마",
        winter = Climate(seatHeat = 2, temperature = 23f),
        seatMoveMs = 500
    )

    private val control = FakeVehicleControl(
        original = PassengerState(seatHeat = 0, temperature = null, windowPosition = 3)
    )
    private val greeter = FakeGreeter()
    private val steps = mutableListOf<MajiStep>()

    // 12월 기준 (겨울 설정 사용)
    private val flow = MajiFlowImpl(
        control = control,
        profiles = FakeProfileRepository(listOf(mom)),
        greeter = greeter,
        month = { 12 }
    ).also { it.addListener { step -> steps.add(step) } }

    // ───────────── 한 바퀴 ─────────────

    @Test
    fun `대기에서 종료까지 한 바퀴 돌면 단계마다 맞는 함수가 불려요`() {
        flow.start(mom)
        assertEquals(MajiStep.HEADING, flow.step)

        flow.onParkChanged(true)
        flow.onParkingBrakeChanged(true)
        assertEquals(MajiStep.PREPARING, flow.step)
        assertEquals(Climate(seatHeat = 2, temperature = 23f), control.preparedClimate)
        assertEquals(500, control.preparedSeatMoveMs)

        flow.onPassengerDoorChanged(true)
        assertEquals(MajiStep.BOARDED, flow.step)
        assertTrue(control.stopSeatCalled)
        assertEquals(listOf("greeting:엄마", "song:엄마"), greeter.played)

        flow.onParkChanged(false)
        assertEquals(MajiStep.RIDING_HOME, flow.step)
        assertEquals(listOf("엄마"), control.rideHomeFor)

        flow.finish()
        assertEquals(MajiStep.FINISHED, flow.step)
        assertEquals(control.original, control.restored)
        assertTrue(greeter.stopped)
        assertEquals(
            listOf(MajiStep.HEADING, MajiStep.PREPARING, MajiStep.BOARDED, MajiStep.RIDING_HOME, MajiStep.FINISHED),
            steps
        )
    }

    // ───────────── 조건 ─────────────

    @Test
    fun `P단만 들어가고 브레이크가 안 걸리면 준비하지 않아요`() {
        flow.start(mom)
        flow.onParkChanged(true)
        assertEquals(MajiStep.HEADING, flow.step)
        assertEquals(null, control.preparedClimate)
    }

    @Test
    fun `정차 전에 문이 열리면 무시해요`() {
        flow.start(mom)
        flow.onPassengerDoorChanged(true)
        assertEquals(MajiStep.HEADING, flow.step)
        assertTrue(greeter.played.isEmpty())
    }

    @Test
    fun `귀갓길 중 문이 열려도 인사를 다시 하지 않아요`() {
        flow.start(mom)
        flow.onParkChanged(true)
        flow.onParkingBrakeChanged(true)
        flow.onPassengerDoorChanged(true)
        flow.onParkChanged(false)

        flow.onPassengerDoorChanged(false)
        flow.onPassengerDoorChanged(true)

        assertEquals(MajiStep.RIDING_HOME, flow.step)
        assertEquals(listOf("greeting:엄마", "song:엄마"), greeter.played)
    }

    @Test
    fun `맞이 중에 다시 시작해도 무시해요`() {
        flow.start(mom)
        flow.start(mom.copy(name = "딸"))
        assertEquals("엄마", flow.profile?.name)
    }

    // ───────────── 취소와 값 ─────────────

    @Test
    fun `준비 중에 취소하면 원래 상태로 되돌려요`() {
        flow.start(mom)
        flow.onParkChanged(true)
        flow.onParkingBrakeChanged(true)
        flow.cancel()

        assertEquals(MajiStep.FINISHED, flow.step)
        assertEquals(control.original, control.restored)
    }

    @Test
    fun `온도는 A가 알려준 마지막 값으로 채워서 저장해요`() {
        flow.start(mom)
        flow.onPassengerTemperatureChanged(21.5f)
        flow.onParkChanged(true)
        flow.onParkingBrakeChanged(true)
        flow.finish()

        assertEquals(21.5f, control.restored?.temperature)
    }

    @Test
    fun `설정 안 한 계절이면 값 없이 시트만 움직여요`() {
        val summerOnly = mom.copy(winter = null, summer = Climate(seatHeat = 1, temperature = 20f))
        val flow = MajiFlowImpl(
            control = control,
            profiles = FakeProfileRepository(listOf(summerOnly)),
            greeter = greeter,
            month = { 12 }
        )
        flow.start(summerOnly)
        flow.onParkChanged(true)
        flow.onParkingBrakeChanged(true)

        assertEquals(Climate(), control.preparedClimate)
        assertEquals(500, control.preparedSeatMoveMs)
    }

    // ───────────── 가짜 B·E ─────────────

    private class FakeVehicleControl(val original: PassengerState) : VehicleControl {
        var preparedClimate: Climate? = null
        var preparedSeatMoveMs: Int? = null
        var stopSeatCalled = false
        val rideHomeFor = mutableListOf<String>()
        var restored: PassengerState? = null

        override fun isControlAvailable() = true
        override fun readPassengerState(onResult: (PassengerState) -> Unit) = onResult(original)
        override fun preparePassenger(climate: Climate, seatMoveMs: Int, onDone: () -> Unit) {
            preparedClimate = climate
            preparedSeatMoveMs = seatMoveMs
            onDone()
        }
        override fun stopSeat() {
            stopSeatCalled = true
        }
        override fun applyRideHome(profile: Profile) {
            rideHomeFor.add(profile.name)
        }
        override fun restore(state: PassengerState, onDone: () -> Unit) {
            restored = state
            onDone()
        }
    }

    private class FakeGreeter : Greeter {
        val played = mutableListOf<String>()
        var stopped = false
        override fun playGreeting(profile: Profile, onDone: () -> Unit) {
            played.add("greeting:${profile.name}")
            onDone()
        }
        override fun playSong(profile: Profile) {
            played.add("song:${profile.name}")
        }
        override fun playNotice(audio: String) {}
        override fun stopAll() {
            stopped = true
        }
    }
}
