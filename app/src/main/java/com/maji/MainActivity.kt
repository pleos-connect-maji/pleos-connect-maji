package com.maji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.maji.flow.FlowDatabase
import com.maji.flow.MajiFlowImpl
import com.maji.flow.RoomFlowStateStore
import kotlinx.coroutines.launch
import com.maji.profile.RoomProfileRepository
import com.maji.signal.PleosVehicleSignals
import com.maji.ui.ProfileEditRoute
import com.maji.ui.ProgressRoute
import com.maji.ui.SeatSetupRoute
import com.maji.ui.needsSeatSetup
import java.time.LocalDate
import com.maji.ui.StartRoute
import com.maji.voice.PleosGreeter
import com.maji.ui.theme.MajiTheme
import com.maji.ui.theme.ThemeModeSwitch
import com.maji.ui.theme.ThemePreference

// 앱 첫 화면. 홈(사람 카드, 흐름 시작), 진행·완료 화면, 자리 설정, 사람 추가·수정을 연결해요.
// 테마(#107): 화면 밝기는 자동(차의 낮/밤)·낮·밤 중에 고르고, 고른 값은 저장해요.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePref = ThemePreference(this)
        setContent {
            var themeMode by remember { mutableStateOf(themePref.load()) }
            MajiTheme(themeMode) {
                MajiApp(themeSwitch = {
                    ThemeModeSwitch(themeMode, onChange = { themeMode = it; themePref.save(it) }, modifier = it)
                })
            }
        }
    }
}

// 화면 전환 상태
private sealed class AppScreen {
    object Home : AppScreen()
    object ProfileAdd : AppScreen()
    data class ProfileEdit(val id: Long) : AppScreen()
}

@Composable
private fun MajiApp(themeSwitch: @Composable (Modifier) -> Unit) {
    val context = LocalContext.current.applicationContext

    // 차 제어 인스턴스는 하나만 만들어서 흐름과 다른 화면이 같이 써요 (시트 기록이 하나로 유지돼요)
    val repo = remember { RoomProfileRepository.create(context) }
    val control = remember { PleosVehicleControl(context) }
    val greeter = remember { PleosGreeter.create(context) }
    val scope = rememberCoroutineScope()  // 흐름이 저장을 백그라운드에서 해요 (오늘만의 인사 비우기)
    val flowStore = remember { RoomFlowStateStore(FlowDatabase.get(context).flowSnapshotDao()) }  // 맞이 상태 저장 (#26)
    val flow = remember { MajiFlowImpl(control, repo, greeter, scope, stateStore = flowStore) }
    val signals = remember { PleosVehicleSignals(context) }

    var drivingRestricted by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(flow.step) }
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    var showDone by remember { mutableStateOf(false) }  // "다 내렸어요"로 끝났으면 완료 화면을 보여 줘요

    // 자리 설정 화면(#46)에 쓰는 차 상태. A 신호 값을 기억해 둬요.
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
            if (it == MajiStep.HEADING) {
                seatSetupDone = false  // 새 맞이마다 자리 설정을 다시 물어요
                showDone = false
            }
        }
        scope.launch { flow.restoreSavedIfFresh() }  // 저장된 맞이가 있으면 되살려요 (#26)
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

    // 자리 설정 화면: 맞이 중이고, 조수석 문이 닫혀 있고, 아직 P단이 아니고, 오늘 계절 값이 없을 때 (D 요청)
    val active = step != MajiStep.IDLE && step != MajiStep.FINISHED
    // 자리 설정 화면: 조건은 SeatSetupRules.needsSeatSetup에 있어요 (D 답변 반영: 정차 중에 띄움)
    val seatSetupProfile = flow.profile.takeIf {
        it != null && needsSeatSetup(
            active = active,
            seatSetupDone = seatSetupDone,
            isPark = isPark,
            doorOpen = doorOpen,
            climateToday = repo.climateFor(it, repo.seasonFor(LocalDate.now().monthValue)),
            boarded = step == MajiStep.BOARDED || step == MajiStep.RIDING_HOME
        )
    }

    if (seatSetupProfile != null && screen == AppScreen.Home) {
        SeatSetupRoute(
            repo = repo,
            profile = seatSetupProfile,
            currentHeat = currentHeat,
            currentTemp = currentTemp,
            drivingRestricted = drivingRestricted,
            onSaved = { seatSetupDone = true; flow.onSeatSetupFinished(saved = true) },
            onSkip = { seatSetupDone = true; flow.onSeatSetupFinished(saved = false) },  // "다음에"면 준비 완료 안내음을 안 틀어요
            control = control,  // 시트 ◀▶(앞뒤) 연결에 필요 (빠져 있어서 시트가 안 움직였어요)
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
            showDone = showDone,
            currentHeat = currentHeat,
            currentTemp = currentTemp,
            onFinished = { showDone = true },
            onDone = { showDone = false },
            onAdd = { screen = AppScreen.ProfileAdd },
            onEditProfile = { screen = AppScreen.ProfileEdit(it.id) },
            themeSwitch = themeSwitch
        )
        AppScreen.ProfileAdd -> ProfileEditRoute(
            repo = repo,
            profileId = null,
            drivingRestricted = drivingRestricted,
            onDone = { screen = AppScreen.Home }
        )
        is AppScreen.ProfileEdit -> ProfileEditRoute(
            repo = repo,
            profileId = current.id,
            drivingRestricted = drivingRestricted,
            onDone = { screen = AppScreen.Home }
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
    showDone: Boolean,
    currentHeat: Int?,
    currentTemp: Float?,
    onFinished: () -> Unit,
    onDone: () -> Unit,
    onAdd: () -> Unit,
    onEditProfile: (Profile) -> Unit,
    themeSwitch: @Composable (Modifier) -> Unit
) {
    val active = step != MajiStep.IDLE && step != MajiStep.FINISHED

    // 맞이 중이면 진행 화면이 화면 전체를 써요. "다 내렸어요"로 끝났으면 완료 화면을 한 번 보여 줘요.
    // ("맞이 그만하기"로 끝나면 완료 화면 없이 바로 홈으로 돌아와요)
    if (active || (showDone && step == MajiStep.FINISHED)) {
        ProgressRoute(
            flow = flow,
            profiles = repo,
            drivingRestricted = drivingRestricted,
            controlAvailable = control.isControlAvailable(),
            onFinished = onFinished,
            onDone = onDone,
            control = control,
            currentHeat = currentHeat,
            currentTemp = currentTemp
        )
    } else {
        Box(Modifier.fillMaxSize()) {
            StartRoute(
                profiles = repo,
                flow = flow,
                drivingRestricted = drivingRestricted,
                onEdit = onEditProfile,
                onAdd = onAdd
            )
            // 화면 밝기 고르기: 홈 오른쪽 위에 작게
            themeSwitch(Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 56.dp))
        }
    }
}
