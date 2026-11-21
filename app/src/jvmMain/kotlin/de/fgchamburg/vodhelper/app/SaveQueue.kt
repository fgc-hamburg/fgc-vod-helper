package de.fgchamburg.vodhelper.app

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Runs saves (project and settings) one at a time, in the order they were asked for, on one
 * background thread. Work is queued the moment it is submitted, so it isn't lost when a screen
 * goes away; [awaitIdle] lets the app finish queued saves before it quits.
 */
class SaveQueue {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "saves").apply { isDaemon = true } }

    fun <T> submit(work: () -> T): CompletableFuture<T> = CompletableFuture.supplyAsync(work, executor)

    /** Blocks until everything submitted so far has run, or [timeoutMillis] has passed. */
    fun awaitIdle(timeoutMillis: Long = 5_000) {
        runCatching { submit {}.get(timeoutMillis, TimeUnit.MILLISECONDS) }
    }
}
