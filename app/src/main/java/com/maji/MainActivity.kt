package com.maji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maji.contract.MajiStep
import com.maji.contract.VehicleSignals
import com.maji.control.PleosVehicleControl
import com.maji.flow.MajiFlowImpl
import com.maji.profile.RoomProfileRepository
import com.maji.signal.PleosVehicleSignals
import com.maji.ui.ProfileEditRoute
import com.maji.ui.ProfileListRoute
import com.maji.ui.StartRoute
import com.maji.voice.PleosGreeter

// 앱 첫 화면. 시작 화면(흐름 시작), 탑승자 관리 화면(#39)을 연결해요.
// 진행 화면(#56)과 자리 설정 화면(#46)은 머지 뒤에 붙여요.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MajiApp()
                }
            }
        }
    }
}

// 화면 전환 상태
private sealed class AppScreen {
    object Home : AppScreen()
    object ProfileList : AppScreen()
    object ProfileAdd : AppScreen()
    data class ProfileEdit(val id: Long) : AppScreen()
}

@Composable
private fun MajiApp() {
    val context = LocalContext.current.applicationContext

    // 차 제어 인스턴스는 하나만 만들어서 흐름과 다른 화면이 같이 써요 (시트 기록이 하나로 유지돼요)
    val repo = remember { RoomProfileRepository.create(context) }
    val control = remember { PleosVehicleControl(context) }
    val greeter = remember { PleosGreeter.create(context) }
    val flow = remember { MajiFlowImpl(control, repo, greeter) }
    val signals = remember { PleosVehicleSignals(context) }

    var drivingRestricted by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(flow.step) }
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }

    // 앱이 보이는 동안만 신호를 받고, 나갈 때 멈추고 제어를 풀어요.
    // 신호는 흐름에 그대로 넘기고, 주행 상태만 화면에도 따로 넘겨요.
    DisposableEffect(Unit) {
        flow.addListener { step = it }
        signals.start(object : VehicleSignals.Listener by flow {
            override fun onDrivingRestrictionChanged(isRestricted: Boolean) {
                drivingRestricted = isRestricted
            }
        })
        onDispose {
            signals.stop()
            control.release()
        }
    }

    when (val current = screen) {
        AppScreen.Home -> HomeScreen(
            repo = repo,
            flow = flow,
            step = step,
            drivingRestricted = drivingRestricted,
            onManageProfiles = { screen = AppScreen.ProfileList }
        )
        AppScreen.ProfileList -> ProfileListRoute(
            repo = repo,
            drivingRestricted = drivingRestricted,
            onAdd = { screen = AppScreen.ProfileAdd },
            onEdit = { screen = AppScreen.ProfileEdit(it) },
            onBack = { screen = AppScreen.Home }
        )
        AppScreen.ProfileAdd -> ProfileEditRoute(
            repo = repo,
            profileId = null,
            drivingRestricted = drivingRestricted,
            onDone = { screen = AppScreen.ProfileList }
        )
        is AppScreen.ProfileEdit -> ProfileEditRoute(
            repo = repo,
            profileId = current.id,
            drivingRestricted = drivingRestricted,
            onDone = { screen = AppScreen.ProfileList }
        )
    }
}

@Composable
private fun HomeScreen(
    repo: RoomProfileRepository,
    flow: MajiFlowImpl,
    step: MajiStep,
    drivingRestricted: Boolean,
    onManageProfiles: () -> Unit
) {
    val active = step != MajiStep.IDLE && step != MajiStep.FINISHED

    Column(modifier = Modifier.fillMaxSize()) {
        // 임시 상태 줄. 진행 화면(#56)이 머지되면 이 자리를 진행 화면으로 바꿔요.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "지금 단계: $step", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (active) {
                Button(onClick = { flow.finish() }, enabled = !drivingRestricted) { Text("최종 하차") }
                OutlinedButton(onClick = { flow.cancel() }, enabled = !drivingRestricted) { Text("맞이 취소") }
            }
            OutlinedButton(onClick = onManageProfiles, enabled = !drivingRestricted) { Text("탑승자 관리") }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            StartRoute(
                profiles = repo,
                flow = flow,
                drivingRestricted = drivingRestricted
            )
        }
    }
}
