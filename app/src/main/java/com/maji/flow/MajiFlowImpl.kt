package com.maji.flow

import com.maji.contract.AmbientLight
import com.maji.contract.Climate
import com.maji.contract.Greeter
import com.maji.contract.MajiFlow
import com.maji.contract.MajiStep
import com.maji.contract.PassengerState
import com.maji.contract.Profile
import com.maji.contract.ProfileRepository
import com.maji.contract.Season
import com.maji.contract.VehicleControl
import com.maji.contract.VehicleSignals
import com.maji.voice.Notices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

// C. 맞이 흐름 (#22). 차 신호(A)를 받아 단계를 넘기고, 단계마다 B·D·E 함수를 불러요.
// B·D·E는 서로 부르지 않고 여기서만 불러요.
class MajiFlowImpl(
    private val control: VehicleControl,
    private val profiles: ProfileRepository,
    private val greeter: Greeter,
    private val scope: CoroutineScope,  // 저장(suspend)을 부르는 곳
    private val month: () -> Int = { LocalDate.now().monthValue },
    private val destinations: DestinationSetter? = null,  // 맞이 장소 내비 목적지 (#27)
    private val stateStore: FlowStateStore? = null,  // 맞이 상태 저장 (#26)
    private val now: () -> Long = { System.currentTimeMillis() }
) : MajiFlow, VehicleSignals.Listener {

    private var current = MajiStep.IDLE
    private var currentProfile: Profile? = null
    private val listeners = mutableListOf<(MajiStep) -> Unit>()

    private var isPark = false
    private var lastTemperature: Float? = null  // 에뮬레이터에서 못 읽는 원래 온도 대신 쓸 값 (A가 알려줌)
    private var startTemperature: Float? = null  // 맞이를 시작할 때 알던 온도 (준비 때 바뀌기 전 값). 재시작 뒤에도 씀
    private var savedState: PassengerState? = null  // 바꾸기 전 조수석 상태, 하차 때 되돌려요

    private var preparedClimate: Climate? = null  // 자리 준비 때 맞춘 값. 이 값과 다르게 바뀐 칸만 학습해요
    private var changedHeat: Int? = null
    private var changedTemperature: Float? = null
    private var currentSeason: Season? = null  // 맞이를 시작할 때 정한 계절 (학습과 복원에 씀)
    private var finishing = false  // 내릴 때 무드등 읽기·복구가 끝나기 전엔 새 맞이를 받지 않아요 (#112 리뷰)
    private var manualMovesAtBoarding: Int? = null  // 탈 때 앱 ◀▶ 이동 횟수 (#112 리뷰)
    private var seatAtBoarding: Int? = null  // 문이 열려 시트를 멈춘 순간의 시트 기록. 내릴 때와 다르면 탄 뒤 움직인 것 (#111)

    init {
        // 시트 이동 기록이 바뀌면 저장해요 (#26, 근희 #86). 맞이 중일 때만 저장해요 (끝난 뒤에는 저장 기록을 다시 만들지 않아요)
        control.setOnSeatMovedChanged { if (isInProgress()) persist() }
    }

    override val step: MajiStep get() = current
    override val profile: Profile? get() = currentProfile

    override fun addListener(onStep: (MajiStep) -> Unit) {
        listeners.add(onStep)
    }

    // 맞이 시작 화면에서 탑승자 버튼
    override fun start(profile: Profile) {
        if (current != MajiStep.IDLE && current != MajiStep.FINISHED) return  // 맞이 중이면 무시
        if (finishing) return  // 지난 맞이 복구가 아직 안 끝났어요 (최대 2초)
        currentProfile = profile
        isPark = false
        savedState = null
        startTemperature = lastTemperature  // 준비 전에 알던 온도를 원래 값 후보로 잡아요 (#26)
        preparedClimate = null
        changedHeat = null
        changedTemperature = null
        seatAtBoarding = null
        manualMovesAtBoarding = null
        profile.place?.let { destinations?.requestDestination(it) }
        currentSeason = profiles.seasonFor(month())
        moveTo(MajiStep.HEADING)
    }

    // 맞이 취소 → 원래대로 (학습은 하지 않아요)
    override fun cancel() = endAndRestore(learn = false)

    // 최종 하차 버튼 → 바꾼 값을 학습하고 원래대로
    override fun finish() = endAndRestore(learn = true)

    // ───────────── A 신호 받기 ─────────────

    override fun onParkChanged(isPark: Boolean) {
        this.isPark = isPark
        if (!isPark && current == MajiStep.BOARDED) {
            moveTo(MajiStep.RIDING_HOME)
            currentProfile?.let { control.applyRideHome(it) }
        }
        checkReadyToPrepare()
    }

    override fun onPassengerDoorChanged(isOpen: Boolean) {
        if (!isOpen || current != MajiStep.PREPARING) return  // 중간 하차는 #25에서 다뤄요
        val p = currentProfile ?: return
        moveTo(MajiStep.BOARDED)
        control.stopSeat()
        seatAtBoarding = control.seatMovedMs()  // 멈춘 뒤라 정산이 끝난 값이에요
        manualMovesAtBoarding = control.manualSeatMoveCount()
        greeter.playGreeting(p) {
            greeter.playSong(p)
            clearSpecialGreeting(p)
        }
    }

    // 탄 뒤(BOARDED, RIDING_HOME)에 바뀐 값만 탑승자가 바꾼 걸로 봐요. 준비 중에 우리가 맞춘 값은 빼요.
    // 처음 맞이(준비 값 없음)여도 타는 동안 바꾼 마지막 값을 기록해서 내릴 때 저장해요 (#111)
    override fun onPassengerHeatChanged(level: Int) {
        if (!isPassengerSeated()) return
        changedHeat = RideLearnRules.recordAfterBoarding(level, preparedClimate?.seatHeat)  // 준비 값으로 되돌리면 기록을 지워요
        persist()  // 학습 값을 저장해요 (#26)
    }

    override fun onPassengerTemperatureChanged(celsius: Float) {
        lastTemperature = celsius
        if (!isPassengerSeated()) return
        changedTemperature = RideLearnRules.recordAfterBoarding(celsius, preparedClimate?.temperature)
        persist()  // 학습 값을 저장해요 (#26)
    }

    // ───────────── 내부 ─────────────

    private fun isInProgress(): Boolean = current != MajiStep.IDLE && current != MajiStep.FINISHED

    // 탑승 뒤(BOARDED, RIDING_HOME)에만 탑승자가 바꾼 값으로 봐요
    private fun isPassengerSeated(): Boolean =
        current == MajiStep.BOARDED || current == MajiStep.RIDING_HOME

    // P단에 들어가면 자리 준비 시작. 주차 브레이크 신호는 A가 보내지 않아서 쓰지 않아요.
    private fun checkReadyToPrepare() {
        if (current != MajiStep.HEADING || !isPark) return
        val p = currentProfile ?: return
        scope.launch {
            // 맞이를 시작한 뒤에 저장된 값(자리 설정 등)도 반영하도록, 준비 직전에 탑승자 정보를 다시 읽어요
            val fresh = profiles.get(p.id) ?: p
            if (current != MajiStep.HEADING) return@launch
            currentProfile = fresh
            moveTo(MajiStep.PREPARING)

            // 준비 전에 원래 상태를 먼저 읽어 둬야 나중에 되돌릴 수 있어요
            control.readPassengerState { state ->
                savedState = state.copy(temperature = state.temperature ?: startTemperature ?: lastTemperature)
                // 아직 설정 안 한 계절이면 값은 비우고 시트만 움직여요 (설정 화면 연결은 나중에)
                val climate = profiles.climateFor(fresh, profiles.seasonFor(month())) ?: Climate()
                preparedClimate = climate
                persist()  // 준비 값이 생겼으니 저장해요 (#26)
                greeter.playNotice(Notices.PREPARE)
                control.preparePassenger(climate, fresh.seatMoveMs) {
                    // 준비가 끝났을 때 아직 준비 중이면 완료 안내음 (문이 먼저 열렸으면 안 틀어요)
                    if (current == MajiStep.PREPARING) greeter.playNotice(Notices.PREPARE_DONE)
                }
            }
        }
    }

    // 맞이 끝: 필요하면 마지막 값을 기억하고, 원래 상태로 되돌리고, 안내음 정리.
    // 무드등은 함께 가는 중까지 갔을 때만 차에서 읽어요 (그 전엔 차의 원래 조명이라 기억하면 안 돼요).
    // 읽은 뒤에 복구해야 탑승자가 고른 색이 남아 있어요. B가 꼭 한 번(최대 2초 안) 알려줘요.
    private fun endAndRestore(learn: Boolean) {
        if (current == MajiStep.IDLE || current == MajiStep.FINISHED) return
        val p = currentProfile
        val state = savedState
        val readLight = learn && current == MajiStep.RIDING_HOME
        val climate = Climate(seatHeat = changedHeat, temperature = changedTemperature)
        val movedInApp = manualMovesAtBoarding?.let { control.manualSeatMoveCount() != it } ?: false
        val seatMs = RideLearnRules.seatToLearn(seatAtBoarding, control.seatMovedMs(), movedInApp)
        val season = currentSeason ?: profiles.seasonFor(month())
        savedState = null
        greeter.stopAll()
        destinations?.cancelDestination()
        moveTo(MajiStep.FINISHED)

        fun restoreNow() { state?.let { control.restore(it) } }
        if (!learn || p == null) return restoreNow()
        if (readLight) {
            finishing = true
            control.readAmbientLight { light ->
                finishing = false
                learnLast(p, season, climate, seatMs, light)
                restoreNow()
            }
        } else {
            learnLast(p, season, climate, seatMs, null)
            restoreNow()
        }
    }

    // 내릴 때 마지막 값 저장 (#111): 열선·온도는 그 계절에, 시트·무드등은 프로필에.
    // 저장이 서로 덮어쓰지 않게 한 코루틴 안에서 차례로 해요.
    private fun learnLast(p: Profile, season: Season, climate: Climate, seatMs: Int?, light: AmbientLight?) {
        scope.launch {
            if (climate.seatHeat != null || climate.temperature != null) profiles.learn(p.id, season, climate)
            if (seatMs != null || light != null) {
                val fresh = profiles.get(p.id) ?: return@launch
                RideLearnRules.withRide(fresh, seatMs, light)?.let { profiles.save(it) }
            }
        }
    }

    // 오늘만의 인사는 한 번 읽고 나면 비워요 (#38)
    private fun clearSpecialGreeting(p: Profile) {
        if (p.specialGreetingText == null) return
        scope.launch {
            profiles.get(p.id)?.let { profiles.save(it.copy(specialGreetingText = null)) }
        }
    }

    private fun moveTo(next: MajiStep) {
        current = next
        if (next == MajiStep.IDLE || next == MajiStep.FINISHED) forgetSaved() else persist()
        listeners.forEach { it(next) }
    }

    // 지금 맞이 상태를 저장해요 (#26). 맞이 대상이나 계절이 없으면 저장하지 않아요.
    private fun persist() {
        val id = currentProfile?.id ?: return
        val season = currentSeason ?: return
        val snapshot = FlowSnapshot(
            step = current,
            profileId = id,
            season = season,
            savedState = savedState,
            preparedClimate = preparedClimate,
            savedAtMillis = now(),
            learnedHeat = changedHeat,
            learnedTemp = changedTemperature,
            seatMovedMs = control.seatMovedMs(),
            startTemperature = startTemperature
        )
        scope.launch { stateStore?.save(snapshot) }
    }

    // 맞이가 끝나면 저장된 맞이를 지워요 (#26)
    private fun forgetSaved() {
        scope.launch { stateStore?.clear() }
    }

    // 앱을 다시 켰을 때 저장된 맞이를 되살려요 (#26). 하루가 지났으면 버려요.
    // 소리(인사, 안내음, 노래)는 다시 재생하지 않아요 (이현 답변).
    // 탑승자 정보는 DB 최신 값을 써요. 없으면 null로 두고, 학습만 건너뛰어요 (원영 답변).
    suspend fun restoreSavedIfFresh(): Boolean {
        val store = stateStore ?: return false
        if (current != MajiStep.IDLE) return false
        val snapshot = store.load() ?: return false
        if (!FlowRestoreRules.isRestorable(snapshot, now())) {
            store.clear()
            return false
        }
        currentProfile = profiles.get(snapshot.profileId)
        currentSeason = snapshot.season
        startTemperature = snapshot.startTemperature
        // 저장된 온도를 못 읽었으면(null) 시작할 때 알던 온도로 채워요 (#26)
        savedState = snapshot.savedState?.let { s ->
            if (s.temperature == null && snapshot.startTemperature != null) s.copy(temperature = snapshot.startTemperature) else s
        }
        preparedClimate = snapshot.preparedClimate
        changedHeat = snapshot.learnedHeat  // 저장된 학습 값을 이어받아요 (#26)
        changedTemperature = snapshot.learnedTemp
        // 시트 기록은 restore() 전에 B에 돌려줘야 시트가 원위치로 돌아가요 (근희 안내)
        snapshot.seatMovedMs?.let { control.restoreSeatMovedMs(it) }
        // 저장 기록을 다시 쓰지 않도록, 단계만 바꾸고 알려 줘요
        current = snapshot.step
        listeners.forEach { it(snapshot.step) }
        return true
    }
}
