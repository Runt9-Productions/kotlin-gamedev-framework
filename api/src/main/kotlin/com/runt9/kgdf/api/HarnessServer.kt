package com.runt9.kgdf.api

import com.runt9.kgdf.api.controller.ApiControllerRegistry
import com.runt9.kgdf.api.controller.ApiException
import com.runt9.kgdf.api.result.respondNoData
import com.runt9.kgdf.game.PostRender
import com.runt9.kgdf.log.kgdfLogger
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

/** Serves the harness over loopback. */
object HarnessServer {
    private val logger = kgdfLogger()

    private val shutdownGrace = 1_000.milliseconds
    private val shutdownTimeout = 2_000.milliseconds
    private const val LOCALHOST = "127.0.0.1"
    private const val CURRENT_SCREEN_ROUTE = "/currentScreen"

    /**
     * Runs [runGame] with the server bound around it, from the end of the first frame drawn until the game loop
     * unwinds.
     *
     * Wraps [runGame] because the game loop blocks, which bounds the server's lifetime by the game's without
     * either one knowing the other.
     *
     * Binding late is a contract rather than an optimization. Every route hops onto the rendering thread, so a
     * port answering before the loop started would fail each request with a lateinit stack. A caller waiting for
     * the harness can therefore treat the port answering as the signal.
     */
    @Suppress("HttpUrlsUsage")
    fun serve(port: Int, runGame: () -> Unit) {
        val server = embeddedServer(CIO, port = port, host = LOCALHOST) {
            routing {
                ApiControllerRegistry.addRoutesToRouting(this)

                // Every response carries currentScreen, but a caller that has not acted yet has none to read, and
                // each screen's own read refuses unless that screen is up. This one answers from any screen.
                get(CURRENT_SCREEN_ROUTE) { call.respondNoData() }
            }

            // Global exception handler that allows exceptions to be thrown to respond with an error as opposed to each controller
            // doing error handling in its own way then having to return from their handler function. Can easily be expanded in the
            // future to support strucutred errors by funneling all exceptions through ApiException
            install(StatusPages) {
                exception<ApiException> { call, cause -> call.respondText(cause.message ?: "An unknown error occurred", status = cause.statusCode) }
            }

            install(ContentNegotiation) {
                json(Json {
                    // Without encodeDefaults a nullable field vanishes from the wire exactly when it is null.
                    encodeDefaults = true
                })
            }
        }

        var bound = false
        PostRender.afterRender {
            try {
                server.start(wait = false)
            } catch (e: Exception) {
                // Wrapped because the thrown exception says nothing useful: a bind failure arrives as a coroutine
                // cancellation, and the BindException naming the port is only its cause.
                throw IllegalStateException("Harness could not bind $LOCALHOST:$port", e)
            }

            bound = true
            logger.info { "Harness listening on http://$LOCALHOST:$port" }
        }

        try {
            runGame()
        } finally {
            // Guarded because stop() on a server that never started is not a no-op: it starts the engine's lazy
            // job in order to join it, binding the port only to tear it straight back down.
            if (bound) server.stop(shutdownGrace.inWholeMilliseconds, shutdownTimeout.inWholeMilliseconds)
        }
    }
}
