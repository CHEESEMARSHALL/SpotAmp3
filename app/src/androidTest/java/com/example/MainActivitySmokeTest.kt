package com.example

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that the real application entry point can create on a device. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @Test
    fun mainActivityCreatesSuccessfully() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED))
            scenario.onActivity { activity ->
                assertEquals(MainActivity::class.java, activity::class.java)
            }
        }
    }
}
