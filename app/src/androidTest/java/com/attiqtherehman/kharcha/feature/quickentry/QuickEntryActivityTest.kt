package com.attiqtherehman.kharcha.feature.quickentry

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attiqtherehman.kharcha.domain.model.TransactionFilter
import com.attiqtherehman.kharcha.domain.repository.TransactionRepository
import com.attiqtherehman.kharcha.domain.usecase.SeedDefaultsUseCase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
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
    val compose = createAndroidComposeRule<QuickEntryActivity>()

    @Inject lateinit var transactions: TransactionRepository
    @Inject lateinit var seed: SeedDefaultsUseCase

    @Before
    fun setUp() {
        hilt.inject()
        runBlocking { seed() }
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
}
