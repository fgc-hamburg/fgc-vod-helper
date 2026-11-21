package de.fgchamburg.vodhelper.core

/** Source of the current time. Passed in so logic stays testable and deterministic. */
fun interface Clock {
    fun nowEpochSeconds(): Long
}
