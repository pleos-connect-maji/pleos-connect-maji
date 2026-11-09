package com.maji.flow

import com.maji.contract.Climate
import com.maji.contract.MajiStep
import com.maji.contract.PassengerState

// 맞이 상태를 한 번에 담은 것 (#26). 앱을 다시 켰을 때 되살리는 데 써요.
// 임시 기본값: 복원 기간 하루는 팀 결정 전 추정이에요.
data class FlowSnapshot(
    val step: MajiStep,
    val profileId: Long,
    val savedState: PassengerState?,  // 바꾸기 전 조수석 상태 (하차·취소 때 되돌림)
    val preparedClimate: Climate?,    // 준비 때 보낸 값 (학습 기준)
    val savedAtMillis: Long
)
