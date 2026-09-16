package com.runt9.kgdf.util

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.cbor.Cbor
import kotlin.random.Random

private val RELOAD_SEED = "reload-seed".hashCode()
private const val CALLS = 25
private const val PROBES = 10
private const val POSITION_SEARCH_LIMIT = 10_000

// SingleFileSaveStateService's configuration, which is private to it.
private val saveFileCbor = Cbor { encodeDefaults = true; ignoreUnknownKeys = true }

/** A randomizer as it comes back off disk: only its counter was stored, and construction replays the stream. */
private fun reload(rng: SeededRandomizer): SeededRandomizer = saveFileCbor.decodeFromByteArray(
    SeededRandomizer.serializer(),
    saveFileCbor.encodeToByteArray(SeededRandomizer.serializer(), rng)
)

private class DrawKind(val name: String, val draw: (SeededRandomizer) -> Any?)

private fun items(count: Int) = (1..count).toList()

private val drawKinds = listOf(
    DrawKind("percentChance, one nextFloat") { it.percentChance(0.5f) },
    DrawKind("lucky percentChance, two nextFloat") { it.percentChance(0.5f, lucky = true) },
    DrawKind("randomRange, one nextFloat") { it.randomRange(1f..10f) },
    DrawKind("coinFlip, one nextBoolean") { it.coinFlip() },
    DrawKind("randomFromCollection of 6, one nextInt(bound)") { it.randomFromCollection(items(6)) },
    DrawKind("lucky randomFromCollection of 6, two nextInt(bound)") { it.randomFromCollection(items(6), lucky = true) },
    DrawKind("nextInt(2^30 + 1), where rejection sampling redraws") { it.randomizeBasic { rng -> rng.nextInt((1 shl 30) + 1) } },
    DrawKind("nextDouble, two nextBits") { it.randomizeBasic { rng -> rng.nextDouble() } },
    DrawKind("nextLong, two nextInt") { it.randomizeBasic { rng -> rng.nextLong() } },
    DrawKind("shuffle of 24") { it.randomizeBasic { rng -> items(24).shuffled(rng) } },
    DrawKind("shuffle of 2") { it.randomizeBasic { rng -> items(2).shuffled(rng) } },
    DrawKind("shuffle of 1") { it.randomizeBasic { rng -> items(1).shuffled(rng) } }
)

private fun SeededRandomizer.probe() = List(PROBES) { randomizeBasic { it.nextInt() } }

/** How many single steps into [RELOAD_SEED]'s stream a randomizer sits, given its next two raw values. */
private fun streamPosition(next: List<Int>): String {
    val stream = Random(RELOAD_SEED)
    var previous = stream.nextInt()
    repeat(POSITION_SEARCH_LIMIT) { steps ->
        val current = stream.nextInt()
        if (previous == next[0] && current == next[1]) return steps.toString()
        previous = current
    }
    return "more than $POSITION_SEARCH_LIMIT"
}

class SeededRandomizerReloadTest : FunSpec({
    context("a reloaded randomizer resumes exactly where the live one is") {
        withData(nameFn = { it.name }, drawKinds) { kind ->
            val live = SeededRandomizer(RELOAD_SEED, 0)
            repeat(CALLS) { kind.draw(live) }
            val counter = live.rngCounter
            val reloaded = reload(live)

            val liveNext = live.probe()
            withClue("counter replays $counter steps, live stream is ${streamPosition(liveNext)} steps in") {
                reloaded.probe() shouldBe liveNext
            }
        }
    }

    // The control for every row above: the same comparison, with the saved counter one step off.
    context("a saved counter one step off is caught") {
        withData(nameFn = { it.name }, drawKinds) { kind ->
            val live = SeededRandomizer(RELOAD_SEED, 0)
            repeat(CALLS) { kind.draw(live) }
            live.rngCounter++
            val reloaded = reload(live)
            live.rngCounter--

            reloaded.probe() shouldNotBe live.probe()
        }
    }

    // Counting must not change what is drawn, or every seeded outcome anyone has recorded deals differently.
    test("a randomizer draws the same values as a bare generator on the same seed") {
        val draw = { rng: Random ->
            listOf(
                rng.nextInt(), rng.nextInt(6), rng.nextInt((1 shl 30) + 1), rng.nextFloat(), rng.nextBoolean(),
                rng.nextDouble(), rng.nextLong(), items(24).shuffled(rng)
            )
        }
        val randomizer = SeededRandomizer(RELOAD_SEED, 0)
        val bare = Random(RELOAD_SEED)

        List(CALLS) { randomizer.randomizeBasic(draw) } shouldBe List(CALLS) { draw(bare) }
    }
})
