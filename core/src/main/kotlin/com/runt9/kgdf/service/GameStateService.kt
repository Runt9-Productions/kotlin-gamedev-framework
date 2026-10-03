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

    // Volatile because read() hands this to whichever thread calls it while save() replaces it from another.
    @Volatile
    private lateinit var gameState: T

    private fun cachedState(): T {
        if (!this@GameStateService::gameState.isInitialized) {
            if (stateService.hasSavedFile()) {
                gameState = stateService.loadState()
            } else {
                gameState = initNewState()
                stateService.saveState(gameState)
            }
        }

        return gameState
    }

    fun load(): T = cachedState().clone() as T

    /**
     * Runs [select] against the cached state itself, not a clone, so it costs nothing and sees every [save] that has
     * returned, on any thread. [select] must only read: a mutation lands in the cache without a save, and returning
     * the state or a mutable part of it hands out the cache.
     */
    fun <R> read(select: T.() -> R): R = cachedState().select()

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
