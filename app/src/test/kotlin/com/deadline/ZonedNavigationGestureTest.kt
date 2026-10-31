package com.deadline

import com.deadline.ui.navigation.NavigationZoningDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZonedNavigationGestureTest {

    @Test
    fun testScreenSpaceGestureZoning() {
        val screenHeight = 1000f

        // Bottom 28% starts at 720f
        assertTrue(NavigationZoningDefaults.isTouchInGlobalNavZone(720f, screenHeight))
        assertTrue(NavigationZoningDefaults.isTouchInGlobalNavZone(850f, screenHeight))
        assertTrue(NavigationZoningDefaults.isTouchInGlobalNavZone(1000f, screenHeight))
        assertFalse(NavigationZoningDefaults.isTouchInGlobalNavZone(719f, screenHeight))
        assertFalse(NavigationZoningDefaults.isTouchInGlobalNavZone(500f, screenHeight))
        assertFalse(NavigationZoningDefaults.isTouchInGlobalNavZone(100f, screenHeight))

        // Localized Content Zone is 180f to 720f
        assertTrue(NavigationZoningDefaults.isTouchInLocalizedContentZone(500f, screenHeight))
        assertTrue(NavigationZoningDefaults.isTouchInLocalizedContentZone(200f, screenHeight))
        assertTrue(NavigationZoningDefaults.isTouchInLocalizedContentZone(719f, screenHeight))
        assertFalse(NavigationZoningDefaults.isTouchInLocalizedContentZone(750f, screenHeight))
        assertFalse(NavigationZoningDefaults.isTouchInLocalizedContentZone(150f, screenHeight))
    }

    @Test
    fun testGestureDisambiguationThresholds() {
        // Pure horizontal swipe
        assertTrue(NavigationZoningDefaults.isHorizontalSwipeDominant(50f, 0f))
        assertTrue(NavigationZoningDefaults.isHorizontalSwipeDominant(-50f, 5f))

        // Diagonal swipe with dx = 32, dy = 20 -> 32 / 20 = 1.6 > 1.5 -> dominant
        assertTrue(NavigationZoningDefaults.isHorizontalSwipeDominant(32f, 20f))

        // Diagonal swipe with dx = 28, dy = 20 -> 28 / 20 = 1.4 <= 1.5 -> not dominant (vertical list scroll allowed)
        assertFalse(NavigationZoningDefaults.isHorizontalSwipeDominant(28f, 20f))

        // Pure vertical scroll
        assertFalse(NavigationZoningDefaults.isHorizontalSwipeDominant(5f, 50f))
        assertFalse(NavigationZoningDefaults.isHorizontalSwipeDominant(0f, 50f))
    }

    @Test
    fun testTargetPageCalculationFastFling() {
        val pageCount = 5

        // Fling left with high negative velocity from page 1 -> moves to page 2
        val targetNext = NavigationZoningDefaults.calculateTargetPage(
            currentPage = 1,
            offsetFraction = 0.05f,
            velocityX = -1200f,
            pageCount = pageCount
        )
        assertEquals(2, targetNext)

        // Fling right with high positive velocity from page 2 -> moves to page 1
        val targetPrev = NavigationZoningDefaults.calculateTargetPage(
            currentPage = 2,
            offsetFraction = -0.05f,
            velocityX = 1200f,
            pageCount = pageCount
        )
        assertEquals(1, targetPrev)

        // Boundary clamp at max page (page 4) fling left -> remains page 4
        val targetMaxClamp = NavigationZoningDefaults.calculateTargetPage(
            currentPage = 4,
            offsetFraction = 0.1f,
            velocityX = -1500f,
            pageCount = pageCount
        )
        assertEquals(4, targetMaxClamp)

        // Boundary clamp at min page (page 0) fling right -> remains page 0
        val targetMinClamp = NavigationZoningDefaults.calculateTargetPage(
            currentPage = 0,
            offsetFraction = -0.1f,
            velocityX = 1500f,
            pageCount = pageCount
        )
        assertEquals(0, targetMinClamp)
    }

    @Test
    fun testTargetPageCalculationLowVelocitySettle() {
        val pageCount = 5

        // Dragged past 25% threshold forward -> page 2
        val snapForward = NavigationZoningDefaults.calculateTargetPage(
            currentPage = 1,
            offsetFraction = 0.30f,
            velocityX = 0f,
            pageCount = pageCount
        )
        assertEquals(2, snapForward)

        // Dragged less than 25% threshold forward -> snaps back to page 1
        val snapBackForward = NavigationZoningDefaults.calculateTargetPage(
            currentPage = 1,
            offsetFraction = 0.20f,
            velocityX = 0f,
            pageCount = pageCount
        )
        assertEquals(1, snapBackForward)

        // Dragged past 25% threshold backward -> page 0
        val snapBackward = NavigationZoningDefaults.calculateTargetPage(
            currentPage = 1,
            offsetFraction = -0.30f,
            velocityX = 0f,
            pageCount = pageCount
        )
        assertEquals(0, snapBackward)

        // Dragged less than 25% threshold backward -> snaps back to page 1
        val snapBackBackward = NavigationZoningDefaults.calculateTargetPage(
            currentPage = 1,
            offsetFraction = -0.15f,
            velocityX = 0f,
            pageCount = pageCount
        )
        assertEquals(1, snapBackBackward)
    }
}
