package de.fgchamburg.vodhelper.app

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class SaveQueueTest {
    @Test
    fun `saves run one after another in the order they were given`() {
        val queue = SaveQueue()
        val done = Collections.synchronizedList(mutableListOf<Int>())
        val futures = (1..50).map { n -> queue.submit { done += n } }
        futures.forEach { it.get(5, TimeUnit.SECONDS) }
        assertEquals((1..50).toList(), done)
    }

    @Test
    fun `await idle waits for saves already queued`() {
        val queue = SaveQueue()
        val release = CountDownLatch(1)
        val done = Collections.synchronizedList(mutableListOf<String>())
        queue.submit { release.await(5, TimeUnit.SECONDS); done += "first" }
        queue.submit { done += "second" }
        Thread { Thread.sleep(100); release.countDown() }.start()

        queue.awaitIdle()

        assertEquals(listOf("first", "second"), done)
    }

    @Test
    fun `a failing save does not stop the next one`() {
        val queue = SaveQueue()
        queue.submit { error("disk full") }
        assertEquals("ok", queue.submit { "ok" }.get(5, TimeUnit.SECONDS))
    }
}
