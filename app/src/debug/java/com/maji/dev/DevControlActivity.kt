package com.maji.dev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maji.contract.Climate
import com.maji.contract.PassengerState
import com.maji.contract.Profile
import com.maji.control.PleosVehicleControl

/**
 * B 차량 제어 확인 화면 (debug 빌드에만 들어가요).
 * 진짜 PleosVehicleControl을 버튼으로 하나씩 불러 보고, 결과는 화면과 Logcat(MajiControl, MajiDev)에 남겨요.
 *
 * 실행: adb shell am start -n com.maji/com.maji.dev.DevControlActivity
 */
class DevControlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DevControlScreen()
                }
            }
        }
    }
}

@Composable
private fun DevControlScreen() {
    val context = LocalContext.current.applicationContext
    val log = remember { DevLog() }
    // 재시작 흉내(#26) 때 새 B로 바꿔 끼우려고 상태로 들고 있어요
    var control by remember { mutableStateOf(PleosVehicleControl(context)) }
    var seatMoved by remember { mutableIntStateOf(0) } // B가 알려 준 시트 기록 (C가 저장할 값)
    DisposableEffect(control) {
        // 지금 B를 따로 잡아 둬요. onDispose에서 control을 바로 읽으면 "새 B"를 꺼 버려요
        val current = control
        current.setOnSeatMovedChanged { seatMoved = it }
        onDispose { current.release() }
    }

    var saved by remember { mutableStateOf<PassengerState?>(null) }
    var seatTotalMs by remember { mutableIntStateOf(0) } // ◀▶로 맞춘 합계 (D가 저장할 값)
    val mom = remember {
        Profile(name = "엄마", winter = Climate(seatHeat = 2, temperature = 22f), seatMoveMs = 3000, lightBrightness = 60)
    }

    Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("B 차량 제어 확인", style = MaterialTheme.typography.titleLarge)
            Text("제어 가능: ${control.isControlAvailable()}  ·  ◀▶ 합계 ${seatTotalMs}ms  ·  시트 기록 ${seatMoved}ms")

            Button(onClick = {
                control.readPassengerState { saved = it; log.add("#9 읽음 → $it") }
            }, modifier = Modifier.fillMaxWidth()) { Text("1. 지금 상태 읽기 (#9)") }

            Button(onClick = {
                log.add("#10 준비: 열선 2, 22℃, 시트 앞으로 3초")
                control.preparePassenger(mom.winter!!, mom.seatMoveMs) { log.add("#10 준비 끝") }
            }, modifier = Modifier.fillMaxWidth()) { Text("2. 엄마 자리 준비 (#10 #11)") }

            Button(onClick = { control.stopSeat(); log.add("#11 문 열림 → stopSeat") },
                modifier = Modifier.fillMaxWidth()) { Text("3. 문 열림 = 시트 즉시 정지 (#11)") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("#46 누르는 동안:")
                HoldButton("◀ 뒤", onPress = { control.startSeatMove(false) },
                    onRelease = { val ms = control.stopSeatMove(); seatTotalMs += ms; log.add("#46 뒤로 ${ms}ms") })
                HoldButton("앞 ▶", onPress = { control.startSeatMove(true) },
                    onRelease = { val ms = control.stopSeatMove(); seatTotalMs += ms; log.add("#46 앞으로 ${ms}ms") })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { control.setSeatHeat(3); log.add("#46 열선 3") }) { Text("열선 3") }
                Button(onClick = { control.setSeatHeat(-2); log.add("#46 통풍 2") }) { Text("통풍 2") }
                Button(onClick = { control.setTemperature(26f); log.add("#46 온도 26") }) { Text("26℃") }
            }

            Button(onClick = { control.applyRideHome(mom); log.add("#12 귀갓길") },
                modifier = Modifier.fillMaxWidth()) { Text("4. 귀갓길: 창문 닫기·무드등 (#12)") }

            Button(onClick = {
                val s = saved
                if (s == null) log.add("#14 먼저 1번으로 상태를 읽어 주세요")
                else control.restore(s) { log.add("#14 복구 끝 → $s") }
            }, modifier = Modifier.fillMaxWidth()) { Text("5. 원래대로 복구 (#14)") }

            Button(onClick = {
                // C가 하는 일을 흉내: 기록 저장 → 앱 꺼짐(B 버림) → 새 B 만들고 저장값 돌려주기
                val saved = control.seatMovedMs()
                control = PleosVehicleControl(context).also { it.restoreSeatMovedMs(saved) }
                seatMoved = control.seatMovedMs()
                log.add("#26 재시작 흉내: 저장 ${saved}ms → 새 B에 불러옴 ${control.seatMovedMs()}ms")
            }, modifier = Modifier.fillMaxWidth()) { Text("6. 앱 재시작 흉내: 시트 기록 저장 → 불러오기 (#26)") }

            // #97 대체 모드(S6) 테스트: 켜 두고 실제 앱으로 돌아가 맞이를 시작하면 진행 화면에 대체 모드 문구가 나와요
            var forceOff by remember { mutableStateOf(PleosVehicleControl.isForceControlOff(context)) }
            Button(onClick = {
                forceOff = !forceOff
                PleosVehicleControl.setForceControlOff(context, forceOff)
                log.add("#97 제어 불가 강제 ${if (forceOff) "ON → 명령 안 보냄, 대체 모드" else "OFF → 정상"}")
            }, modifier = Modifier.fillMaxWidth()) {
                Text("7. 제어 불가 강제 (대체 모드 테스트): ${if (forceOff) "ON" else "OFF"}")
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(log.lines) { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** 누르고 있는 동안만 동작하는 버튼 (◀ ▶) */
@Composable
private fun HoldButton(label: String, onPress: () -> Unit, onRelease: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 88.dp, height = 48.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onPress()
                    tryAwaitRelease() // 손을 떼거나 취소될 때까지 기다려요
                    onRelease()
                })
            }
    ) { Text(label, color = MaterialTheme.colorScheme.onPrimary) }
}
