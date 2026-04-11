package org.fossify.filemanager

import android.Manifest
import android.os.Build
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.fossify.filemanager.activities.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivitySanityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    @Before
    fun dismissSystemDialogs() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // Dismiss "All files access" dialog if it appears (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val allowButton = device.findObject(UiSelector().textContains("Allow"))
            if (allowButton.exists()) {
                allowButton.click()
            }
        }

        // Wait for the activity to settle
        device.waitForIdle(3000)
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
    fun searchMenuIsDisplayed() {
        onView(withId(R.id.main_menu))
            .check(matches(isDisplayed()))
    }
}
