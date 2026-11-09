package com.maji.flow

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FlowSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FlowSnapshotEntity)

    @Query("SELECT * FROM flow_snapshot WHERE id = 1")
    suspend fun get(): FlowSnapshotEntity?

    @Query("DELETE FROM flow_snapshot")
    suspend fun clear()
}
