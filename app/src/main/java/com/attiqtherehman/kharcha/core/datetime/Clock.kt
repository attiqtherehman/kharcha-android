package com.attiqtherehman.kharcha.core.datetime

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/** Injectable time source so every date-dependent rule is testable. */
interface Clock {
    fun now(): Instant
    val zone: ZoneId

    fun today(): LocalDate = now().atZone(zone).toLocalDate()
    fun nowLocal(): LocalDateTime = now().atZone(zone).toLocalDateTime()
    fun nowTime(): LocalTime = nowLocal().toLocalTime()
    fun nowMillis(): Long = now().toEpochMilli()
}

class SystemClock @Inject constructor() : Clock {
    override fun now(): Instant = Instant.now()
    override val zone: ZoneId get() = ZoneId.systemDefault()
}
