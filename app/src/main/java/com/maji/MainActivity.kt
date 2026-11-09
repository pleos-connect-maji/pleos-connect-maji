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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maji.contract.MajiStep
import com.maji.contract.Profile
import com.maji.contract.VehicleControl
import com.maji.contract.VehicleSignals
import com.maji.control.PleosVehicleControl
import com.maji.flow.MajiFlowImpl
import com.maji.profile.RoomProfileRepository
import com.maji.signal.PleosVehicleSignals
import com.maji.ui.ProfileEditRoute
import com.maji.ui.ProfileListRoute
import com.maji.ui.ProgressRoute
import com.maji.ui.SeatSetupRoute
import com.maji.ui.needsSeatSetup
import java.time.LocalDate
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
    val scope = rememberCoroutineScope()  // 흐름이 저장을 백그라운드에서 해요 (오늘만의 인사 비우기)
    val flow = remember { MajiFlowImpl(control, repo, greeter, scope) }
    val signals = remember { PleosVehicleSignals(context) }

    var drivingRestricted by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(flow.step) }
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }

    // 자리 설정 화면(#46)에 쓰는 차 상태. 나경 신호 값을 기억해 둬요.
    var isPark by remember { mutableStateOf(false) }
    var doorOpen by remember { mutableStateOf(false) }
    var currentHeat by remember { mutableStateOf<Int?>(null) }
    var currentTemp by remember { mutableStateOf<Float?>(null) }
    var seatSetupDone by remember { mutableStateOf(false) }

    // 앱이 보이는 동안만 신호를 받고, 나갈 때 멈추고 제어를 풀어요.
    // 신호는 흐름에 그대로 넘기고, 화면에 필요한 값만 따로 기억해요.
    DisposableEffect(Unit) {
        flow.addListener {
            step = it
            if (it == MajiStep.HEADING) seatSetupDone = false  // 새 맞이마다 자리 설정을 다시 물어요
        }
        signals.start(object : VehicleSignals.Listener by flow {
            override fun onDrivingRestrictionChanged(isRestricted: Boolean) {
                drivingRestricted = isRestricted
            }

            override fun onParkChanged(park: Boolean) {
                isPark = park
                flow.onParkChanged(park)
            }

            override fun onPassengerDoorChanged(isOpen: Boolean) {
                doorOpen = isOpen
                flow.onPassengerDoorChanged(isOpen)
            }

            override fun onPassengerHeatChanged(level: Int) {
                currentHeat = level
                flow.onPassengerHeatChanged(level)
            }

            override fun onPassengerTemperatureChanged(celsius: Float) {
                currentTemp = celsius
                flow.onPassengerTemperatureChanged(celsius)
            }
        })
        onDispose {
            signals.stop()
            control.release()
        }
    }

    // 자리 설정 화면: 맞이 중이고, 조수석 문이 닫혀 있고, 아직 P단이 아니고, 오늘 계절 값이 없을 때 (원영 요청)
    val active = step != MajiStep.IDLE && step != MajiStep.FINISHED
    // 자리 설정 화면: 조건은 SeatSetupRules.needsSeatSetup에 있어요 (원영 답변 반영: 정차 중에 띄움)
    val seatSetupProfile = flow.profile.takeIf {
        it != null && needsSeatSetup(
            active = active,
            seatSetupDone = seatSetupDone,
            isPark = isPark,
            doorOpen = doorOpen,
            climateToday = repo.climateFor(it, repo.seasonFor(LocalDate.now().monthValue))
        )
    }

    if (seatSetupProfile != null && screen == AppScreen.Home) {
        SeatSetupRoute(
            repo = repo,
            profile = seatSetupProfile,
            currentHeat = currentHeat,
            currentTemp = currentTemp,
            drivingRestricted = drivingRestricted,
            onSaved = { seatSetupDone = true },
            onSkip = { seatSetupDone = true },
            onHeatChange = { control.setSeatHeat(it) },
            onTempChange = { celsius -> if (celsius != null) control.setTemperature(celsius) }
        )
    } else when (val current = screen) {
        AppScreen.Home -> HomeScreen(
            repo = repo,
            flow = flow,
            control = control,
            step = step,
            drivingRestricted = drivingRestricted,
            onManageProfiles = { screen = AppScreen.ProfileList },
            onEditProfile = { screen = AppScreen.ProfileEdit(it.id) }
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
    control: VehicleControl,
    step: MajiStep,
    drivingRestricted: Boolean,
    onManageProfiles: () -> Unit,
    onEditProfile: (Profile) -> Unit
) {
    val active = step != MajiStep.IDLE && step != MajiStep.FINISHED

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onManageProfiles, enabled = !drivingRestricted) { Text("탑승자 관리") }
        }

        // 맞이가 시작되면 진행 화면(#56)을 위에 보여 줘요. 제어가 막힌 차면 대체 모드 문구를 써요.
        if (active) {
            Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                ProgressRoute(
                    flow = flow,
                    profiles = repo,
                    drivingRestricted = drivingRestricted,
                    controlAvailable = control.isControlAvailable()
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            StartRoute(
                profiles = repo,
                flow = flow,
                drivingRestricted = drivingRestricted,
                onEdit = onEditProfile
            )
        }
    }
}
