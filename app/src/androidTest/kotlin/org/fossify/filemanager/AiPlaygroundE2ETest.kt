package org.fossify.filemanager

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.fossify.filemanager.activities.MainActivity
import org.fossify.filemanager.helpers.PREF_LOCAL_LLM_PATH
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class AiPlaygroundE2ETest {

    companion object {
        private const val DUMMY_MODEL_PATH = "/data/local/tmp/dummy_gemma.task"
        private const val IDLE_TIMEOUT_MS = 5000L
        private const val AI_RESPONSE_TIMEOUT_MS = 30000L
    }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_LOCAL_LLM_PATH, DUMMY_MODEL_PATH)
            .commit()
    }

    @Test
    fun aiPlaygroundGeneratesResponse() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.waitForIdle(IDLE_TIMEOUT_MS)

        // Open the overflow menu and click AI Test
        val aiTestItem = device.findObject(UiSelector().text("AI Test"))
        if (!aiTestItem.exists()) {
            // Try opening overflow menu first
            val overflowButton = device.findObject(
                UiSelector().description("More options")
            )
            if (overflowButton.exists()) {
                overflowButton.click()
                device.waitForIdle(2000)
            }
        }

        val aiMenuItem = device.findObject(UiSelector().text("AI Test"))
        if (aiMenuItem.exists()) {
            aiMenuItem.click()
            device.waitForIdle(2000)

            // Type into the prompt
            onView(withId(R.id.ai_prompt_input))
                .check(matches(isDisplayed()))
                .perform(typeText("Hello"))

            // Click Generate
            onView(withId(R.id.ai_generate_button))
                .perform(click())

            // Wait for response with retry loop
            var attempts = 0
            val maxAttempts = 15
            var hasResponse = false

            while (attempts < maxAttempts && !hasResponse) {
                Thread.sleep(2000)
                try {
                    onView(withId(R.id.ai_output_text))
                        .check(matches(not(withText(R.string.ai_output_placeholder))))
                    onView(withId(R.id.ai_output_text))
                        .check(matches(not(withText(R.string.generating))))
                    hasResponse = true
                } catch (_: AssertionError) {
                    attempts++
                }
            }

            // Assert the output is not empty (it could be an error message or actual output)
            onView(withId(R.id.ai_output_text))
                .check(matches(isDisplayed()))
                .check(matches(not(withText(""))))
        }

        scenario.close()
    }
}
