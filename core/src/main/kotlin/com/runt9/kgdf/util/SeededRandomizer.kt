package com.runt9.kgdf.util

import com.runt9.kgdf.ext.random
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.random.Random

@Serializable
class SeededRandomizer(val seed: Int, var rngCounter: Int) {

    // Only the counter is saved, and construction replays it as that many generator steps. It must count steps rather
    // than calls, or a reload resumes at a different stream position: one shuffle of n alone takes n - 1 steps.
    @Transient
    private val rng: Random = StepCountingRandom(Random(seed).apply { repeat(rngCounter) { nextInt() } })

    fun <T> randomizeBasic(action: (Random) -> T): T = action(rng)

    fun <T : Comparable<T>> randomize(lucky: Boolean = false, action: (Random) -> T): T {
        val first = randomizeBasic(action)
        if (lucky) {
            val second = randomizeBasic(action)
            return maxOf(first, second)
        }

        return first
    }

    fun percentChance(percentChance: Float, lucky: Boolean = false) = randomize(lucky) { it.nextFloat() } <= percentChance
    fun coinFlip(lucky: Boolean = false) = randomize(lucky) { it.nextBoolean() }

    fun randomRange(range: ClosedFloatingPointRange<Float>, lucky: Boolean = false) = randomize(lucky) {
        range.random(it)
    }

    fun <T> randomFromCollection(list: Collection<T>) = randomizeBasic { list.random(it) }
    fun <T : Comparable<T>> randomFromCollection(list: Collection<T>, lucky: Boolean = false) = randomize(lucky) { list.random(it) }

    // Overrides nextBits and nothing else. Every other Random method reaches the generator through it, and a
    // seeded generator's nextBits is exactly one step, so overriding another method would draw uncounted.
    private inner class StepCountingRandom(private val generator: Random) : Random() {
        override fun nextBits(bitCount: Int): Int {
            rngCounter++
            return generator.nextBits(bitCount)
        }
    }
}
