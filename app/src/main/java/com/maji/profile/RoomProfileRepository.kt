package com.maji.profile

import android.content.Context
import androidx.room.withTransaction
import com.maji.contract.Climate
import com.maji.contract.Profile
import com.maji.contract.ProfileRepository
import com.maji.contract.Season

/**
 * 진짜 저장소 (#37). FakeProfileRepository와 같은 약속(ProfileRepository)이라 바꿔 끼우기만 하면 돼요.
 *
 * 쓰는 법 (Activity 안에서):
 *   val repo: ProfileRepository = RoomProfileRepository.create(applicationContext)
 */
class RoomProfileRepository(private val db: ProfileDatabase) : ProfileRepository {

    private val dao = db.profileDao()

    override suspend fun getAll(): List<Profile> = dao.getAll().map { it.toProfile() }

    override suspend fun get(id: Long): Profile? = dao.get(id)?.toProfile()

    /** 저장 전에 항상 범위 보정 (#4) */
    override suspend fun save(profile: Profile): Long =
        dao.save(ProfileRules.validate(profile).toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)

    override fun seasonFor(month: Int): Season = ProfileRules.seasonFor(month)

    override fun climateFor(profile: Profile, season: Season): Climate? =
        ProfileRules.climateFor(profile, season)

    /** 읽기 → 합치기 → 저장을 한 번에 (중간에 다른 저장이 끼어들지 않게) */
    override suspend fun learn(profileId: Long, season: Season, changed: Climate) {
        db.withTransaction {
            val profile = dao.get(profileId)?.toProfile() ?: return@withTransaction
            val updated = ProfileRules.learned(profile, season, changed) ?: return@withTransaction
            dao.save(ProfileRules.validate(updated).toEntity())
        }
    }

    companion object {
        fun create(context: Context): RoomProfileRepository =
            RoomProfileRepository(ProfileDatabase.get(context))
    }
}