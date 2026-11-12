package com.maji.contract

// 맞이 팀 함수 약속. 첫날 정한 뒤로는 이름과 모양을 바꾸지 않아요.
// 바꿔야 하면 단톡에 먼저 알리고 이 파일을 같이 고쳐요.
// 값 범위는 Pleos 에뮬레이터에서 확인한 기준이에요.

// ───────────── 공통 데이터 (D가 저장, 모두가 씀) ─────────────

/** 계절. 기본은 날짜로(4~10월 SUMMER, 11~3월 WINTER), 설정 화면에서 바꿀 수 있어요 */
enum class Season { SUMMER, WINTER }

/** 조수석 공조 값 한 묶음 */
data class Climate(
    val seatHeat: Int? = null,      // -3..3 (음수 = 통풍, 0 = 끔, 양수 = 열선 단계)
    val temperature: Float? = null  // 15.0..30.0 ℃, 조수석만
)

/** 탑승자 프로필. 첫 탑승 때 탑승자가 만들어요 */
data class Profile(
    val id: Long = 0,
    val name: String,                 // "엄마"
    val winter: Climate? = null,      // null = 겨울 설정을 아직 안 함 -> 겨울에 타면 설정 화면
    // 계절 프리셋: 겨울(11~3월)
    val summer: Climate? = null,              // null = 여름 설정을 아직 안 함 -> 여름에 타면 설정 화면
    // 계절 프리셋: 여름(4~10월)
    val seatMoveMs: Int = 0,          // 계절과 상관없이 하나. 앱 ◀ ▶로 맞춘 시간 (+앞, -뒤, 0 = 안 움직임)
    // 시트 이동 시간 (+ 앞으로, - 뒤로, 0 = 안 움직임)
    val closeWindow: Boolean = true,  // 귀갓길에 창문 닫기
    val lightColor: Int? = null,      // 귀갓길 무드등 색 (ARGB), null = 기본값
    val lightBrightness: Int = 30,    // 0..100
    val greetingText: String? = null, // 운전자가 고르거나 직접 입력한 인사 (TTS로 읽음), null이면 기본 인사 목록의 첫 번째
    val specialGreetingText: String? = null, // 오늘만의 인사, 한 번 쓰면 null로 돌려놓기
    val greetingAudio: String? = null,        // TTS 실패 시 재생할 음성 파일, null이면 E의 기본 파일
    val songUri: String? = null,      // 탑승 시 한 번 재생할 노래
    val place: String? = null         // 최근 맞이 장소 (내비 목적지)
)

/** 바꾸기 전 조수석 상태. B가 읽어서 C가 보관하고 하차 때 B가 되돌려요 */
data class PassengerState(
    val seatHeat: Int?,       // 에뮬레이터에서 읽힘
    val temperature: Float?,  // 에뮬레이터에서는 못 읽음(null). A의 변경 감지 값으로 채워요
    val windowPosition: Int?
)

/** 무드등 한 묶음 (#111). color = ARGB, brightness = 0..100 (앱 기준) */
data class AmbientLight(val color: Int, val brightness: Int)

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

    // ── 자리 설정 화면(#46)용: 누르는 동안 시트 이동, 버튼 즉시 반영 ──
    // 기본 동작을 넣어 둬서 가짜 B(개발 화면·테스트)는 고치지 않아도 돼요.

    /** ◀ ▶ 누를 때. 떼는 신호를 놓쳐도 최대 시간이 지나면 B가 알아서 멈춰요 */
    fun startSeatMove(forward: Boolean) {}

    /** 뗄 때. 이번에 움직인 시간(ms, +앞 -뒤)을 돌려줘요 → 합계를 seatMoveMs로 저장 */
    fun stopSeatMove(): Int {
        stopSeat()
        return 0
    }

    /** 열선 버튼 즉시 반영. -3..3 (음수 = 통풍) */
    fun setSeatHeat(level: Int) {}

    /** 온도 버튼 즉시 반영. 15.0..30.0 ℃ */
    fun setTemperature(celsius: Float) {}

    /** 귀갓길: 창문·무드등 */
    fun applyRideHome(profile: Profile)

    // ── 함께 가는 동안 자리 조절(#111) ──
    // 기본 동작을 넣어 둬서 가짜 B(개발 화면·테스트)는 고치지 않아도 돼요.

    /** 타는 중 무드등 바꾸기. 처음 바꾸기 전 값은 B가 기억했다가 restore 때 되돌려요 */
    fun setAmbientLight(light: AmbientLight) {}

    /** 앱 ◀▶로 시트를 실제로 움직인 횟수 (C가 탈 때와 내릴 때를 비교해서, 탄 뒤 앱으로 움직였을 때만 시트를 기억해요) */
    fun manualSeatMoveCount(): Int = 0

    /** 지금 차의 무드등 읽기 (내릴 때 C가 마지막 값을 기억하려고). 못 읽으면 null. 꼭 한 번 불러요 (메인 스레드) */
    fun readAmbientLight(onResult: (AmbientLight?) -> Unit) = onResult(null)

    /** 최종 하차 후 원래대로. 여러 번 불러도 안전해요 (이미 원위치면 시트는 안 움직임) */
    fun restore(state: PassengerState, onDone: () -> Unit = {})

    // ── 재시작 복원(#26)용: 시트 이동 기록 (설계 문서 docs/flow/restore-design.md 8.7) ──
    // 차에서 시트 위치를 읽을 수 없어서, B가 "원래 자리에서 움직인 시간"을 기억했다가 restore 때 되돌려요.
    // 앱이 꺼지면 이 기록이 사라지므로 C가 flow_snapshot.seatMovedMs에 저장하고, 다시 켤 때 돌려줘요.

    /** 원래 자리에서 지금까지 움직인 시간 (ms, +앞 -뒤) → C가 저장 */
    fun seatMovedMs(): Int = 0

    /** 앱을 다시 켰을 때 저장해 둔 값을 B에 돌려줘요. restore보다 먼저 불러야 시트가 원위치로 돌아가요 */
    fun restoreSeatMovedMs(ms: Int) {}

    /** 시트 이동 기록이 바뀔 때마다 알려줘요 (메인 스레드) → C가 그때 저장. null이면 해제 */
    fun setOnSeatMovedChanged(listener: ((Int) -> Unit)?) {}
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
    suspend fun save(profile: Profile): Long // 첫 설정, 계절 설정도 이걸로 저장
    suspend fun delete(id: Long)

    /** 날짜 기준 기본 계절 (설정 화면에 처음 띄울 값) */
    fun seasonFor(month: Int): Season

    /** 그 계절 값. null이면 아직 설정 안 한 계절 → 설정 화면을 띄워야 해요 */
    fun climateFor(profile: Profile, season: Season): Climate?

    /** 타고 가며 바뀐 칸만 그 계절에 반영 (null 칸은 그대로 둠) */
    suspend fun learn(profileId: Long, season: Season, changed: Climate)
}

// ───────────── E. 음성 ─────────────

interface Greeter {
    /** 인사 재생. specialGreetingText → greetingText → 기본 인사 목록 첫 번째 순서로 TTS로 읽고, TTS가 실패하면 greetingAudio(없으면 기본 파일)를 틀어요 */
    fun playGreeting(profile: Profile, onDone: () -> Unit = {})

    /** 인사 끝나고 한 번만 재생 */
    fun playSong(profile: Profile)

    /** 짧은 안내음 ("엄마 자리 맞출게요" 등) */
    fun playNotice(audio: String)

    fun stopAll()
}
