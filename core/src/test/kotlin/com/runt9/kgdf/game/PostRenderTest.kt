package com.runt9.kgdf.game

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * [PostRender] is process-global with no teardown, so each test empties the queue first rather than trusting the
 * previous one to have left it empty.
 *
 * Draining *runs* whatever is left rather than discarding it, so a block a failing test abandoned executes here.
 * Keep every block in this file touching only its own locals, or one test's leftovers become another's failure.
 */
class PostRenderTest : FunSpec({
    beforeTest { PostRender.drain() }

    test("a scheduled block does not run until the frame is drained") {
        var ran = false
        PostRender.afterRender { ran = true }
        ran shouldBe false

        PostRender.drain()

        ran shouldBe true
    }

    test("a block scheduled from inside a block waits for the next drain") {
        var innerRuns = 0
        PostRender.afterRender { PostRender.afterRender { innerRuns++ } }

        PostRender.drain()
        innerRuns shouldBe 0

        PostRender.drain()
        innerRuns shouldBe 1
    }

    test("blocks run in the order they were scheduled") {
        val order = mutableListOf<Int>()
        PostRender.afterRender { order += 1 }
        PostRender.afterRender { order += 2 }
        PostRender.afterRender { order += 3 }

        PostRender.drain()

        order shouldBe listOf(1, 2, 3)
    }
})
