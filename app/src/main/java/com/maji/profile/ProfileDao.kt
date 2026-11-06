package com.maji.profile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DB에 보내는 명령 모음 (#37). DAO = Data Access Object.
 * 함수 몸통은 안 써요. Room(KSP)이 빌드할 때 SQL을 보고 대신 만들어 줘요.
 */
@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY id")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    /** id가 0이면 새 줄 + 새 번호, 같은 id가 있으면 덮어쓰기. 그 줄의 id를 돌려줘요 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: ProfileEntity): Long

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)
}