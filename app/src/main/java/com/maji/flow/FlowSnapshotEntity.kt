package com.maji.flow

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.maji.contract.Climate
import com.maji.contract.MajiStep
import com.maji.contract.PassengerState
import com.maji.contract.Season

// DB 표 (#26): 맞이는 하나만 있으므로 한 줄(id = 1)만 써요.
// "값 없음"과 "아직 안 함"을 구분하려고 savedSet, preparedSet 칸을 따로 둬요 (profiles 표와 같은 방식).
@Entity(tableName = "flow_snapshot")
data class FlowSnapshotEntity(
    @PrimaryKey val id: Int = 1,
    val step: String,
    val profileId: Long,
    val season: String,
    val savedSet: Boolean,
    val savedSeatHeat: Int?,
    val savedTemp: Float?,
    val savedWindow: Int?,
    val preparedSet: Boolean,
    val preparedSeatHeat: Int?,
    val preparedTemp: Float?,
    val savedAtMillis: Long,
    val learnedHeat: Int?,
    val learnedTemp: Float?,
    val seatMovedMs: Int?,  // version 2에서 추가 (#26, 근희 #86)
    val startTemp: Float?   // version 3에서 추가 (#26): 맞이 시작 때 알던 온도
)

fun FlowSnapshot.toEntity(): FlowSnapshotEntity = FlowSnapshotEntity(
    step = step.name,
    profileId = profileId,
    season = season.name,
    savedSet = savedState != null,
    savedSeatHeat = savedState?.seatHeat,
    savedTemp = savedState?.temperature,
    savedWindow = savedState?.windowPosition,
    preparedSet = preparedClimate != null,
    preparedSeatHeat = preparedClimate?.seatHeat,
    preparedTemp = preparedClimate?.temperature,
    savedAtMillis = savedAtMillis,
    learnedHeat = learnedHeat,
    learnedTemp = learnedTemp,
    seatMovedMs = seatMovedMs,
    startTemp = startTemperature
)

fun FlowSnapshotEntity.toSnapshot(): FlowSnapshot = FlowSnapshot(
    step = MajiStep.valueOf(step),
    profileId = profileId,
    season = Season.valueOf(season),
    savedState = if (savedSet) PassengerState(savedSeatHeat, savedTemp, savedWindow) else null,
    preparedClimate = if (preparedSet) Climate(preparedSeatHeat, preparedTemp) else null,
    savedAtMillis = savedAtMillis,
    learnedHeat = learnedHeat,
    learnedTemp = learnedTemp,
    seatMovedMs = seatMovedMs,
    startTemperature = startTemp
)
