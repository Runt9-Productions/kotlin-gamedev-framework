package com.runt9.kgdf.service

import com.runt9.kgdf.event.EventBus
import com.runt9.kgdf.event.GameStateUpdated
import com.runt9.kgdf.event.eventHandler
import com.runt9.kgdf.game.GameConfig
import com.runt9.kgdf.game.GameState
import com.runt9.kgdf.testsupport.TestAsyncFactory
import com.runt9.kgdf.util.SeededRandomizer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

// Identity equality on purpose: two instances always compare unequal, so a save of a different instance always
// writes, and only a save of the cached instance itself meets the equal-state no-op.
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
    lateinit var scheduler: TestCoroutineScheduler
    lateinit var bus: EventBus
    lateinit var service: CounterStateService
    val events = mutableListOf<CounterStateUpdated>()

    fun serviceOverGameData(): CounterStateService {
        val asyncFactory = TestAsyncFactory(scheduler)
        val saveStateService = CounterSaveStateService(GameConfig("GameStateServiceTest", gameDataPath = gameDataPath))
        return CounterStateService(bus, saveStateService, ServiceAsync(asyncFactory))
    }

    fun saveFile() = gameDataPath.resolve(SAVES_DIR).resolve(SAVED_RUN_FILE).toFile()

    beforeTest {
        gameDataPath = Files.createTempDirectory("kgdfw-game-state")
        scheduler = TestCoroutineScheduler()
        bus = EventBus(TestAsyncFactory(scheduler)).also { it.loop() }
        events.clear()
        bus.registerHandler(CounterStateUpdated::class, eventHandler<CounterStateUpdated> { events += it })
        service = serviceOverGameData()
    }

    afterTest {
        bus.dispose()
        gameDataPath.toFile().deleteRecursively()
    }

    test("peek before the first load throws and writes no save file") {
        shouldThrow<IllegalStateException> { service.peek { count } }

        saveFile().exists() shouldBe false
    }

    test("peek hands its selector the instance save stored, not a clone") {
        val saved = CounterState(count = 5)
        service.save(saved)

        service.peek { this } shouldBeSameInstanceAs saved
        service.peek { count } shouldBe 5
    }

    test("load hands out a clone, so mutating it leaves the cached state alone") {
        val loaded = service.load()
        loaded.count = 9

        service.peek { count } shouldBe 0
    }

    test("the first load with no save file writes the new state to disk") {
        service.load()

        saveFile().exists() shouldBe true
    }

    test("a fresh service loads the state an earlier one saved") {
        service.save(CounterState(count = 7))

        serviceOverGameData().load().count shouldBe 7
    }

    test("saving the cached instance again fires no update event") {
        val state = CounterState(count = 1)

        // Positive control: the first save of this instance does announce itself.
        service.save(state)
        scheduler.advanceUntilIdle()
        events shouldHaveSize 1

        service.save(state)
        scheduler.advanceUntilIdle()

        events shouldHaveSize 1
    }

    test("forceUpdate saves and announces the cached instance again") {
        val state = CounterState(count = 1)
        service.save(state)

        service.save(state, forceUpdate = true)
        scheduler.advanceUntilIdle()

        events shouldHaveSize 2
    }

    test("the update event carries a clone of the saved state") {
        val saved = CounterState(count = 4)
        service.save(saved)
        scheduler.advanceUntilIdle()

        val announced = events.single().newState
        announced shouldNotBeSameInstanceAs saved
        announced.count shouldBe 4
    }

    test("update has applied and saved the change by the time it returns") {
        service.load()

        service.update { count = 3 }

        service.peek { count } shouldBe 3
    }

    test("updateAsync applies the change only once Service-Thread runs") {
        service.load()

        service.updateAsync { count = 3 }
        service.peek { count } shouldBe 0

        scheduler.advanceUntilIdle()
        service.peek { count } shouldBe 3
    }
})
