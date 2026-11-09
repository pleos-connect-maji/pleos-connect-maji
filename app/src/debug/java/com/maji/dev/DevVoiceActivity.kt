package com.maji.dev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maji.contract.Profile
import com.maji.voice.AndroidAudioFocus
import com.maji.voice.GreetingRules
import com.maji.voice.MainScheduler
import com.maji.voice.MediaAudioPlayer
import com.maji.voice.Notices
import com.maji.voice.PleosGreeter
import com.maji.voice.Speaker

/**
 * E 음성 확인 화면 (debug 빌드에만 들어가요).
 * 진짜 PleosGreeter를 버튼으로 불러 보고, 결과는 화면과 Logcat(MajiVoice, MajiDev)에 남겨요.
 *
 * 실행: adb shell am start -n com.maji/com.maji.dev.DevVoiceActivity
 */
class DevVoiceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DevVoiceScreen()
                }
            }
        }
    }
}

@Composable
private fun DevVoiceScreen() {
    val context = LocalContext.current.applicationContext
    val log = remember { DevLog() }
    val greeter = remember { PleosGreeter.create(context) }

    // TTS를 일부러 실패시켜서 대체 파일 길만 확인하는 Greeter
    val fileOnly = remember {
        PleosGreeter(
            speaker = AlwaysFailSpeaker,
            player = MediaAudioPlayer(context),
            songPlayer = MediaAudioPlayer(context),
            scheduler = MainScheduler(),
            focus = AndroidAudioFocus(context),
            defaultGreetings = emptyList(),
            log = { log.add("(파일만) $it") }
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            greeter.release()
            fileOnly.release()
        }
    }

    val mom = remember { Profile(id = 1, name = "엄마", greetingText = "엄마, 오늘도 고생 많으셨어요") }
    val special = remember { mom.copy(specialGreetingText = "엄마, 생일 축하해요!") }
    val noText = remember { Profile(id = 2, name = "아빠") }
    // 테스트 노래는 debug 빌드에만 있어요 (app/src/debug/assets/audio/test_song.mp3)
    val withSong = remember { mom.copy(songUri = "audio/test_song.mp3") }

    fun play(label: String, p: Profile, g: PleosGreeter = greeter) {
        log.add("▶ $label")
        g.playGreeting(p) { log.add("✅ onDone ($label)") }
    }

    Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("E 음성 확인 (#15 인사, #18 안내음, #16 노래, #17 포커스)", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { play("고른 인사", mom) }, modifier = Modifier.fillMaxWidth()) {
                Text("1. 고른 인사 (greetingText)")
            }
            Button(onClick = { play("오늘만의 인사", special) }, modifier = Modifier.fillMaxWidth()) {
                Text("2. 오늘만의 인사 (specialGreetingText)")
            }
            Button(onClick = { play("기본 인사", noText) }, modifier = Modifier.fillMaxWidth()) {
                Text("3. 인사 없음 → 기본 목록 첫 번째")
            }
            Button(onClick = { play("대체 파일", mom, fileOnly) }, modifier = Modifier.fillMaxWidth()) {
                Text("4. TTS 실패 흉내 → 기본 파일 (${GreetingRules.DEFAULT_GREETING_AUDIO})")
            }
            Button(onClick = {
                log.add("▶ 안내음 준비 시작")
                greeter.playNotice(Notices.PREPARE)
            }, modifier = Modifier.fillMaxWidth()) {
                Text("6. 안내음: 자리 준비 시작 (${Notices.PREPARE})")
            }
            Button(onClick = {
                log.add("▶ 안내음 준비 끝")
                greeter.playNotice(Notices.PREPARE_DONE)
            }, modifier = Modifier.fillMaxWidth()) {
                Text("7. 안내음: 자리 준비 끝 (${Notices.PREPARE_DONE})")
            }
            Button(onClick = {
                log.add("▶ 안내음 파일만")
                fileOnly.playNotice(Notices.PREPARE)
            }, modifier = Modifier.fillMaxWidth()) {
                Text("8. TTS 실패 흉내 → 안내음 파일")
            }
            Button(onClick = {
                log.add("▶ 인사 중 안내음 (안내음은 건너뛰어야 정상)")
                play("고른 인사", mom)
                greeter.playNotice(Notices.PREPARE)
            }, modifier = Modifier.fillMaxWidth()) {
                Text("9. 인사 중에 안내음 → 인사만 나와야 정상")
            }
            Button(onClick = {
                log.add("▶ 인사 → 노래 (C가 하는 순서)")
                greeter.playGreeting(withSong) {
                    log.add("✅ onDone → playSong")
                    greeter.playSong(withSong)
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("10. 인사 끝나면 노래 한 번 (test_song.mp3, 10초)")
            }
            Button(onClick = {
                log.add("▶ 노래 → 3초 뒤 안내음 (노래가 작아졌다가 돌아와야 정상)")
                greeter.playSong(withSong)
                MainScheduler().postDelayed(3_000) { greeter.playNotice(Notices.PREPARE) }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("12. 노래 중 안내음 → 노래 작아졌다 돌아옴 (#17)")
            }
            Button(onClick = {
                log.add("▶ 노래 없는 프로필")
                greeter.playSong(mom)
            }, modifier = Modifier.fillMaxWidth()) {
                Text("11. songUri 없음 → 건너뛰어야 정상")
            }
            OutlinedButton(onClick = {
                greeter.stopAll()
                fileOnly.stopAll()
                log.add("■ stopAll (onDone이 안 나와야 정상)")
            }, modifier = Modifier.fillMaxWidth()) {
                Text("5. stopAll")
            }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(log.lines) { Text(it) }
        }
    }
}

private object AlwaysFailSpeaker : Speaker {
    override fun speak(text: String, listener: Speaker.Listener) = listener.onError("일부러 실패")
    override fun stop() {}
    override fun release() {}
}
