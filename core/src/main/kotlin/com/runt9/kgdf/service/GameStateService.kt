@file:Suppress("UNCHECKED_CAST")

package com.runt9.kgdf.service

import com.runt9.kgdf.event.EventBus
import com.runt9.kgdf.event.GameStateUpdated
import com.runt9.kgdf.game.GameState
import com.runt9.kgdf.log.kgdfLogger

abstract class GameStateService<T : GameState, E : GameStateUpdated<T>>(
    private val eventBus: EventBus,
    private val stateService: SingleFileSaveStateService<T>,
    private val serviceAsync: ServiceAsync
) {
    private val logger = kgdfLogger()

    // Volatile: load and peek run on the caller's thread while save may replace this from Service-Thread.
    @Volatile
    private lateinit var gameState: T

    /** First call initializes the state from the save file, or creates and saves a new one when there is none. */
    fun load(): T {
        if (!this@GameStateService::gameState.isInitialized) {
            if (stateService.hasSavedFile()) {
                gameState = stateService.loadState()
            } else {
                gameState = initNewState()
                stateService.saveState(gameState)
            }
        }

        return gameState.clone() as T
    }

    /**
     * Runs [select] against the live cached state, not a clone, and returns what it selects. No side effects: unlike
     * [load] it never initializes, and throws if nothing has loaded or saved yet.
     *
     * [select] must only read. A mutation lands in the cache with no save and no update event, and returning the
     * state or a mutable part of it hands out the cache. [save] keeps the instance it is given, so a caller that
     * mutates an object after saving it also changes what this sees.
     */
    fun <R> peek(select: T.() -> R): R {
        check(this@GameStateService::gameState.isInitialized) { "peek before the first load or save" }
        return gameState.select()
    }

    fun save(gameState: T, forceUpdate: Boolean = false) {
        if (!this@GameStateService::gameState.isInitialized || forceUpdate || gameState != this@GameStateService.gameState) {
            logger.debug { "Saving game state" }
            this@GameStateService.gameState = gameState
            eventBus.enqueueEvent(updatedEvent(gameState.clone() as T))
            stateService.saveState(gameState)
        }
    }

    fun updateAsync(forceUpdate: Boolean = false, update: T.() -> Unit) = serviceAsync.launchOnServiceThread {
        update(forceUpdate, update)
    }

    fun update(forceUpdate: Boolean = false, update: T.() -> Unit) {
        load().apply {
            update()
            save(this, forceUpdate)
        }
    }

    abstract fun initNewState(): T
    abstract fun updatedEvent(state: T): E
}
