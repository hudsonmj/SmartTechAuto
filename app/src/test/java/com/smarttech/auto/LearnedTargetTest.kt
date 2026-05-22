package com.smarttech.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedTargetTest {

    @Test
    fun idBasedTarget() {
        val t = LearnedTarget("com.example:id/button", null)
        assertTrue(t.isIdBased())
        assertFalse(t.isCoordsBased())
        assertFalse(t.isClassBased())
        assertFalse(t.isNextToBased())
        assertEquals("com.example:id/button", t.displayText())
    }

    @Test
    fun coordsBasedTarget() {
        val t = LearnedTarget(null, "COORDS:500,800")
        assertFalse(t.isIdBased())
        assertTrue(t.isCoordsBased())
        assertFalse(t.isClassBased())
        assertFalse(t.isNextToBased())
        assertEquals("\uC88C\uD45C:500,800", t.displayText())
    }

    @Test
    fun classBasedTarget() {
        val t = LearnedTarget(null, "CLASS:android.widget.Button")
        assertFalse(t.isIdBased())
        assertFalse(t.isCoordsBased())
        assertTrue(t.isClassBased())
        assertFalse(t.isNextToBased())
        assertEquals("\uD0C0\uC785:android.widget.Button", t.displayText())
    }

    @Test
    fun nextToBasedTarget() {
        val t = LearnedTarget(null, "NEXTTO:\uD655\uC778")
        assertFalse(t.isIdBased())
        assertFalse(t.isCoordsBased())
        assertFalse(t.isClassBased())
        assertTrue(t.isNextToBased())
        assertEquals("\uC66C\uD14D\uC2A4\uD2B8:\uD655\uC778", t.displayText())
    }

    @Test
    fun textBasedTarget() {
        val t = LearnedTarget(null, "\uD655\uC778")
        assertFalse(t.isIdBased())
        assertFalse(t.isCoordsBased())
        assertFalse(t.isClassBased())
        assertFalse(t.isNextToBased())
        assertEquals("\uD655\uC778", t.displayText())
    }

    @Test
    fun nullFields() {
        val t = LearnedTarget(null, null)
        assertFalse(t.isIdBased())
        assertFalse(t.isCoordsBased())
        assertFalse(t.isClassBased())
        assertFalse(t.isNextToBased())
        assertEquals("?", t.displayText())
    }
}
