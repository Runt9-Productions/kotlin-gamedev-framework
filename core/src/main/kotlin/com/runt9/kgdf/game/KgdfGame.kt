package com.runt9.kgdf.game

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.runt9.kgdf.application.ApplicationInitializer
import com.runt9.kgdf.event.ChangeScreenRequest
import com.runt9.kgdf.event.EventBus
import com.runt9.kgdf.event.ExitRequest
import com.runt9.kgdf.event.HandlesEvent
import com.runt9.kgdf.ext.inject
import com.runt9.kgdf.ext.lazyInject
import com.runt9.kgdf.inject.Injector
import com.runt9.kgdf.input.InputTracker
import com.runt9.kgdf.input.InputTrackingService
import com.runt9.kgdf.log.KotlinLoggingLogger
import com.runt9.kgdf.log.kgdfLogger
import com.runt9.kgdf.ui.core.UiScreen
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.async.onRenderingThread

abstract class KgdfGame : KtxGame<KtxScreen>() {
    private val logger = kgdfLogger()
    private val initializer by lazyInject<ApplicationInitializer>()
    private val input by lazyInject<Input>()
    private val eventBus by lazyInject<EventBus>()
    private val app by lazyInject<Application>()
    private val inputTracking by lazyInject<InputTrackingService>()

    override fun create() {
        Gdx.app.applicationLogger = KotlinLoggingLogger()
        initializer.initialize()

        Injector.initGdxDeps()
        Injector.initRunningDeps()

        val multiplexer = inject<InputMultiplexer>()
        // Index 0, before any screen adds its stage: the tracker must see events no processor has consumed yet.
        multiplexer.addProcessor(0, InputTracker(inputTracking))
        input.inputProcessor = multiplexer
        eventBus.registerHandlers(this)

        addScreens()
        setInitialScreen()
    }

    /** The drain must follow the screen's draw: before it, the back buffer still holds the previous frame. */
    override fun render() {
        super.render()
        PostRender.drain()
    }

    protected abstract fun addScreens()
    protected abstract fun setInitialScreen()

    /** Release events do not arrive while paused, so anything held at that point would read as held on return. */
    override fun pause() {
        super.pause()
        inputTracking.clear()
    }

    override fun dispose() {
        eventBus.unregisterHandlers(this)
        super.dispose()
        initializer.shutdown()
    }

    @HandlesEvent
    suspend fun changeScreen(event: ChangeScreenRequest<*>) = onRenderingThread {
        logger.debug { "Changing screen to ${event.screenClass.simpleName}" }
        setScreen(event.screenClass.java)
    }

    @HandlesEvent(ExitRequest::class)
    fun handleExit() {
        app.exit()
    }

    protected inline fun <reified S : UiScreen> addScreen() = addScreen(inject<S>())
}
