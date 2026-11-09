package com.maji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.maji.contract.VehicleSignals
import com.maji.profile.RoomProfileRepository
import com.maji.signal.PleosVehicleSignals
import com.maji.ui.ProfileEditRoute
import com.maji.ui.ProfileListRoute

// 앱 첫 화면. 지금은 탑승자 관리 화면(#39)만 연결해요. 시작 화면과 진행 화면은 흐름(B·E) 머지 후에 붙여요.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ProfileApp()
                }
            }
        }
    }
}

// 화면 전환 상태. 목록 → 새로 만들기 / 수정 → 목록
private sealed class ProfileScreen {
    object List : ProfileScreen()
    object Add : ProfileScreen()
    data class Edit(val id: Long) : ProfileScreen()
}

@Composable
private fun ProfileApp() {
    val context = LocalContext.current.applicationContext
    val repo = remember { RoomProfileRepository.create(context) }

    // 주행 중 여부는 A의 신호로 받아요. 앱이 보이는 동안만 구독해요.
    var drivingRestricted by remember { mutableStateOf(false) }
    val signals = remember { PleosVehicleSignals(context) }
    DisposableEffect(signals) {
        signals.start(object : VehicleSignals.Listener {
            override fun onDrivingRestrictionChanged(isRestricted: Boolean) {
                drivingRestricted = isRestricted
            }
        })
        onDispose { signals.stop() }
    }

    var screen by remember { mutableStateOf<ProfileScreen>(ProfileScreen.List) }
    val backToList = { screen = ProfileScreen.List }

    when (val current = screen) {
        ProfileScreen.List -> ProfileListRoute(
            repo = repo,
            drivingRestricted = drivingRestricted,
            onAdd = { screen = ProfileScreen.Add },
            onEdit = { screen = ProfileScreen.Edit(it) },
            onBack = {}  // 첫 화면이라 돌아갈 곳이 없어요
        )
        ProfileScreen.Add -> ProfileEditRoute(
            repo = repo,
            profileId = null,
            drivingRestricted = drivingRestricted,
            onDone = backToList
        )
        is ProfileScreen.Edit -> ProfileEditRoute(
            repo = repo,
            profileId = current.id,
            drivingRestricted = drivingRestricted,
            onDone = backToList
        )
    }
}
