package com.attiqtherehman.kharcha.core.common

import java.util.UUID

object Ids {
    /** Random UUID for user-created records. */
    fun newId(): String = UUID.randomUUID().toString()

    /** Deterministic UUID for seeded records so two installs merge without duplicates. */
    fun seeded(key: String): String = UUID.nameUUIDFromBytes("kharcha:$key".toByteArray(Charsets.UTF_8)).toString()
}

/** Thrown when a mutation would break a ledger invariant. */
class LedgerViolationException(val violations: List<Any>) : IllegalArgumentException("Ledger violation: $violations")

/** Raised by delete operations that are refused because other records still reference the row. */
class HasReferencesException(val referenceCount: Int) : IllegalStateException("$referenceCount records still reference this item")

/** Raised when a bill occurrence is marked paid twice. */
class AlreadyPaidException : IllegalStateException("This bill occurrence is already marked as paid")

/** Implemented by the platform layer; repositories call it after mutations. */
interface DataChangeNotifier {
    fun financialDataChanged()
    fun remindersChanged()
}
