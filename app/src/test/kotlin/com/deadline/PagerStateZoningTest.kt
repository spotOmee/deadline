package com.deadline

import androidx.compose.foundation.pager.PagerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PagerStateZoningTest {

    private class TestPagerState(
        initialPage: Int = 0,
        private val pageCountProvider: () -> Int
    ) : PagerState(initialPage, 0f) {
        override val pageCount: Int
            get() = pageCountProvider()
    }

    @Test
    fun testPagerStateCreation() {
        val state = TestPagerState(initialPage = 2) { 5 }
        assertEquals(2, state.currentPage)
        assertEquals(5, state.pageCount)
    }

    @Test
    fun testDispatchRawDeltaSign() {
        val state = TestPagerState(initialPage = 1) { 5 }
        val consumedPositive = state.dispatchRawDelta(100f)
        assertTrue(consumedPositive >= 0f)
        assertEquals(1, state.currentPage)

        val consumedNegative = state.dispatchRawDelta(-100f)
        assertTrue(consumedNegative <= 0f || consumedNegative == 0f)
        assertEquals(1, state.currentPage)
    }
}
