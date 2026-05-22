package com.smarttech.auto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    @Throws(Exception::class)
    fun packageNameIsCorrect() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(context)
        assertEquals("com.smarttech.auto", context.packageName)
    }
}
