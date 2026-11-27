package com.runt9.kgdf.async

import com.runt9.kgdf.log.LogLevel
import com.runt9.kgdf.testsupport.TestAsyncFactory
import com.runt9.kgdf.testsupport.capturingLogs
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds

/**
 * Driven through [TestAsyncFactory] rather than a real thread, which is the property the queue exists to give its
 * users: `EventBus`, and any consumer service built on the queue, take their context from `AsyncFactory` through
 * it, so a test scheduler drains them deterministically.
 */
class AsyncWorkQueueTest : FunSpec({
    class Handler(private val onEach: (String) -> Unit = {}) {
        val handled = CopyOnWriteArrayList<String>()

        fun handle(item: String) {
            onEach(item)
            handled += item
        }
    }

    fun queueFor(scheduler: TestCoroutineScheduler, handler: Handler) =
        AsyncWorkQueue(TestAsyncFactory(scheduler), "Test-Thread", handler::handle).also { it.start() }

    test("a submitted item reaches the handler").config(coroutineTestScope = true) {
        val handler = Handler()
        val queue = queueFor(testCoroutineScheduler, handler)

        queue.submit("first")
        testCoroutineScheduler.advanceUntilIdle()

        handler.handled shouldContainExactly listOf("first")
    }

    test("submit counts the item before the handler has run").config(coroutineTestScope = true) {
        val handler = Handler()
        val queue = queueFor(testCoroutineScheduler, handler)

        queue.submit("first")

        // Deliberately no advance: the count has to be up the moment submit returns, because that is when a
        // caller about to shut down asks whether anything is still outstanding.
        queue.pending.value shouldBe 1
        queue.isIdle shouldBe false
        handler.handled.shouldContainExactly(emptyList())
    }

    test("the count returns to zero once the handler completes").config(coroutineTestScope = true) {
        val handler = Handler()
        val queue = queueFor(testCoroutineScheduler, handler)

        queue.submit("first")
        queue.submit("second")
        testCoroutineScheduler.advanceUntilIdle()

        queue.pending.value shouldBe 0
        queue.isIdle shouldBe true
    }

    test("items are handled in submission order").config(coroutineTestScope = true) {
        val handler = Handler()
        val queue = queueFor(testCoroutineScheduler, handler)

        listOf("a", "b", "c", "d").forEach(queue::submit)
        testCoroutineScheduler.advanceUntilIdle()

        handler.handled shouldContainExactly listOf("a", "b", "c", "d")
    }

    test("a suspending handler finishes its item before the next one starts") {
        runTest {
            val order = CopyOnWriteArrayList<String>()
            val queue = AsyncWorkQueue(TestAsyncFactory(testScheduler), "Test-Thread") { item: String ->
                order += "enter:$item"
                delay(50.milliseconds)
                order += "exit:$item"
            }
            queue.start()

            queue.submit("1")
            queue.submit("2")
            testScheduler.advanceUntilIdle()

            // Interleaving would read enter:1, enter:2, exit:1, exit:2. Consumers depend on this: EventBus
            // dispatch and DeckService's flip ordering are both one-at-a-time-fully-completed by contract.
            order shouldContainExactly listOf("enter:1", "exit:1", "enter:2", "exit:2")
        }
    }

    test("a handler that throws does not stop the queue, and the failure is logged").config(coroutineTestScope = true) {
        val handler = Handler { if (it == "boom") throw IllegalStateException("handler blew up") }

        capturingLogs { sink ->
            val queue = queueFor(testCoroutineScheduler, handler)

            queue.submit("boom")
            testCoroutineScheduler.advanceUntilIdle()

            // The surviving submission is what carries this test. A cleared count alone proves nothing, because
            // the failure path zeroes the count on its way out and would look identical here.
            queue.submit("after")
            testCoroutineScheduler.advanceUntilIdle()

            handler.handled shouldContainExactly listOf("after")
            queue.pending.value shouldBe 0
            sink.messagesAt(LogLevel.ERROR).any { "handler failed; queue continues" in it } shouldBe true
        }
    }

    // A service's stop() can run on every exit from a screen, not only at shutdown: cancelling mid-request loses
    // the item and still counts it as handled.
    test("stop lets the in-flight item finish rather than cancelling it mid-handle").config(coroutineTestScope = true) {
        runTest {
            val order = CopyOnWriteArrayList<String>()
            val queue = AsyncWorkQueue<String>(TestAsyncFactory(testScheduler), "Test-Thread") { item ->
                order += "enter:$item"
                delay(50.milliseconds)
                order += "exit:$item"
            }
            queue.start()

            queue.submit("inflight")
            // Far enough in to be inside the handler, not far enough to leave it.
            testScheduler.advanceTimeBy(10.milliseconds)
            order shouldContainExactly listOf("enter:inflight")

            queue.stop()
            testScheduler.advanceUntilIdle()

            order shouldContainExactly listOf("enter:inflight", "exit:inflight")
            queue.pending.value shouldBe 0
        }
    }

    test("stop halts consumption without discarding the count, and start resumes it").config(coroutineTestScope = true) {
        val handler = Handler()
        val queue = queueFor(testCoroutineScheduler, handler)

        // Submitted BEFORE the stop and never advanced, so the item is genuinely outstanding at the moment stop
        // is called. Stopping an already-idle queue would make the count assertion below unable to fail.
        queue.submit("queued before stop")
        queue.stop()
        testCoroutineScheduler.advanceUntilIdle()

        handler.handled.shouldContainExactly(emptyList())
        // Not zeroed: stop means stop processing, not that the queued work stopped existing.
        queue.pending.value shouldBe 1

        queue.start()
        testCoroutineScheduler.advanceUntilIdle()

        handler.handled shouldContainExactly listOf("queued before stop")
        queue.pending.value shouldBe 0
    }

    test("a queue stopped while it is already consuming can still be restarted").config(coroutineTestScope = true) {
        val handler = Handler()
        val queue = queueFor(testCoroutineScheduler, handler)

        // Advanced first, so the loop is genuinely running and parked waiting for the next item when stop lands.
        // Stopping before it ever starts skips the consumer's own teardown, which is where a channel-cancelling
        // iteration would do its damage -- so that ordering could not catch this.
        queue.submit("before stop")
        testCoroutineScheduler.advanceUntilIdle()
        handler.handled shouldContainExactly listOf("before stop")

        queue.stop()
        testCoroutineScheduler.advanceUntilIdle()

        queue.start()
        queue.submit("after restart")
        testCoroutineScheduler.advanceUntilIdle()

        handler.handled shouldContainExactly listOf("before stop", "after restart")
        queue.pending.value shouldBe 0
    }

    test("dispose zeroes an outstanding count and says what was lost").config(coroutineTestScope = true) {
        val handler = Handler()

        capturingLogs { sink ->
            val queue = queueFor(testCoroutineScheduler, handler)
            queue.stop()
            testCoroutineScheduler.advanceUntilIdle()
            queue.submit("never handled")

            queue.dispose()
            testCoroutineScheduler.advanceUntilIdle()

            // Zeroed even though the item was never handled: nothing can drain a disposed queue, so a count left
            // above zero would hang every later awaitIdle instead of failing.
            queue.pending.value shouldBe 0
            sink.messagesAt(LogLevel.ERROR).any { "stopped with 1 item(s) unhandled" in it } shouldBe true
        }
    }

    test("disposing a drained queue is not reported as an error").config(coroutineTestScope = true) {
        val handler = Handler()

        capturingLogs { sink ->
            val queue = queueFor(testCoroutineScheduler, handler)
            queue.submit("first")
            testCoroutineScheduler.advanceUntilIdle()

            queue.dispose()
            testCoroutineScheduler.advanceUntilIdle()

            // A clean shutdown logging at ERROR is how people learn to ignore ERROR lines.
            sink.messagesAt(LogLevel.ERROR).shouldContainExactly(emptyList())
        }
    }

    test("submitting to a disposed queue stays quiet, and does not leave the item counted").config(coroutineTestScope = true) {
        val handler = Handler()

        capturingLogs { sink ->
            val queue = queueFor(testCoroutineScheduler, handler)
            queue.dispose()
            testCoroutineScheduler.advanceUntilIdle()

            queue.submit("too late")
            testCoroutineScheduler.advanceUntilIdle()

            handler.handled.shouldContainExactly(emptyList())
            // The count must come back down. submit() raises it before trying the channel, so a rejected item
            // that skipped the decrement would leave the queue permanently non-idle.
            queue.pending.value shouldBe 0
            sink.messagesAt(LogLevel.DEBUG).any { "dropped an item; queue is closed" in it } shouldBe true
            // Neither level, deliberately: shutdown reaches this constantly, and a queue that shouts on every
            // quit is how ERROR lines stop being read at all.
            sink.messagesAt(LogLevel.ERROR).shouldContainExactly(emptyList())
            sink.messagesAt(LogLevel.WARN).shouldContainExactly(emptyList())
        }
    }

    test("awaitIdle resumes once the queue drains") {
        runTest {
            val handler = Handler()
            val queue = AsyncWorkQueue(TestAsyncFactory(testScheduler), "Test-Thread", handler::handle)
            queue.start()
            var settled = false

            queue.submit("first")
            queue.pending.value shouldBe 1

            val waiter = launch {
                queue.awaitIdle()
                settled = true
            }

            testScheduler.advanceUntilIdle()

            settled shouldBe true
            waiter.join()
        }
    }
})
