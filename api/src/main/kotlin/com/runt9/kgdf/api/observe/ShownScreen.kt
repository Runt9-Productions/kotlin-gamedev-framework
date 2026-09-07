package com.runt9.kgdf.api.observe

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.scenes.scene2d.Stage
import com.runt9.kgdf.ext.lazyInject
import com.runt9.kgdf.game.KgdfGame
import com.runt9.kgdf.game.PostRender
import com.runt9.kgdf.ui.controller.Controller
import com.runt9.kgdf.ui.core.UiScreen
import com.runt9.kgdf.ui.view.DialogView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import ktx.async.onRenderingThread
import kotlin.time.Duration.Companion.seconds

/** Generous: a timeout here means the render loop stopped, which is worth surfacing rather than waiting out. */
internal val RENDER_TIMEOUT = 10.seconds

/**
 * One hop onto the rendering thread, bounded. Everything the harness reads or drives goes through here, so an
 * answer cannot describe two different frames and a stalled render loop surfaces rather than hanging.
 *
 * Endpoints should use `ApiController.onRender` or `ScreenApiController.onScreen`.
 *
 * Nesting one hop inside another would deadlock until the timeout, because this posts and suspends and the inner
 * call would wait on a frame the outer block still holds. Both wrappers take a non-suspend block, so that cannot
 * be written — keep it that way. Give either one a suspend block and the deadlock becomes reachable.
 */
suspend fun <T> renderHop(block: () -> T): T = withTimeout(RENDER_TIMEOUT) { onRenderingThread { block() } }

/**
 * [renderHop] for anything that reads what was *drawn* rather than what is in memory. A plain hop runs between
 * frames, after the buffers have swapped, so the back buffer it sees is one frame behind; this one runs at the end
 * of the next frame's render, before the swap.
 *
 * [block] is a non-suspend block for the same reason [renderHop]'s is. Anything it throws comes back to the caller
 * rather than escaping into the game loop.
 */
suspend fun <T> postRenderHop(block: () -> T): T = withTimeout(RENDER_TIMEOUT) {
    suspendCancellableCoroutine { continuation ->
        PostRender.afterRender { continuation.resumeWith(runCatching(block)) }
    }
}

/**
 * Which screen the player is on. **Screen here means screen or topmost open dialog**, which is what a player
 * means by it too.
 */
object ShownScreen {
    private val multiplexer by lazyInject<InputMultiplexer>()

    /**
     * The controller for the topmost open dialog if there is one, otherwise for the screen behind it. This is
     * both what a game keys its observers on and the instance they read.
     *
     * The stage comes from the multiplexer because that is the list dispatch itself walks, so nothing is live
     * here without being live for a real click too. **Last wins** -- later processors sit on top.
     *
     * The controller is pushed down into observers rather than injected by them because
     * [com.runt9.kgdf.ui.DialogManager] builds a dialog's controller with `dynamicInject`, which constructs
     * rather than resolving from the DI container.
     */
    fun shown(): Controller? {
        val stage = multiplexer.processors.filterIsInstance<Stage>().lastOrNull() ?: return null
        stage.root.children.filterIsInstance<DialogView>().lastOrNull { it.isVisible }?.let { return it.controller }

        val game = Gdx.app?.applicationListener as? KgdfGame ?: return null
        return (game.shownScreen as? UiScreen)?.uiController
    }
}
