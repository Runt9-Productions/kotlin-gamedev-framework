package com.runt9.kgdf.ui.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.Viewport
import com.runt9.kgdf.asset.SkinLoader
import com.runt9.kgdf.ext.lazyInject
import com.runt9.kgdf.settings.PlayerSettingsConfig
import com.runt9.kgdf.ui.controller.DialogController
import com.runt9.kgdf.ui.view.View

class BasicStage(private val origWidth: Float, private val origHeight: Float, viewport: Viewport) : Stage(viewport) {
    private val skinLoader by lazyInject<SkinLoader>()
    private val playerSettingsConfig by lazyInject<PlayerSettingsConfig>()
    val activeDialogs = mutableListOf<DialogController>()
    private var currentView: View? = null

    init {
        applyUiScale()
    }

    fun render(delta: Float) {
        viewport.apply()
        act(delta)
        draw()
    }

    /** Copies first: [DialogController.hide] removes the dialog from [activeDialogs] as it goes. */
    fun hideAllDialogs() = activeDialogs.toList().forEach { it.hide() }

    fun setView(view: View) {
        this.currentView = view
        (view as Group).run {
            root = view
            view.init()
        }
    }

    fun applyUiScale(uiScale: Float = playerSettingsConfig.get().uiScale, atRuntime: Boolean = false) {
        // Divide by uiScale because we want 50% UI scale to decrease the size of UI elements, which means we need to make the Viewport have a larger world size
        val newWidth = origWidth / uiScale
        val newHeight = origHeight / uiScale
        val activeDialogs = activeDialogs.toList()
        val currentView = currentView
        if (atRuntime) {
            clear()
            activeDialogs.forEach { it.hide() }
        }
        viewport.setWorldSize(newWidth, newHeight)
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        if (atRuntime) {
            setView(currentView!!)
            activeDialogs.forEach { it.show(this, skipAnimation = true) }
        }
        skinLoader.regenerateFonts(this)
    }
}
