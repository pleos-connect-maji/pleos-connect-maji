package com.maji.contract

// 맞이 팀 함수 약속. 첫날 정한 뒤로는 이름과 모양을 바꾸지 않아요.
// 바꿔야 하면 단톡에 먼저 알리고 이 파일을 같이 고쳐요.
// 값 범위는 Pleos 에뮬레이터에서 확인한 기준이에요.

// ───────────── 공통 데이터 (D가 저장, 모두가 씀) ─────────────

/** 조수석 공조 값 한 묶음 */
data class Climate(
    val seatHeat: Int,      // -3..3 (음수 = 통풍, 0 = 끔, 양수 = 열선 단계)
    val temperature: Float  // 15.0..30.0 ℃, 조수석만
)

/** 탑승자 프로필 */
data class Profile(
    val id: Long = 0,
    val name: String,                 // "엄마"
    val winter: Climate,              // 계절 프리셋: 겨울(11~3월)
    val summer: Climate,              // 계절 프리셋: 여름(4~10월)
    val seatMoveMs: Int,              // 시트 이동 시간 (+ 앞으로, - 뒤로, 0 = 안 움직임)
    val closeWindow: Boolean = true,  // 귀갓길에 창문 닫기
    val lightColor: Int? = null,      // 귀갓길 무드등 색 (ARGB), null = 기본값
    val lightBrightness: Int = 30,    // 0..100
    val greetingAudio: String,        // 인사 음성 파일 경로
    val specialGreetingAudio: String? = null, // 오늘만의 인사, 한 번 쓰면 null로 돌려놓기
    val songUri: String? = null,      // 탑승 시 한 번 재생할 노래
    val place: String? = null         // 최근 맞이 장소 (내비 목적지)
)

/** 바꾸기 전 조수석 상태. B가 읽어서 C가 보관하고 하차 때 B가 되돌려요 */
data class PassengerState(
    val seatHeat: Int?,       // 에뮬레이터에서 읽힘
    val temperature: Float?,  // 에뮬레이터에서는 못 읽음(null). A의 변경 감지 값으로 채워요
    val windowPosition: Int?
)

/** 이 차에서 되는 기능 (A가 앱 시작 시 확인) */
data class Capabilities(
    val seatMove: Boolean,
    val seatHeat: Boolean,
    val temperature: Boolean,
    val window: Boolean,
    val light: Boolean
)

// ───────────── A. 차량 신호 ─────────────

interface VehicleSignals {
    fun start(listener: Listener)
    fun stop()
    fun capabilities(): Capabilities

    interface Listener {
        fun onParkChanged(isPark: Boolean) {}               // P단 들어감/풀림
        fun onParkingBrakeChanged(isOn: Boolean) {}
        fun onPassengerDoorChanged(isOpen: Boolean) {}       // 조수석 문
        fun onDrivingRestrictionChanged(isRestricted: Boolean) {} // 주행 중 화면 잠금
        fun onPassengerHeatChanged(level: Int) {}            // 선호 학습용
        fun onPassengerTemperatureChanged(celsius: Float) {} // 선호 학습용
    }
}

// ───────────── B. 차량 제어 ─────────────

interface VehicleControl {
    /** 제어 SDK가 안 되면 false → 대체 모드(화면 표시 + 음성 파일) */
    fun isControlAvailable(): Boolean

    fun readPassengerState(onResult: (PassengerState) -> Unit)

    /** P단 정차 시 자리 준비. 시트 → 열선 → 온도 순서 */
    fun preparePassenger(climate: Climate, seatMoveMs: Int, onDone: () -> Unit = {})

    /** 문이 열리면 C가 바로 부름 */
    fun stopSeat()

    /** 귀갓길: 창문·무드등 */
    fun applyRideHome(profile: Profile)

    /** 최종 하차 후 원래대로 */
    fun restore(state: PassengerState, onDone: () -> Unit = {})
}

// ───────────── C. 맞이 흐름 ─────────────

enum class MajiStep {
    IDLE,        // 맞이 전
    HEADING,     // 탑승자 고르고 이동 중
    PREPARING,   // P단 정차, 자리 준비 중/완료
    BOARDED,     // 조수석 문 열림 → 인사
    RIDING_HOME, // P단 풀림, 귀갓길
    FINISHED     // 최종 하차 버튼 → 복구
}

interface MajiFlow {
    val step: MajiStep
    val profile: Profile?
    fun addListener(onStep: (MajiStep) -> Unit)
    fun start(profile: Profile)  // 맞이 시작 화면에서 탑승자 버튼
    fun cancel()                 // 맞이 취소 → 원래대로
    fun finish()                 // 최종 하차 버튼
}

// ───────────── D. 프로필 ─────────────

interface ProfileRepository {
    suspend fun getAll(): List<Profile>
    suspend fun get(id: Long): Profile?
    suspend fun save(profile: Profile): Long
    suspend fun delete(id: Long)

    /** 오늘 날짜 기준으로 winter/summer 중 하나 */
    fun climateFor(profile: Profile, month: Int): Climate

    /** 탑승자가 직접 바꾼 마지막 값을 해당 계절 프리셋에 반영 */
    suspend fun learn(profileId: Long, month: Int, changed: Climate)
}

// ───────────── E. 음성 ─────────────

interface Greeter {
    /** 인사 재생. specialGreetingAudio가 있으면 그걸 먼저 써요 */
    fun playGreeting(profile: Profile, onDone: () -> Unit = {})

    /** 인사 끝나고 한 번만 재생 */
    fun playSong(profile: Profile)

    /** 짧은 안내음 ("엄마 자리 맞출게요" 등) */
    fun playNotice(audio: String)

    fun stopAll()
}
