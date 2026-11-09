package com.maji.flow

import com.maji.contract.Climate
import com.maji.contract.Greeter
import com.maji.contract.MajiStep
import com.maji.contract.PassengerState
import com.maji.contract.Profile
import com.maji.contract.VehicleControl
import com.maji.profile.FakeProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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

    // 테스트에서는 저장이 바로 끝나도록 Unconfined 범위를 써요
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val repo = FakeProfileRepository(listOf(mom))

    // 12월 기준 (겨울 설정 사용)
    private val flow = MajiFlowImpl(
        control = control,
        profiles = repo,
        greeter = greeter,
        scope = scope,
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
    fun `P단에 들어가면 주차 브레이크 신호 없이도 자리 준비를 시작해요`() {
        flow.start(mom)
        flow.onParkChanged(true)
        assertEquals(MajiStep.PREPARING, flow.step)
        assertEquals(Climate(seatHeat = 2, temperature = 23f), control.preparedClimate)
    }

    @Test
    fun `P단이 아직 아니면 준비하지 않아요`() {
        flow.start(mom)
        flow.onParkingBrakeChanged(true)
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
            scope = scope,
            month = { 12 }
        )
        flow.start(summerOnly)
        flow.onParkChanged(true)
        flow.onParkingBrakeChanged(true)

        assertEquals(Climate(), control.preparedClimate)
        assertEquals(500, control.preparedSeatMoveMs)
    }

    // ───────────── 학습과 오늘만의 인사 ─────────────

    @Test
    fun `탑승자가 바꾼 열선만 최종 하차 때 그 계절에 저장해요`() = runBlocking {
        flow.start(mom)
        flow.onParkChanged(true)
        flow.onParkingBrakeChanged(true)  // 겨울 설정: 열선 2단, 23℃
        flow.onPassengerDoorChanged(true)
        flow.onPassengerHeatChanged(1)    // 탑승자가 1단으로 바꿈
        flow.finish()

        assertEquals(Climate(seatHeat = 1, temperature = 23f), repo.get(1)!!.winter)
    }

    @Test
    fun `자리 준비 중에 바뀐 값은 탑승자가 바꾼 게 아니라서 기록하지 않아요`() = runBlocking {
        flow.start(mom)
        flow.onParkChanged(true)
        flow.onParkingBrakeChanged(true)
        flow.onPassengerHeatChanged(3)    // 준비 중 변화
        flow.finish()

        assertEquals(Climate(seatHeat = 2, temperature = 23f), repo.get(1)!!.winter)
    }

    @Test
    fun `맞이를 취소하면 바꾼 값을 저장하지 않아요`() = runBlocking {
        flow.start(mom)
        flow.onParkChanged(true)
        flow.onParkingBrakeChanged(true)
        flow.onPassengerDoorChanged(true)
        flow.onPassengerHeatChanged(1)
        flow.cancel()

        assertEquals(Climate(seatHeat = 2, temperature = 23f), repo.get(1)!!.winter)
    }

    @Test
    fun `오늘만의 인사는 한 번 읽고 나면 비워요`() = runBlocking {
        val special = mom.copy(specialGreetingText = "생일 축하해!")
        val specialRepo = FakeProfileRepository(listOf(special))
        val specialFlow = MajiFlowImpl(
            control = control,
            profiles = specialRepo,
            greeter = greeter,
            scope = scope,
            month = { 12 }
        )
        specialFlow.start(special)
        specialFlow.onParkChanged(true)
        specialFlow.onParkingBrakeChanged(true)
        specialFlow.onPassengerDoorChanged(true)

        assertEquals(null, specialRepo.get(1)!!.specialGreetingText)
    }

    // ───────────── 중간 하차와 최종 하차 (#25) ─────────────

    @Test
    fun `귀갓길 중 문 열림·닫힘은 중간 하차로 보고 단계와 복구를 그대로 둬요`() {
        flow.start(mom)
        flow.onParkChanged(true)
        flow.onParkingBrakeChanged(true)
        flow.onPassengerDoorChanged(true)
        flow.onParkChanged(false)
        val stepsBefore = steps.size

        flow.onPassengerDoorChanged(true)
        flow.onPassengerDoorChanged(false)

        assertEquals(MajiStep.RIDING_HOME, flow.step)
        assertEquals(stepsBefore, steps.size)  // 단계가 바뀌지 않았어요
        assertEquals(null, control.restored)   // 복구는 하지 않았어요
        assertEquals(1, control.stopSeatCount) // 정지는 처음 탑승 때 한 번뿐이에요
        assertEquals(listOf("greeting:엄마", "song:엄마"), greeter.played)
    }

    @Test
    fun `최종 하차 버튼을 누르면 FINISHED가 되고 원래 상태로 복구해요`() {
        flow.start(mom)
        flow.onParkChanged(true)
        flow.onParkingBrakeChanged(true)
        flow.onPassengerDoorChanged(true)
        flow.onParkChanged(false)
        flow.onPassengerDoorChanged(true)
        flow.onPassengerDoorChanged(false)

        flow.finish()

        assertEquals(MajiStep.FINISHED, flow.step)
        assertEquals(control.original, control.restored)
    }

    // ───────────── 안내음 (E) ─────────────

    @Test
    fun `자리 준비를 시작할 때와 끝났을 때 안내음을 각각 불러요`() {
        flow.start(mom)
        flow.onParkChanged(true)
        flow.onParkingBrakeChanged(true)

        assertEquals(listOf("audio/prepare_notice.mp3", "audio/prepare_done.mp3"), greeter.notices)
    }

    @Test
    fun `바꿨다가 준비 값으로 되돌리면 저장하지 않아요`() = runBlocking {
        flow.start(mom)
        flow.onParkChanged(true)
        flow.onParkingBrakeChanged(true)
        flow.onPassengerDoorChanged(true)
        flow.onPassengerHeatChanged(1)    // 바꿈
        flow.onPassengerHeatChanged(2)    // 다시 준비 값(2단)으로 돌림
        flow.finish()

        assertEquals(Climate(seatHeat = 2, temperature = 23f), repo.get(1)!!.winter)
    }

    // ───────────── 내비 목적지 (#27) ─────────────

    private class FakeDestinations : DestinationSetter {
        val requested = mutableListOf<String>()
        var cancelled = 0
        override fun requestDestination(place: String) {
            requested.add(place)
        }
        override fun cancelDestination() {
            cancelled++
        }
    }

    @Test
    fun `맞이를 시작하면 프로필의 장소를 목적지로 정하고 끝나면 취소해요`() {
        val nav = FakeDestinations()
        val navFlow = MajiFlowImpl(control, repo, greeter, scope, destinations = nav, month = { 12 })
        navFlow.start(mom.copy(place = "○○역"))
        assertEquals(listOf("○○역"), nav.requested)

        navFlow.finish()
        assertEquals(1, nav.cancelled)
    }

    @Test
    fun `장소가 없으면 목적지를 정하지 않아요`() {
        val nav = FakeDestinations()
        val navFlow = MajiFlowImpl(control, repo, greeter, scope, destinations = nav, month = { 12 })
        navFlow.start(mom)
        assertEquals(emptyList<String>(), nav.requested)
    }

    // ───────────── 맞이 상태 저장 (#26) ─────────────

    private class FakeFlowStateStore : FlowStateStore {
        var snapshot: FlowSnapshot? = null
        override fun save(snapshot: FlowSnapshot) {
            this.snapshot = snapshot
        }
        override fun load(): FlowSnapshot? = snapshot
        override fun clear() {
            snapshot = null
        }
    }

    @Test
    fun `맞이 상태를 저장하고 끝나면 지워요`() {
        val store = FakeFlowStateStore()
        val savedFlow = MajiFlowImpl(control, repo, greeter, scope, stateStore = store, now = { 1000L }, month = { 12 })
        savedFlow.start(mom)
        assertEquals(MajiStep.HEADING, store.load()!!.step)
        assertEquals(1L, store.load()!!.profileId)

        savedFlow.onParkChanged(true)
        assertEquals(Climate(seatHeat = 2, temperature = 23f), store.load()!!.preparedClimate)
        assertEquals(1000L, store.load()!!.savedAtMillis)

        savedFlow.finish()
        assertEquals(null, store.load())
    }

    // ───────────── 가짜 B·E ─────────────

    private class FakeVehicleControl(val original: PassengerState) : VehicleControl {
        var preparedClimate: Climate? = null
        var preparedSeatMoveMs: Int? = null
        var stopSeatCalled = false
        var stopSeatCount = 0
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
            stopSeatCount++
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
        val notices = mutableListOf<String>()
        override fun playNotice(audio: String) {
            notices.add(audio)
        }
        override fun stopAll() {
            stopped = true
        }
    }
}
