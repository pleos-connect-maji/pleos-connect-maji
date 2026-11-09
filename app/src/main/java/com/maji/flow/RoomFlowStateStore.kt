package com.maji.flow

// 맞이 상태를 Room(maji-flow.db)에 저장해요 (#26).
class RoomFlowStateStore(private val dao: FlowSnapshotDao) : FlowStateStore {
    override suspend fun save(snapshot: FlowSnapshot) {
        dao.upsert(snapshot.toEntity())
    }

    override suspend fun load(): FlowSnapshot? = dao.get()?.toSnapshot()

    override suspend fun clear() {
        dao.clear()
    }
}
