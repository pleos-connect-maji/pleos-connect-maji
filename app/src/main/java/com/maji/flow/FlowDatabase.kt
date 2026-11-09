package com.maji.flow

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 맞이 상태 DB 파일 (#26). 탑승자 DB(maji-profile.db)와 따로 둬요.
 * 탑승자 DB는 버전이 바뀌면 지우는 설정이라, 같은 DB에 넣으면 탑승자 데이터가 지워질 수 있어요.
 * 그래서 이 DB는 fallbackToDestructiveMigration을 쓰지 않아요. 표를 바꿀 때는 Migration을 직접 써요.
 */
@Database(entities = [FlowSnapshotEntity::class], version = 1, exportSchema = false)
abstract class FlowDatabase : RoomDatabase() {

    abstract fun flowSnapshotDao(): FlowSnapshotDao

    companion object {
        @Volatile
        private var instance: FlowDatabase? = null

        fun get(context: Context): FlowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FlowDatabase::class.java,
                    "maji-flow.db"
                ).build().also { instance = it }
            }
    }
}
