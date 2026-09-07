package com.runt9.kgdf.game

import com.badlogic.gdx.Gdx
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * One-shot work that has to observe the frame it was scheduled against.
 *
 * [KgdfGame.render] drains this once the screen has drawn and before the buffers swap, which is the only moment
 * the back buffer holds the current frame. Work handed to `Application.postRunnable` runs after the swap instead,
 * so anything it reads out of the back buffer is a frame behind.
 */
object PostRender {
    private val pending = ConcurrentLinkedQueue<() -> Unit>()

    /**
     * Runs [block] at the end of the next frame that is drawn, on the rendering thread. Anything [block] throws
     * escapes into the game loop, the same as a posted runnable.
     */
    fun afterRender(block: () -> Unit) {
        pending += block
        // No render means no drain, and a game that renders only on demand would never reach one. The null-safe
        // call is load-bearing rather than defensive: Gdx.graphics is null with no running application, so
        // dropping it breaks every test that drains by hand.
        Gdx.graphics?.requestRendering()
    }

    internal fun drain() {
        // Bounded by what is already queued: work scheduled from inside a block belongs to the next frame, so
        // draining until empty would run it a frame early and never terminate for a block that reschedules itself.
        repeat(pending.size) {
            val block = pending.poll() ?: return
            block()
        }
    }
}
