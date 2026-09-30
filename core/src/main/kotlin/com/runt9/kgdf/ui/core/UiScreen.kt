package com.runt9.kgdf.ui.core

import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.utils.viewport.FitViewport
import com.runt9.kgdf.asset.SkinLoader
import com.runt9.kgdf.ext.lazyInject
import com.runt9.kgdf.ui.DialogManager
import com.runt9.kgdf.ui.controller.Controller

/**
 * Used for UI displays such as menus or part of a GameScreen to display UI elements on the screen while
 * allowing the game to control its own grid.
 */
abstract class UiScreen(width: Float, height: Float) : BaseScreen {
    val uiStage = BasicStage(width, height, FitViewport(width, height))
    val input by lazyInject<InputMultiplexer>()
    val dialogManager by lazyInject<DialogManager>()
    private val skinLoader by lazyInject<SkinLoader>()
    override val stages = listOf(uiStage)
    abstract val uiController: Controller

    override fun show() {
        input.addProcessor(uiStage)
        // Before setView, not after with atRuntime: that clears the stage and runs the view's init() a second time,
        // discarding anything the first build started, such as actions or actors added to the stage.
        uiStage.applyUiScale()
        uiController.load()
        uiStage.setView(uiController.view)
        dialogManager.currentStage = uiStage
    }

    override fun hide() {
        // clear() drops a dialog's actors but not its activeDialogs entry, which would count as open next show.
        uiStage.hideAllDialogs()
        uiStage.clear()
        input.removeProcessor(uiStage)
        uiController.dispose()
        dialogManager.currentStage = null
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        uiStage.viewport.update(width, height)
        skinLoader.regenerateFonts(uiStage)
    }
}
