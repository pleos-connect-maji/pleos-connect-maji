package com.maji.flow

// 맞이 상태를 저장하는 곳 (#26). 실제 저장 방식(DataStore 등)은 팀 결정 후 구현해요.
interface FlowStateStore {
    fun save(snapshot: FlowSnapshot)
    fun load(): FlowSnapshot?
    fun clear()
}
