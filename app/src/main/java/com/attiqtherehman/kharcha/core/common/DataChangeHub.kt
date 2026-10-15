package com.attiqtherehman.kharcha.core.common

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fan-out point for data-change events. Repositories emit; the widget updater and the reminder
 * scheduler collect. Keeps the data layer free of Android widget/alarm dependencies.
 */
@Singleton
class DataChangeHub @Inject constructor() : DataChangeNotifier {
    private val _financial = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _reminders = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Balances, transactions, categories or accounts changed. */
    val financialEvents: SharedFlow<Unit> = _financial

    /** Reminder definitions, payments or reminder settings changed; alarms must be recomputed. */
    val reminderEvents: SharedFlow<Unit> = _reminders

    override fun financialDataChanged() {
        _financial.tryEmit(Unit)
    }

    override fun remindersChanged() {
        _reminders.tryEmit(Unit)
    }
}
