package com.maji.flow

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 맞이 상태 DB 파일 (#26). 탑승자 DB(maji-profile.db)와 따로 둬요.
 * 탑승자 DB는 버전이 바뀌면 지우는 설정이라, 같은 DB에 넣으면 탑승자 데이터가 지워질 수 있어요.
 * 그래서 이 DB는 fallbackToDestructiveMigration을 쓰지 않아요. 표를 바꿀 때는 Migration을 직접 써요.
 */
@Database(entities = [FlowSnapshotEntity::class], version = 3, exportSchema = false)
abstract class FlowDatabase : RoomDatabase() {

    abstract fun flowSnapshotDao(): FlowSnapshotDao

    companion object {
        @Volatile
        private var instance: FlowDatabase? = null

        // v1 → v2: 시트 이동 기록 칸 추가 (#26). 기존 맞이 기록은 그대로 두고 칸만 늘려요.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flow_snapshot ADD COLUMN seatMovedMs INTEGER")
            }
        }

        // v2 → v3: 맞이 시작 때 온도 칸 추가 (#26). 기존 칸은 그대로 둬요.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flow_snapshot ADD COLUMN startTemp REAL")
            }
        }

        fun get(context: Context): FlowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FlowDatabase::class.java,
                    "maji-flow.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
