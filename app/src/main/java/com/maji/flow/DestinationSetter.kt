package com.maji.flow

// 내비 목적지 설정 (#27). 실제 NaviHelper SDK는 버전과 설정을 확인한 뒤 연결해요.
// 흐름은 이 인터페이스만 부르고, SDK 구현은 나중에 MainActivity에서 넘겨요.
interface DestinationSetter {
    /** 프로필의 맞이 장소를 목적지로 설정해요. */
    fun requestDestination(place: String)

    /** 맞이가 끝나면 목적지를 취소해요. */
    fun cancelDestination()
}
