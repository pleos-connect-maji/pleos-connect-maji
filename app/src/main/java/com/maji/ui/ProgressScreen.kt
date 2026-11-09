package com.maji.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maji.contract.Climate
import com.maji.contract.MajiFlow
import com.maji.contract.MajiStep
import com.maji.contract.ProfileRepository
import java.time.LocalDate

// 맞이 진행 화면 (#24). 지금 단계를 문구로 보여 주고, 최종 하차·취소 버튼으로 맞이를 끝내요.
// 주행 중에는 한 줄 문구만 보여 주고, 버튼은 누를 수 없어요.

/**
 * 화면에 보여 줄 문구. 주행 중이면 한 줄만 고정으로 보여 줘요.
 * fallback(제어가 막힌 차, #13)이면 준비 중·종료 문구를 직접 조작하라는 문구로 바꿔요.
 * 안내음과 탑승·귀갓길 문구는 그대로예요.
 */
fun progressMessage(
    step: MajiStep,
    profileName: String,
    drivingRestricted: Boolean,
    fallback: Boolean = false,
    climate: Climate? = null
): String {
    if (drivingRestricted) return "$profileName 맞이 중 · 도착하면 자리를 준비할게요"
    if (fallback) {
        when (step) {
            MajiStep.PREPARING -> return fallbackPreparingMessage(profileName, climate)
            MajiStep.FINISHED -> return "맞이가 끝났어요! 자리는 직접 원래대로 돌려 주세요!"
            else -> Unit
        }
    }
    return when (step) {
        MajiStep.IDLE -> "맞이를 시작하면 여기에 진행 상황이 보여요"
        MajiStep.HEADING -> "$profileName 맞이 중 · 도착하면 자리를 준비할게요"
        MajiStep.PREPARING -> "$profileName 자리 맞추는 중…"
        MajiStep.BOARDED -> "$profileName 탑승했어요 · 편안한 귀갓길 되세요"
        MajiStep.RIDING_HOME -> "$profileName 귀갓길 중이에요"
        MajiStep.FINISHED -> "맞이가 끝났어요"
    }
}

/**
 * 대체 모드 준비 문구. 둘째 줄은 값이 있는 칸만 보여요(둘 다 없으면 첫 줄만).
 * 열선 음수 = 통풍 N단계, 0 = 열선 끔, 양수 = 열선 N단계.
 */
private fun fallbackPreparingMessage(profileName: String, climate: Climate?): String {
    val heat = climate?.seatHeat?.let {
        when {
            it < 0 -> "통풍 ${-it}단계"
            it == 0 -> "열선 끔"
            else -> "열선 ${it}단계"
        }
    }
    val temperature = climate?.temperature?.let { celsius ->
        val text = if (celsius % 1f == 0f) celsius.toInt().toString() else celsius.toString()
        "${text}℃"
    }
    val detail = listOfNotNull(heat, temperature).joinToString(", ")
    val first = "$profileName 자리는 직접 맞춰 주세요"
    return if (detail.isEmpty()) first else "$first\n$detail"
}

/**
 * 흐름의 단계를 받아서 진행 화면을 띄워요.
 * controlAvailable이 false면 대체 모드 문구를 써요. 준비 문구의 열선·온도는 흐름이 쓰는 계절 값과 같아요.
 */
@Composable
fun ProgressRoute(
    flow: MajiFlow,
    profiles: ProfileRepository,
    drivingRestricted: Boolean,
    controlAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(flow.step) }
    LaunchedEffect(flow) {
        flow.addListener { step = it }
    }
    val profile = flow.profile
    val climate = profile?.let { profiles.climateFor(it, profiles.seasonFor(LocalDate.now().monthValue)) }
    ProgressScreen(
        step = step,
        profileName = profile?.name.orEmpty(),
        drivingRestricted = drivingRestricted,
        fallback = !controlAvailable,
        climate = climate,
        onFinish = { flow.finish() },
        onCancel = { flow.cancel() },
        modifier = modifier
    )
}

/** 화면만 그려요. 값과 이벤트를 밖에서 받아서 미리보기와 테스트에도 쓸 수 있어요. */
@Composable
fun ProgressScreen(
    step: MajiStep,
    profileName: String,
    drivingRestricted: Boolean,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    fallback: Boolean = false,
    climate: Climate? = null
) {
    val active = step != MajiStep.IDLE && step != MajiStep.FINISHED

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = progressMessage(step, profileName, drivingRestricted, fallback, climate),
            style = MaterialTheme.typography.headlineSmall
        )

        if (active) {
            Button(
                onClick = onFinish,
                enabled = !drivingRestricted,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
            ) {
                Text(text = "최종 하차", style = MaterialTheme.typography.titleLarge)
            }
            Button(
                onClick = onCancel,
                enabled = !drivingRestricted,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
            ) {
                Text(text = "맞이 취소", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
