package de.fgchamburg.vodhelper.core

import java.time.Instant

object SystemClock : Clock {
    override fun nowEpochSeconds(): Long = Instant.now().epochSecond
}
