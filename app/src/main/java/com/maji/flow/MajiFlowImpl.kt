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
import java.time.LocalDate

// C. 맞이 흐름 (#22). 차 신호(A)를 받아 단계를 넘기고, 단계마다 B·D·E 함수를 불러요.
// B·D·E는 서로 부르지 않고 여기서만 불러요.
class MajiFlowImpl(
    private val control: VehicleControl,
    private val profiles: ProfileRepository,
    private val greeter: Greeter,
    private val month: () -> Int = { LocalDate.now().monthValue }
) : MajiFlow, VehicleSignals.Listener {

    private var current = MajiStep.IDLE
    private var currentProfile: Profile? = null
    private val listeners = mutableListOf<(MajiStep) -> Unit>()

    private var isPark = false
    private var lastTemperature: Float? = null  // 에뮬레이터에서 못 읽는 원래 온도 대신 쓸 값 (A가 알려줌)
    private var savedState: PassengerState? = null  // 바꾸기 전 조수석 상태, 하차 때 되돌려요

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
        savedState = null
        moveTo(MajiStep.HEADING)
    }

    // 맞이 취소 → 원래대로
    override fun cancel() = endAndRestore()

    // 최종 하차 버튼
    override fun finish() = endAndRestore()

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
        greeter.playGreeting(p) { greeter.playSong(p) }
    }

    override fun onPassengerTemperatureChanged(celsius: Float) {
        lastTemperature = celsius
    }

    // ───────────── 내부 ─────────────

    // P단에 들어가면 자리 준비 시작. 주차 브레이크 신호는 A가 보내지 않아서 쓰지 않아요.
    private fun checkReadyToPrepare() {
        if (current != MajiStep.HEADING || !isPark) return
        val p = currentProfile ?: return
        moveTo(MajiStep.PREPARING)

        // 준비 전에 원래 상태를 먼저 읽어 둬야 나중에 되돌릴 수 있어요
        control.readPassengerState { state ->
            savedState = state.copy(temperature = state.temperature ?: lastTemperature)
            // 아직 설정 안 한 계절이면 값은 비우고 시트만 움직여요 (설정 화면 연결은 나중에)
            val climate = profiles.climateFor(p, profiles.seasonFor(month())) ?: Climate()
            greeter.playNotice(PREPARE_NOTICE)
            control.preparePassenger(climate, p.seatMoveMs)
        }
    }

    // 맞이 끝: 원래 상태로 되돌리고 안내음 정리
    private fun endAndRestore() {
        if (current == MajiStep.IDLE || current == MajiStep.FINISHED) return
        savedState?.let { control.restore(it) }
        savedState = null
        greeter.stopAll()
        moveTo(MajiStep.FINISHED)
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
