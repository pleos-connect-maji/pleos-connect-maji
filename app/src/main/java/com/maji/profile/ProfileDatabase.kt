package com.maji.profile

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 프로필 DB 파일 (#37). 앱 안에 "maji-profile.db" 파일로 저장돼서 앱을 꺼도 남아요.
 *
 * version: 표 모양(ProfileEntity 칸)을 바꾸면 1 → 2로 올려요.
 * fallbackToDestructiveMigration: 버전이 바뀌면 옛 데이터를 지우고 새로 만들어요.
 *   (공모전 기간엔 이게 편해요. 지워지면 프로필만 다시 만들면 돼요)
 */
@Database(entities = [ProfileEntity::class], version = 1, exportSchema = false)
abstract class ProfileDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile
        private var instance: ProfileDatabase? = null

        /** 앱 전체에서 DB는 하나만 열어요. 여러 번 불러도 같은 걸 돌려줘요 */
        fun get(context: Context): ProfileDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ProfileDatabase::class.java,
                    "maji-profile.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}