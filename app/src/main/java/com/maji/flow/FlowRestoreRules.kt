package com.maji.flow

// 저장된 맞이를 되살릴지 정하는 규칙 (#26). 복원 기간은 임시로 하루예요 (팀 결정 전 기본값).
object FlowRestoreRules {
    const val MAX_AGE_MILLIS: Long = 24L * 60 * 60 * 1000

    fun isRestorable(snapshot: FlowSnapshot, nowMillis: Long): Boolean =
        nowMillis - snapshot.savedAtMillis <= MAX_AGE_MILLIS
}
