package com.maji.ui

import com.maji.contract.Climate

// 자리 설정 화면을 띄울지 정하는 규칙 (#46).
// 지금 조건(원영 답변 반영): 맞이 중 + 아직 자리 설정을 안 끝냄 + 정차 중(P단) + 조수석 문이 닫힘 + 오늘 계절 값이 없음.
// 잠금은 주행 상태(drivingRestricted)로 따로 해요. 정차 중에는 잠기지 않아요.
fun needsSeatSetup(
    active: Boolean,
    seatSetupDone: Boolean,
    isPark: Boolean,
    doorOpen: Boolean,
    climateToday: Climate?
): Boolean = active && !seatSetupDone && isPark && !doorOpen && climateToday == null
