package com.maji.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// 화면 밝기 선택 테스트: 자동은 차를 따르고, 낮·밤은 차와 상관없이 고른 값을 써요
class ThemeModeTest {

    @Test
    fun `자동이면 차의 낮밤을 따른다`() {
        assertTrue(ThemeMode.AUTO.isDark(systemDark = true))
        assertFalse(ThemeMode.AUTO.isDark(systemDark = false))
    }

    @Test
    fun `낮을 고르면 차가 밤이어도 라이트`() {
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
    }

    @Test
    fun `밤을 고르면 차가 낮이어도 다크`() {
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
    }
}
