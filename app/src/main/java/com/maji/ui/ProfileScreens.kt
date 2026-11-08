package com.maji.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maji.R
import com.maji.contract.Profile
import com.maji.contract.ProfileRepository
import com.maji.profile.FakeProfileRepository
import kotlinx.coroutines.launch

// D. 탑승자 관리 화면 (#39). 운전자가 출발 전(정차 중)에 탑승자를 만들고 고쳐요.
// 자리(열선·온도·시트)는 여기서 안 정해요 → 탑승자가 탄 뒤 자리 설정 화면(#46)에서.
//
// StartScreen과 같은 방식:
//   ...Route  = DB에서 읽고 저장하는 부분 (C가 이걸 불러요)
//   ...Screen = 화면만 그리는 부분 (미리보기에도 써요)

const val MAX_PROFILES = 3

// ───────────── 탑승자 목록 ─────────────

/** 탑승자 목록. onAdd → 새 탑승자 화면, onEdit(id) → 수정 화면으로 C가 넘겨 줘요 */
@Composable
fun ProfileListRoute(
    repo: ProfileRepository,
    drivingRestricted: Boolean,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val list by produceState(initialValue = emptyList<Profile>(), repo) {
        value = repo.getAll()
    }
    ProfileListScreen(
        profiles = list,
        drivingRestricted = drivingRestricted,
        onAdd = onAdd,
        onEdit = { onEdit(it.id) },
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
fun ProfileListScreen(
    profiles: List<Profile>,
    drivingRestricted: Boolean,
    onAdd: () -> Unit,
    onEdit: (Profile) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 세로로 긴 카드 3칸 한 줄: [엄마] [딸] [+]  → 꽉 차면 + 없음
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "탑승자 관리",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) { Text("돌아가기") }
        }

        Text(
            text = when {
                drivingRestricted -> "주행 중에는 바꿀 수 없어요. 정차 후에 해 주세요."
                profiles.isEmpty() -> "+ 를 눌러 데리러 갈 사람을 추가해 주세요."
                profiles.size >= MAX_PROFILES -> "탑승자는 ${MAX_PROFILES}명까지 등록할 수 있어요. 카드를 누르면 수정해요."
                else -> "카드를 누르면 수정해요."
            },
            style = MaterialTheme.typography.bodyLarge
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            val cardModifier = Modifier
                .weight(1f)
                .fillMaxHeight()

            profiles.take(MAX_PROFILES).forEach { profile ->
                FilledTonalButton(
                    onClick = { onEdit(profile) },
                    enabled = !drivingRestricted,
                    shape = RoundedCornerShape(24.dp),
                    modifier = cardModifier
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = profile.name, style = MaterialTheme.typography.displaySmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = seatStatus(profile), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            val slotsLeft = MAX_PROFILES - profiles.size
            if (slotsLeft > 0) {
                OutlinedButton(
                    onClick = onAdd,
                    enabled = !drivingRestricted,
                    shape = RoundedCornerShape(24.dp),
                    modifier = cardModifier
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "+", style = MaterialTheme.typography.displayLarge)
                        Text(text = "새 탑승자", style = MaterialTheme.typography.titleLarge)
                    }
                }
                // 남는 칸은 비워 둬서 카드가 1/3 크기를 유지해요
                repeat(slotsLeft - 1) { Spacer(modifier = cardModifier) }
            }
        }
    }
}

/** "겨울 ✓ · 여름 아직" */
private fun seatStatus(p: Profile): String {
    val winter = if (p.winter != null) "겨울 ✓" else "겨울 아직"
    val summer = if (p.summer != null) "여름 ✓" else "여름 아직"
    return "$winter · $summer"
}

// ───────────── 탑승자 추가·수정 ─────────────

/** profileId가 null이면 새 탑승자, 있으면 그 사람 수정. 저장·삭제·취소하면 onDone */
@Composable
fun ProfileEditRoute(
    repo: ProfileRepository,
    profileId: Long?,
    drivingRestricted: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(profileId == null) }
    var initial by remember { mutableStateOf<Profile?>(null) }

    LaunchedEffect(profileId) {
        if (profileId != null) {
            initial = repo.get(profileId)
            ready = true
        }
    }

    if (!ready) {
        Text(text = "불러오는 중…", modifier = modifier.padding(24.dp))
    } else ProfileEditScreen(
        initial = initial,
        presets = stringArrayResource(R.array.greeting_presets).toList(),
        drivingRestricted = drivingRestricted,
        onSave = { profile ->
            scope.launch {
                repo.save(profile)
                onDone()
            }
        },
        onDelete = {
            scope.launch {
                initial?.let { repo.delete(it.id) }
                onDone()
            }
        },
        onCancel = onDone,
        modifier = modifier
    )
}

