package com.maji.flow

import com.maji.contract.Climate
import com.maji.contract.Greeter
import com.maji.contract.MajiFlow
import com.maji.contract.MajiStep
import com.maji.contract.PassengerState
import com.maji.contract.Profile
import com.maji.contract.ProfileRepository
import com.maji.contract.VehicleControl
import com.maji.contract.VehicleSignals
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
    private val month: () -> Int = { LocalDate.now().monthValue }
) : MajiFlow, VehicleSignals.Listener {

    private var current = MajiStep.IDLE
    private var currentProfile: Profile? = null
    private val listeners = mutableListOf<(MajiStep) -> Unit>()

    private var isPark = false
    private var isBrakeOn = false
    private var lastTemperature: Float? = null  // 에뮬레이터에서 못 읽는 원래 온도 대신 쓸 값 (A가 알려줌)
    private var savedState: PassengerState? = null  // 바꾸기 전 조수석 상태, 하차 때 되돌려요

    private var preparedClimate: Climate? = null  // 자리 준비 때 맞춘 값. 이 값과 다르게 바뀐 칸만 학습해요
    private var changedHeat: Int? = null
    private var changedTemperature: Float? = null

    override val step: MajiStep get() = current
    override val profile: Profile? get() = currentProfile

    override fun addListener(onStep: (MajiStep) -> Unit) {
        listeners.add(onStep)
    }

    // 맞이 시작 화면에서 탑승자 버튼
    override fun start(profile: Profile) {
        if (current != MajiStep.IDLE && current != MajiStep.FINISHED) return  // 맞이 중이면 무시
        currentProfile = profile
        isPark = false
        isBrakeOn = false
        savedState = null
        preparedClimate = null
        changedHeat = null
        changedTemperature = null
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

    override fun onParkingBrakeChanged(isOn: Boolean) {
        isBrakeOn = isOn
        checkReadyToPrepare()
    }

    override fun onPassengerDoorChanged(isOpen: Boolean) {
        if (!isOpen || current != MajiStep.PREPARING) return  // 중간 하차는 #25에서 다뤄요
        val p = currentProfile ?: return
        moveTo(MajiStep.BOARDED)
        control.stopSeat()
        greeter.playGreeting(p) {
            greeter.playSong(p)
            clearSpecialGreeting(p)
        }
    }

    override fun onPassengerHeatChanged(level: Int) {
        // 준비 중에 우리가 맞춘 값은 탑승자가 바꾼 게 아니고, 맞춘 값이 없으면 기준이 없어서 기록하지 않아요
        val prepared = preparedClimate?.seatHeat ?: return
        if (isPassengerSeated() && level != prepared) changedHeat = level
    }

    override fun onPassengerTemperatureChanged(celsius: Float) {
        lastTemperature = celsius
        val prepared = preparedClimate?.temperature ?: return
        if (isPassengerSeated() && celsius != prepared) changedTemperature = celsius
    }

    // ───────────── 내부 ─────────────

    // 탑승 뒤(BOARDED, RIDING_HOME)에만 탑승자가 바꾼 값으로 봐요
    private fun isPassengerSeated(): Boolean =
        current == MajiStep.BOARDED || current == MajiStep.RIDING_HOME

    // P단 + 주차 브레이크가 둘 다 켜지면 자리 준비 시작
    private fun checkReadyToPrepare() {
        if (current != MajiStep.HEADING || !isPark || !isBrakeOn) return
        val p = currentProfile ?: return
        moveTo(MajiStep.PREPARING)

        // 준비 전에 원래 상태를 먼저 읽어 둬야 나중에 되돌릴 수 있어요
        control.readPassengerState { state ->
            savedState = state.copy(temperature = state.temperature ?: lastTemperature)
            // 아직 설정 안 한 계절이면 값은 비우고 시트만 움직여요 (설정 화면 연결은 나중에)
            val climate = profiles.climateFor(p, profiles.seasonFor(month())) ?: Climate()
            preparedClimate = climate
            greeter.playNotice(PREPARE_NOTICE)
            control.preparePassenger(climate, p.seatMoveMs)
        }
    }

    // 맞이 끝: 필요하면 바꾼 값을 학습하고, 원래 상태로 되돌리고, 안내음 정리
    private fun endAndRestore(learn: Boolean) {
        if (current == MajiStep.IDLE || current == MajiStep.FINISHED) return
        if (learn) learnChanges()
        savedState?.let { control.restore(it) }
        savedState = null
        greeter.stopAll()
        moveTo(MajiStep.FINISHED)
    }

    // 탑승자가 직접 바꾼 칸만 그 계절에 저장해요. 바뀐 칸이 없으면 저장하지 않아요.
    private fun learnChanges() {
        val p = currentProfile ?: return
        val heat = changedHeat
        val temperature = changedTemperature
        if (heat == null && temperature == null) return
        val season = profiles.seasonFor(month())
        val changed = Climate(seatHeat = heat, temperature = temperature)
        scope.launch { profiles.learn(p.id, season, changed) }
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
        listeners.forEach { it(next) }
    }

    companion object {
        // 자리 맞출게요 안내음 파일 (E 담당 파일 이름에 맞춰 바꿔야 해요)
        private const val PREPARE_NOTICE = "audio/prepare_notice.mp3"
    }
}
