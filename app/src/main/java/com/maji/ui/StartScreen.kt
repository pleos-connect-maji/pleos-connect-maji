package com.maji.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maji.contract.MajiFlow
import com.maji.contract.Profile
import com.maji.contract.ProfileRepository

// 맞이 시작 화면 (#23). 프로필을 큰 버튼으로 보여주고, 누르면 맞이를 시작해요.
// 주행 중에는 버튼을 누를 수 없어요 (A의 onDrivingRestrictionChanged 값을 받아 넘겨 줘요).

/** 프로필을 DB에서 읽어 와서 시작 화면에 넘겨요. flow는 누른 프로필로 맞이를 시작해요. */
@Composable
fun StartRoute(
    profiles: ProfileRepository,
    flow: MajiFlow,
    drivingRestricted: Boolean,
    onEdit: (Profile) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val list by produceState(initialValue = emptyList<Profile>(), profiles) {
        value = profiles.getAll()
    }
    StartScreen(
        profiles = list,
        drivingRestricted = drivingRestricted,
        onStart = { flow.start(it) },
        onEdit = onEdit,
        modifier = modifier
    )
}

/** 화면만 그려요. 값과 이벤트를 밖에서 받아서 미리보기와 테스트에도 쓸 수 있어요. */
@Composable
fun StartScreen(
    profiles: List<Profile>,
    drivingRestricted: Boolean,
    onStart: (Profile) -> Unit,
    onEdit: (Profile) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "누구를 마중 가세요?",
            style = MaterialTheme.typography.headlineMedium
        )

        if (drivingRestricted) {
            Text(
                text = "주행 중에는 시작할 수 없어요. 정차 후에 눌러 주세요.",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        if (profiles.isEmpty()) {
            Text(
                text = "등록된 프로필이 없어요.",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        profiles.forEach { profile ->
            Button(
                onClick = { onStart(profile) },
                enabled = !drivingRestricted,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            // 수정은 정차 중에만 (주행 중이면 막아요). 편집 화면은 원영의 탑승자 관리 화면이에요.
            OutlinedButton(
                onClick = { onEdit(profile) },
                enabled = !drivingRestricted,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text(
                    text = "${profile.name} 정보 수정",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
