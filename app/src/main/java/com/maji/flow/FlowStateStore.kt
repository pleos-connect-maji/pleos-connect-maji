package com.maji.flow

// 맞이 상태를 저장하는 곳 (#26). 지금은 Room(maji-flow.db)으로 구현해요.
interface FlowStateStore {
    suspend fun save(snapshot: FlowSnapshot)
    suspend fun load(): FlowSnapshot?
    suspend fun clear()
}
