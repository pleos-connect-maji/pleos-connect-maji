package com.maji.profile

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.maji.contract.Climate
import com.maji.contract.Profile

/**
 * DB 표(table) 한 줄 = 탑승자 한 명 (#37).
 * Profile을 그대로 못 넣는 이유: winter/summer가 "Climate 묶음"이라 칸으로 풀어서 저장해요.
 *
 * winterSet/summerSet을 따로 둔 이유:
 *   "겨울 설정 안 함(null)"과 "겨울 설정 했는데 열선·온도 둘 다 그대로 두기"를 구분하려고요.
 *   heat, temp 칸만 있으면 둘 다 빈 칸이라 구분이 안 돼요.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,  // 0으로 넣으면 DB가 1, 2, 3… 번호를 붙여요
    val name: String,

    val winterSet: Boolean,
    val winterHeat: Int?,
    val winterTemp: Float?,

    val summerSet: Boolean,
    val summerHeat: Int?,
    val summerTemp: Float?,

    val seatMoveMs: Int,
    val closeWindow: Boolean,
    val lightColor: Int?,
    val lightBrightness: Int,
    val greetingText: String?,
    val specialGreetingText: String?,
    val greetingAudio: String?,
    val songUri: String?,
    val place: String?
)

/** Profile → DB 한 줄 (저장할 때) */
fun Profile.toEntity(): ProfileEntity = ProfileEntity(
    id = id,
    name = name,
    winterSet = winter != null,
    winterHeat = winter?.seatHeat,
    winterTemp = winter?.temperature,
    summerSet = summer != null,
    summerHeat = summer?.seatHeat,
    summerTemp = summer?.temperature,
    seatMoveMs = seatMoveMs,
    closeWindow = closeWindow,
    lightColor = lightColor,
    lightBrightness = lightBrightness,
    greetingText = greetingText,
    specialGreetingText = specialGreetingText,
    greetingAudio = greetingAudio,
    songUri = songUri,
    place = place
)

/** DB 한 줄 → Profile (꺼낼 때) */
fun ProfileEntity.toProfile(): Profile = Profile(
    id = id,
    name = name,
    winter = if (winterSet) Climate(seatHeat = winterHeat, temperature = winterTemp) else null,
    summer = if (summerSet) Climate(seatHeat = summerHeat, temperature = summerTemp) else null,
    seatMoveMs = seatMoveMs,
    closeWindow = closeWindow,
    lightColor = lightColor,
    lightBrightness = lightBrightness,
    greetingText = greetingText,
    specialGreetingText = specialGreetingText,
    greetingAudio = greetingAudio,
    songUri = songUri,
    place = place
)