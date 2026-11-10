package com.maji.flow

import com.maji.contract.Climate
import com.maji.contract.Greeter
import com.maji.contract.MajiStep
import com.maji.contract.PassengerState
import com.maji.contract.Profile
import com.maji.contract.Season
import com.maji.contract.VehicleControl
import com.maji.profile.FakeProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 맞이 상태 저장과 복원 (#26). 가짜 저장소와 가짜 B·E로 확인해요.
class MajiFlowRestoreTest {

    private val mom = Profile(id = 1, name = "엄마", winter = Climate(seatHeat = 2, temperature = 23f), seatMoveMs = 500)
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val now = 1_000_000_000L

    private class FakeStore : FlowStateStore {
        var snapshot: FlowSnapshot? = null
        override suspend fun save(snapshot: FlowSnapshot) { this.snapshot = snapshot }
        override suspend fun load(): FlowSnapshot? = snapshot
        override suspend fun clear() { snapshot = null }
    }

    private class FakeControl : VehicleControl {
        var restored: PassengerState? = null
        var seatMoved = 0
        var seatListener: ((Int) -> Unit)? = null
        override fun seatMovedMs(): Int = seatMoved
        override fun restoreSeatMovedMs(ms: Int) { seatMoved = ms }
        override fun setOnSeatMovedChanged(listener: ((Int) -> Unit)?) { seatListener = listener }
        override fun isControlAvailable() = true
        override fun readPassengerState(onResult: (PassengerState) -> Unit) =
            onResult(PassengerState(seatHeat = 0, temperature = null, windowPosition = 3))
        override fun preparePassenger(climate: Climate, seatMoveMs: Int, onDone: () -> Unit) = onDone()
        override fun stopSeat() {}
        override fun applyRideHome(profile: Profile) {}
        override fun restore(state: PassengerState, onDone: () -> Unit) {
            restored = state
            onDone()
        }
    }

    private class FakeGreeter : Greeter {
        val played = mutableListOf<String>()
        override fun playGreeting(profile: Profile, onDone: () -> Unit) { played.add("greeting") }
        override fun playSong(profile: Profile) { played.add("song") }
        override fun playNotice(audio: String) { played.add(audio) }
        override fun stopAll() {}
    }

    private fun flowWith(store: FlowStateStore, repo: FakeProfileRepository, control: FakeControl, greeter: FakeGreeter) =
        MajiFlowImpl(control, repo, greeter, scope, month = { 12 }, stateStore = store, now = { now })

    @Test
    fun `맞이를 시작하고 준비하면 저장하고, 끝나면 지워요`() {
        val store = FakeStore()
        val control = FakeControl()
        val flow = flowWith(store, FakeProfileRepository(listOf(mom)), control, FakeGreeter())

        flow.start(mom)
        assertEquals(MajiStep.HEADING, store.snapshot!!.step)
        flow.onParkChanged(true)
        assertEquals(Climate(seatHeat = 2, temperature = 23f), store.snapshot!!.preparedClimate)
        assertEquals(Season.WINTER, store.snapshot!!.season)

        flow.finish()
        assertNull(store.snapshot)
    }

    @Test
    fun `저장된 맞이를 되살리면 단계와 준비 값이 돌아오고, 취소하면 원래 상태로 돌려요`() = runBlocking {
        val saved = PassengerState(seatHeat = 0, temperature = null, windowPosition = 3)
        val store = FakeStore().apply {
            snapshot = FlowSnapshot(
                step = MajiStep.PREPARING, profileId = 1, season = Season.WINTER,
                savedState = saved, preparedClimate = Climate(seatHeat = 2, temperature = 23f), savedAtMillis = now - 1000
            )
        }
        val control = FakeControl()
        val flow = flowWith(store, FakeProfileRepository(listOf(mom)), control, FakeGreeter())

        assertTrue(flow.restoreSavedIfFresh())
        assertEquals(MajiStep.PREPARING, flow.step)
        flow.cancel()
        assertEquals(saved, control.restored)
    }

    @Test
    fun `시트 이동 기록을 저장하고, 다시 켰을 때 B에 돌려줘요`() = runBlocking {
        val store = FakeStore()
        val control = FakeControl()
        val flow = flowWith(store, FakeProfileRepository(listOf(mom)), control, FakeGreeter())

        flow.start(mom)
        control.seatMoved = 800
        control.seatListener?.invoke(800)  // 시트가 움직이면 B가 알려줘요
        assertEquals(800, store.snapshot!!.seatMovedMs)

        // 앱을 껐다 켠 것처럼 새 B와 새 흐름으로 되살려요
        val newControl = FakeControl()
        val reopened = flowWith(store, FakeProfileRepository(listOf(mom)), newControl, FakeGreeter())
        assertTrue(reopened.restoreSavedIfFresh())
        assertEquals(800, newControl.seatMoved)
    }

    @Test
    fun `맞이가 끝난 뒤에는 시트 기록이 바뀌어도 저장하지 않아요`() {
        val store = FakeStore()
        val control = FakeControl()
        val flow = flowWith(store, FakeProfileRepository(listOf(mom)), control, FakeGreeter())

        flow.start(mom)
        flow.finish()
        control.seatListener?.invoke(300)
        assertNull(store.snapshot)
    }

    @Test
    fun `복원할 때 인사와 안내음을 다시 재생하지 않아요`() = runBlocking {
        val greeter = FakeGreeter()
        val store = FakeStore().apply {
            snapshot = FlowSnapshot(MajiStep.BOARDED, 1, Season.WINTER, null, null, now - 1000)
        }
        val flow = flowWith(store, FakeProfileRepository(listOf(mom)), FakeControl(), greeter)

        flow.restoreSavedIfFresh()
        assertTrue(greeter.played.isEmpty())
    }

    @Test
    fun `하루가 지난 맞이는 되살리지 않고 버려요`() = runBlocking {
        val store = FakeStore().apply {
            snapshot = FlowSnapshot(MajiStep.HEADING, 1, Season.WINTER, null, null, now - FlowRestoreRules.MAX_AGE_MILLIS - 1)
        }
        val flow = flowWith(store, FakeProfileRepository(listOf(mom)), FakeControl(), FakeGreeter())

        assertFalse(flow.restoreSavedIfFresh())
        assertNull(store.snapshot)
        assertEquals(MajiStep.IDLE, flow.step)
    }

    @Test
    fun `탑승자가 삭제되었어도 맞이는 되살리고, 끝낼 수 있어요`() = runBlocking {
        val saved = PassengerState(seatHeat = 0, temperature = null, windowPosition = 3)
        val store = FakeStore().apply {
            snapshot = FlowSnapshot(MajiStep.RIDING_HOME, 99, Season.WINTER, saved, Climate(2, 23f), now - 1000)
        }
        val control = FakeControl()
        val flow = flowWith(store, FakeProfileRepository(listOf(mom)), control, FakeGreeter())

        assertTrue(flow.restoreSavedIfFresh())
        assertNull(flow.profile)
        flow.finish()
        assertEquals(saved, control.restored)
        assertEquals(MajiStep.FINISHED, flow.step)
    }

    @Test
    fun `탑승 중 바뀐 열선을 저장하고, 되살린 뒤 끝내면 그 계절에 쌓여요`() = runBlocking {
        val repo = FakeProfileRepository(listOf(mom))
        val store = FakeStore()
        val control = FakeControl()
        val first = flowWith(store, repo, control, FakeGreeter())
        first.start(mom)
        first.onParkChanged(true)
        first.onParkingBrakeChanged(true)
        first.onPassengerDoorChanged(true)
        first.onPassengerHeatChanged(1)  // 탑승자가 1단으로 바꿈
        assertEquals(1, store.snapshot!!.learnedHeat)

        // 앱을 껐다가 다시 켠 상황을 흉내 내요
        val second = flowWith(store, repo, control, FakeGreeter())
        assertTrue(second.restoreSavedIfFresh())
        second.finish()

        assertEquals(1, repo.get(1)!!.winter!!.seatHeat)
    }
}
