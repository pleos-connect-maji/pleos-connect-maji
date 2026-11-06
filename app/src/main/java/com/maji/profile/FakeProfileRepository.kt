package com.maji.profile

import com.maji.contract.Climate
import com.maji.contract.Profile
import com.maji.contract.ProfileRepository
import com.maji.contract.Season

/**
 * DB 없이 메모리에만 저장하는 임시 저장소 (#2).
 * 앱을 끄면 지워져요. Room 버전(#36, #37)이 나오면 그걸로 바꿔 끼우면 돼요.
 *
 * 쓰는 법:
 *   val repo: ProfileRepository = FakeProfileRepository(FakeProfileRepository.samples())
 */
class FakeProfileRepository(
    initial: List<Profile> = emptyList()
) : ProfileRepository {

    private val lock = Any()
    private val profiles = linkedMapOf<Long, Profile>()  // id → 프로필 (넣은 순서 유지)
    private var nextId = 1L

    init {
        initial.forEach { insertOrReplace(it) }
    }

    override suspend fun getAll(): List<Profile> = synchronized(lock) {
        profiles.values.toList()
    }

    override suspend fun get(id: Long): Profile? = synchronized(lock) {
        profiles[id]
    }

    /** id가 0이면 새로 만들고 새 id를, 아니면 그 id를 덮어쓰고 그 id를 돌려줘요 */
    override suspend fun save(profile: Profile): Long = synchronized(lock) {
        insertOrReplace(profile)
    }

    override suspend fun delete(id: Long) {
        synchronized(lock) { profiles.remove(id) }
    }

    override fun seasonFor(month: Int): Season = ProfileRules.seasonFor(month)

    override fun climateFor(profile: Profile, season: Season): Climate? =
        ProfileRules.climateFor(profile, season)

    /** changed에서 null이 아닌 칸만 그 계절에 반영. 다른 계절은 건드리지 않아요 */
    override suspend fun learn(profileId: Long, season: Season, changed: Climate) {
        synchronized(lock) {
            val profile = profiles[profileId] ?: return          // 없는 프로필이면 무시
            val current = ProfileRules.climateFor(profile, season)
            val merged = ProfileRules.merge(current, changed) ?: return  // 바뀐 칸 없음
            if (merged == current) return                        // 기존 값과 같으면 저장 안 함
            insertOrReplace(ProfileRules.withClimate(profile, season, merged))
        }
    }

    // lock 안에서만 불러요
    private fun insertOrReplace(profile: Profile): Long {
        val id = if (profile.id == 0L) nextId else profile.id
        if (id >= nextId) nextId = id + 1
        profiles[id] = ProfileRules.validate(profile).copy(id = id)
        return id
    }

    companion object {
        /** 화면·흐름 테스트용 예시 탑승자 (기획서 시나리오 4번) */
        fun samples(): List<Profile> = listOf(
            Profile(
                name = "엄마",
                winter = Climate(seatHeat = 3, temperature = 24.0f),
                summer = Climate(seatHeat = -2, temperature = 22.0f),
                seatMoveMs = 1500,
                greetingText = "엄마, 오늘도 고생 많으셨어요"
            ),
            Profile(
                name = "딸",
                winter = Climate(seatHeat = 2, temperature = 21.0f),
                summer = null,  // 여름 설정 전 → 여름에 타면 설정 화면
                seatMoveMs = -800
            ),
            Profile(
                name = "여자친구",
                winter = null,
                summer = null   // 처음 타는 사람 → 첫 탑승 설정 화면
            )
        )
    }
}