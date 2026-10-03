package com.runt9.kgdf.service

import com.runt9.kgdf.event.EventBus
import com.runt9.kgdf.event.GameStateUpdated
import com.runt9.kgdf.game.GameConfig
import com.runt9.kgdf.game.GameState
import com.runt9.kgdf.testsupport.TestAsyncFactory
import com.runt9.kgdf.util.SeededRandomizer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

@Serializable
class CounterState(var count: Int = 0, override val rng: SeededRandomizer = SeededRandomizer(0, 0)) : GameState {
    override fun clone() = CounterState(count, rng)
}

class CounterStateUpdated(newState: CounterState) : GameStateUpdated<CounterState>(newState)

class CounterSaveStateService(gameConfig: GameConfig) : SingleFileSaveStateService<CounterState>(CounterState::class, gameConfig)

class CounterStateService(eventBus: EventBus, saveStateService: CounterSaveStateService, serviceAsync: ServiceAsync) :
    GameStateService<CounterState, CounterStateUpdated>(eventBus, saveStateService, serviceAsync) {
    override fun initNewState() = CounterState()
    override fun updatedEvent(state: CounterState) = CounterStateUpdated(state)
}

class GameStateServiceTest : FunSpec({
    lateinit var gameDataPath: Path
    lateinit var service: CounterStateService

    beforeTest {
        gameDataPath = Files.createTempDirectory("kgdfw-game-state")
        val asyncFactory = TestAsyncFactory(TestCoroutineScheduler())
        val saveStateService = CounterSaveStateService(GameConfig("GameStateServiceTest", gameDataPath = gameDataPath))
        service = CounterStateService(EventBus(asyncFactory), saveStateService, ServiceAsync(asyncFactory))
    }

    afterTest {
        gameDataPath.toFile().deleteRecursively()
    }

    test("peek before the first load throws and writes no save file") {
        shouldThrow<IllegalStateException> { service.peek { count } }

        gameDataPath.resolve(SAVES_DIR).resolve(SAVED_RUN_FILE).toFile().exists() shouldBe false
    }

    test("peek hands its selector the instance save stored, not a clone") {
        val saved = CounterState(count = 5)
        service.save(saved)

        service.peek { this } shouldBeSameInstanceAs saved
        service.peek { count } shouldBe 5
    }
})