@Composable
fun ProfileEditScreen(
    initial: Profile?,
    presets: List<String>,
    drivingRestricted: Boolean,
    onSave: (Profile) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val customIndex = presets.size  // 마지막 선택지 = "직접 입력"

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var choice by remember {
        val saved = initial?.greetingText
        mutableIntStateOf(
            when {
                saved == null -> 0                        // 안 정했으면 기본 목록 첫 번째
                presets.contains(saved) -> presets.indexOf(saved)
                else -> customIndex                       // 목록에 없으면 직접 입력한 것
            }
        )
    }
    var custom by remember {
        mutableStateOf(initial?.greetingText?.takeIf { !presets.contains(it) } ?: "")
    }
    var special by remember { mutableStateOf(initial?.specialGreetingText ?: "") }
    var askDelete by remember { mutableStateOf(false) }

    val greeting: String? = if (choice < customIndex) presets[choice] else custom.trim().ifBlank { null }
    val canSave = name.isNotBlank() && !drivingRestricted && (choice < customIndex || custom.isNotBlank())

    // 차 화면은 가로로 넓어서: 맨 위 = 제목 + 버튼, 아래 = 왼쪽(이름·오늘만의 인사) | 오른쪽(인사 고르기)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 맨 위: 제목 + 저장·취소·삭제 (스크롤 안 해도 항상 보여요)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (initial == null) "새 탑승자" else "${initial.name} 수정",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val base = initial ?: Profile(name = "")
                    onSave(
                        base.copy(
                            name = name.trim(),
                            greetingText = greeting,
                            specialGreetingText = special.trim().ifBlank { null }
                        )
                    )
                },
                enabled = canSave,
                modifier = Modifier.heightIn(min = 56.dp)
            ) { Text("저장") }

            OutlinedButton(onClick = onCancel, modifier = Modifier.heightIn(min = 56.dp)) { Text("취소") }

            if (initial != null) {
                TextButton(
                    onClick = { askDelete = true },
                    enabled = !drivingRestricted,
                    modifier = Modifier.heightIn(min = 56.dp)
                ) { Text("삭제") }
            }
        }

        if (drivingRestricted) {
            Text(text = "주행 중에는 바꿀 수 없어요. 정차 후에 해 주세요.", style = MaterialTheme.typography.bodyLarge)
        }

        // ── 아래: 두 칸
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // 왼쪽 칸: 이름, 오늘만의 인사
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 10) name = it },
                    label = { Text("이름 (예: 엄마)") },
                    singleLine = true,
                    enabled = !drivingRestricted,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(text = "오늘만의 인사 (선택)", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = special,
                    onValueChange = { if (it.length <= 40) special = it },
                    label = { Text("예: 생일 축하해! (다음 맞이 한 번만 나와요)") },
                    singleLine = true,
                    enabled = !drivingRestricted,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 오른쪽 칸: 인사 고르기 (혹시 넘치면 이 칸만 스크롤)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = "탈 때 들려줄 인사", style = MaterialTheme.typography.titleMedium)
                presets.forEachIndexed { i, text ->
                    GreetingOption(text = text, selected = choice == i, enabled = !drivingRestricted, onClick = { choice = i })
                }
                GreetingOption(text = "직접 입력", selected = choice == customIndex, enabled = !drivingRestricted, onClick = { choice = customIndex })
                if (choice == customIndex) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { if (it.length <= 40) custom = it },
                        label = { Text("인사 문장") },
                        singleLine = true,
                        enabled = !drivingRestricted,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (askDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("${initial.name} 삭제") },
            text = { Text("자리 설정과 인사가 모두 지워져요. 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = { askDelete = false; onDelete() }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { askDelete = false }) { Text("취소") }
            }
        )
    }
}

/** 인사 선택지 한 줄 (줄 전체를 눌러도 선택돼요) */
@Composable
private fun GreetingOption(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

// ───────────── 미리보기 (Split 버튼 → 오른쪽에 그려져요) ─────────────

private val previewPresets = listOf(
    "오늘도 고생 많았어요",
    "어서 와요, 자리 맞춰 뒀어요",
    "기다렸어요! 편하게 앉아요",
    "오늘 하루 어땠어요?"
)

private val previewProfiles = FakeProfileRepository.samples()
    .mapIndexed { i, p -> p.copy(id = i + 1L) }

@Preview(name = "목록 - 3명", showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun ListPreview() {
    MaterialTheme { Surface { ProfileListScreen(previewProfiles, false, {}, {}, {}) } }
}

@Preview(name = "목록 - 1명", showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun OneListPreview() {
    MaterialTheme { Surface { ProfileListScreen(previewProfiles.take(1), false, {}, {}, {}) } }
}

@Preview(name = "목록 - 비어 있음", showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun EmptyListPreview() {
    MaterialTheme { Surface { ProfileListScreen(emptyList(), false, {}, {}, {}) } }
}

@Preview(name = "새 탑승자", showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun NewPreview() {
    MaterialTheme { Surface { ProfileEditScreen(null, previewPresets, false, {}, {}, {}) } }
}

@Preview(name = "엄마 수정", showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun EditPreview() {
    MaterialTheme { Surface { ProfileEditScreen(previewProfiles[0], previewPresets, false, {}, {}, {}) } }
}

@Preview(name = "주행 중", showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun DrivingPreview() {
    MaterialTheme { Surface { ProfileEditScreen(previewProfiles[1], previewPresets, true, {}, {}, {}) } }
}