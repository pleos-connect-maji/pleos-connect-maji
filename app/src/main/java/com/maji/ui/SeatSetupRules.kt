package com.maji.ui

import com.maji.contract.Climate

// 자리 설정 화면을 띄울지 정하는 규칙 (#46, 원영 요청).
// 지금 조건: 맞이 중 + 아직 자리 설정을 안 끝냄 + 아직 P단이 아님 + 조수석 문이 닫힘 + 오늘 계절 값이 없음.
// 원영과 조건을 정하면 이 함수만 고치면 돼요.
fun needsSeatSetup(
    active: Boolean,
    seatSetupDone: Boolean,
    isPark: Boolean,
    doorOpen: Boolean,
    climateToday: Climate?
): Boolean = active && !seatSetupDone && !isPark && !doorOpen && climateToday == null
