package com.maji.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// 스쿼클 모서리 계산 테스트. 기대값은 시안에 쓴 Figma 방식 계산(파이썬)과 같아요.
class SquircleGeometryTest {

    @Test
    fun `smoothing 0이면 일반 둥근 모서리와 같다`() {
        val k = SquircleGeometry.corner(radius = 40f, smoothing = 0f, width = 400f, height = 400f)
        assertEquals(40f, k.p, 0.001f)      // 곡선이 반지름 거리에서 시작
        assertEquals(0f, k.a, 0.001f)
        assertEquals(0f, k.b, 0.001f)
        assertEquals(0f, k.c, 0.001f)
        assertEquals(90f, k.arcDegrees, 0.001f) // 원호 하나로 90도
    }

    @Test
    fun `smoothing 0점6이면 곡선이 더 일찍 시작한다`() {
        // 시안 계산: 반지름 72, smoothing 0.6 → p 115.2, a 40.32, b 20.16, c 15.41, d 7.85
        val k = SquircleGeometry.corner(radius = 72f, smoothing = 0.6f, width = 240f, height = 240f)
        assertEquals(115.2f, k.p, 0.01f)
        assertEquals(40.32f, k.a, 0.01f)
        assertEquals(20.16f, k.b, 0.01f)
        assertEquals(15.41f, k.c, 0.02f)
        assertEquals(7.85f, k.d, 0.02f)
        assertEquals(36f, k.arcDegrees, 0.001f)
    }

    @Test
    fun `칸이 작으면 곡선이 반을 넘지 않게 줄인다`() {
        val k = SquircleGeometry.corner(radius = 40f, smoothing = 0.6f, width = 100f, height = 60f)
        assertTrue("곡선 시작 거리 ${k.p}는 짧은 변의 반(30) 이하", k.p <= 30f + 0.001f)
        assertTrue(k.r <= 30f)
    }

    @Test
    fun `반지름이 0이면 모서리가 없다`() {
        val k = SquircleGeometry.corner(radius = 0f, smoothing = 0.6f, width = 100f, height = 100f)
        assertEquals(0f, k.p, 0.001f)
    }
}
