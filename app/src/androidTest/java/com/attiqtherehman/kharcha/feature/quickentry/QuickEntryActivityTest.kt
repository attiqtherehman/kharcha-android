package com.attiqtherehman.kharcha.feature.quickentry

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.attiqtherehman.kharcha.domain.model.TransactionFilter
import com.attiqtherehman.kharcha.domain.repository.TransactionRepository
import com.attiqtherehman.kharcha.domain.usecase.SeedDefaultsUseCase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/** End-to-end: type an amount on the keypad, save, and find the row in the database. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class QuickEntryActivityTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var transactions: TransactionRepository
    @Inject lateinit var seed: SeedDefaultsUseCase

    private lateinit var scenario: ActivityScenario<QuickEntryActivity>

    @Before
    fun setUp() {
        hilt.inject()
        // The quick-entry ViewModel reads the account list once when it starts, and the Hilt test
        // application does not run the app's start-up tasks, so the defaults must exist before the
        // activity is launched: on a fresh install there would otherwise be no account to save to.
        runBlocking { seed() }
        wakeDisplay()
        scenario = ActivityScenario.launch(QuickEntryActivity::class.java)
        showOverLockScreen()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun typingAnAmountAndSaving_createsAnExpense() {
        val before = runBlocking { transactions.list(TransactionFilter()).size }
        compose.onNodeWithText("Save").assertIsDisplayed()
        listOf("2", "5", "0").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("Save & add another").performClick()
        compose.waitUntil(5_000) { runBlocking { transactions.list(TransactionFilter()).size } == before + 1 }
        val latest = runBlocking { transactions.list(TransactionFilter()).first() }
        assertEquals(250_00L, latest.transaction.amount.minor)
    }

    /**
     * A phone that has dozed off resumes a freshly launched activity behind the keyguard without
     * attaching its window, so the Compose hierarchy never appears ("No compose hierarchies found").
     * Waking the display and showing the activity over the lock screen makes the test independent
     * of the screen state; it does not unlock the device.
     */
    private fun wakeDisplay() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
        }
    }
}
