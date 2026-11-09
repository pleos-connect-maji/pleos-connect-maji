package com.maji.flow

import com.maji.contract.Climate
import com.maji.contract.MajiStep
import com.maji.contract.PassengerState
import com.maji.contract.Season

// 맞이 상태를 한 번에 담은 것 (#26). 앱을 다시 켰을 때 되살리는 데 써요.
// 탑승 중 바뀐 열선·온도(학습 후보)와 시트 이동 기록은 여기에 없어요 (팀 결정: 학습 값 제외, 시트 기록은 근희 저장).
data class FlowSnapshot(
    val step: MajiStep,
    val profileId: Long,
    val season: Season,              // 맞이를 시작할 때 정한 계절 (원영 답변)
    val savedState: PassengerState?, // 바꾸기 전 조수석 상태 (하차·취소 때 되돌림)
    val preparedClimate: Climate?,   // 준비 때 보낸 값 (학습 기준)
    val savedAtMillis: Long
)
