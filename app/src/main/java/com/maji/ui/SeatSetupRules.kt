package com.maji.ui

import com.maji.contract.Climate

// 자리 설정 화면을 띄울지 정하는 규칙 (#46).
// 지금 조건(D 답변 반영): 맞이 중 + 아직 자리 설정을 안 끝냄 + 정차 중(P단) + 조수석 문이 닫힘 + 오늘 계절 값이 없음.
// 이미 탔으면(탑승·함께 가는 중) 띄우지 않아요: 그때는 함께 가는 중 화면의 자리 조절에서 바꾸고, 내릴 때 기억해요 (#111).
//   (예전엔 함께 가는 길에 차를 세우면 "처음 맞이하네요"가 "다 내렸어요" 화면을 덮었어요)
// 잠금은 주행 상태(drivingRestricted)로 따로 해요. 정차 중에는 잠기지 않아요.
fun needsSeatSetup(
    active: Boolean,
    seatSetupDone: Boolean,
    isPark: Boolean,
    doorOpen: Boolean,
    climateToday: Climate?,
    boarded: Boolean = false
): Boolean = active && !boarded && !seatSetupDone && isPark && !doorOpen && climateToday == null
