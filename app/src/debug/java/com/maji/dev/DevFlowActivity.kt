package com.maji.dev

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maji.contract.Climate
import com.maji.contract.Greeter
import com.maji.contract.MajiStep
import com.maji.contract.PassengerState
import com.maji.contract.Profile
import com.maji.contract.VehicleControl
import com.maji.flow.MajiFlowImpl
import com.maji.profile.FakeProfileRepository

// 개발용 단계 확인 화면 (#22). 차 신호 버튼을 눌러 단계를 넘기고, 각 단계에서 불린 B·E 함수를 로그로 확인해요.
// debug 빌드에서만 실행돼요: adb shell am start -n com.maji/com.maji.dev.DevFlowActivity
class DevFlowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DevFlowScreen()
                }
            }
        }
    }
}

@Composable
fun DevFlowScreen() {
    val log = remember { DevLog() }
    val scope = rememberCoroutineScope()
    val flow = remember {
        MajiFlowImpl(
            control = DevVehicleControl(log),
            profiles = FakeProfileRepository(FakeProfileRepository.samples()),
            greeter = DevGreeter(log),
            scope = scope
        )
    }
    val profile = remember { FakeProfileRepository.samples().first() }

    var step by remember { mutableStateOf(flow.step) }
    LaunchedEffect(flow) {
        flow.addListener {
            step = it
            log.add("단계 → $it")
        }
    }

    var isPark by remember { mutableStateOf(false) }
    var isBrakeOn by remember { mutableStateOf(false) }
    var isDoorOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "현재 단계: $step", style = MaterialTheme.typography.titleLarge)

        Button(onClick = { flow.start(profile) }, modifier = Modifier.fillMaxWidth()) {
            Text("맞이 시작 (${profile.name})")
        }
        Button(
            onClick = {
                isPark = !isPark
                flow.onParkChanged(isPark)
                log.add("신호 P단 ${if (isPark) "켜짐" else "꺼짐"}")
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("P단 ${if (isPark) "끄기" else "켜기"}") }
        Button(
            onClick = {
                isBrakeOn = !isBrakeOn
                flow.onParkingBrakeChanged(isBrakeOn)
                log.add("신호 브레이크 ${if (isBrakeOn) "켜짐" else "꺼짐"}")
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("주차 브레이크 ${if (isBrakeOn) "끄기" else "켜기"}") }
        Button(
            onClick = {
                isDoorOpen = !isDoorOpen
                flow.onPassengerDoorChanged(isDoorOpen)
                log.add("신호 조수석 문 ${if (isDoorOpen) "열림" else "닫힘"}")
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("조수석 문 ${if (isDoorOpen) "닫기" else "열기"}") }
        Button(onClick = { flow.finish() }, modifier = Modifier.fillMaxWidth()) {
            Text("최종 하차")
        }
        Button(onClick = { flow.cancel() }, modifier = Modifier.fillMaxWidth()) {
            Text("맞이 취소")
        }

        Text(text = "로그 (최신이 위)", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(log.lines) { line -> Text(text = line) }
        }
    }
}

// 화면과 Logcat(태그 MajiDev)에 같이 남기는 로그
class DevLog {
    val lines = mutableStateListOf<String>()

    fun add(message: String) {
        Log.d(TAG, message)
        lines.add(0, message)
    }

    companion object {
        private const val TAG = "MajiDev"
    }
}

// B 자리 대신 쓰는 가짜 제어. 어떤 함수가 불렸는지만 로그로 남겨요.
private class DevVehicleControl(private val log: DevLog) : VehicleControl {
    private val original = PassengerState(seatHeat = 0, temperature = null, windowPosition = 3)

    override fun isControlAvailable() = true

    override fun readPassengerState(onResult: (PassengerState) -> Unit) {
        log.add("B readPassengerState")
        onResult(original)
    }

    override fun preparePassenger(climate: Climate, seatMoveMs: Int, onDone: () -> Unit) {
        log.add("B preparePassenger $climate seatMoveMs=$seatMoveMs")
        onDone()
    }

    override fun stopSeat() {
        log.add("B stopSeat")
    }

    override fun applyRideHome(profile: Profile) {
        log.add("B applyRideHome ${profile.name}")
    }

    override fun restore(state: PassengerState, onDone: () -> Unit) {
        log.add("B restore $state")
        onDone()
    }
}

// E 자리 대신 쓰는 가짜 음성. 소리 대신 로그만 남겨요.
private class DevGreeter(private val log: DevLog) : Greeter {
    override fun playGreeting(profile: Profile, onDone: () -> Unit) {
        log.add("E playGreeting ${profile.name}")
        onDone()
    }

    override fun playSong(profile: Profile) {
        log.add("E playSong ${profile.name}")
    }

    override fun playNotice(audio: String) {
        log.add("E playNotice $audio")
    }

    override fun stopAll() {
        log.add("E stopAll")
    }
}
