package org.fossify.filemanager

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.fossify.filemanager.activities.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivitySanityTest {

    companion object {
        private const val ACTIVITY_IDLE_TIMEOUT_MS = 5000L
    }

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun waitForActivity() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.waitForIdle(ACTIVITY_IDLE_TIMEOUT_MS)
    }

    @Test
    fun mainActivityLaunches() {
        onView(withId(R.id.main_coordinator))
            .check(matches(isDisplayed()))
    }

    @Test
    fun mainViewPagerIsDisplayed() {
        onView(withId(R.id.main_view_pager))
            .check(matches(isDisplayed()))
    }

    @Test
    fun bottomTabsAreDisplayed() {
        onView(withId(R.id.main_tabs_holder))
            .check(matches(isDisplayed()))
    }

    @Test
    fun mainHolderIsDisplayed() {
        onView(withId(R.id.main_holder))
            .check(matches(isDisplayed()))
    }

    @Test
    fun mainMenuIsDisplayed() {
        onView(withId(R.id.main_menu))
            .check(matches(isDisplayed()))
    }
}
