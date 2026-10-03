package com.runt9.kgdf.ext

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

const val PERCENT_MULTI = 100

fun Float.percent() = this * PERCENT_MULTI
fun Double.percent() = this * PERCENT_MULTI
fun Double.sqrt() = sqrt(this)
fun Float.sqrt() = sqrt(this)
fun Int.sqrt() = toDouble().sqrt()

fun ClosedRange<Float>.random(rng: Random) = rng.nextFloat() * (endInclusive - start) + start

fun Float.displayInt() = roundToInt().toString()
fun Float.displayDecimal(decimals: Int = 2, trimTrailingZeros: Boolean = false) = toDouble().displayDecimal(decimals, trimTrailingZeros)
fun Float.displayMultiplier(decimals: Int = 2) = "${displayDecimal(decimals)}x"
fun Float.displayPercent(decimals: Int = 1) = "${(this * 100f).displayDecimal(decimals)}%"

fun Double.displayInt() = roundToInt().toString()
fun Double.displayDecimal(decimals: Int = 2, trimTrailingZeros: Boolean = false): String {
    val formatted = "%.${decimals}f".format(this)
    if (!trimTrailingZeros) return formatted
    // "NaN" and "Infinity" end in no digit, so trimming would erase them entirely.
    if (!isFinite()) return formatted

    val trimmed = formatted.withoutTrailingZeros(decimals)
    // A small negative formats as "-0.00", which trims to "-0".
    if (trimmed == "-0") return "0"
    return trimmed
}

private fun String.withoutTrailingZeros(decimals: Int): String {
    // With no decimal places every trailing zero is part of the whole number: 10 would trim to "1".
    if (decimals == 0) return this
    // Drops the separator whatever character the locale uses. A locale with non-ASCII digits is not trimmed at all.
    return trimEnd('0').dropLastWhile { !it.isDigit() }
}

fun Double.displayMultiplier(decimals: Int = 2) = "${displayDecimal(decimals)}x"
fun Double.displayPercent(decimals: Int = 1) = "${(this * 100f).displayDecimal(decimals)}%"

fun Float.clamp(min: Float? = null, max: Float? = null) = when {
    min != null && min > this -> min
    max != null && max < this -> max
    else -> this
}

data class Size(val width: Float, val height: Float)

val Float.radDeg get() = this * MathUtils.radDeg
val Float.degRad get() = this * MathUtils.degRad
fun Vector2.toAngle() = atan2(-x.toDouble(), y.toDouble()).toFloat()

fun Float.toVector(outVector: Vector2): Vector2 {
    outVector.x = (-sin(toDouble())).toFloat()
    outVector.y = cos(toDouble()).toFloat()
    return outVector
}


fun Boolean.toInt() = if (this) 1 else 0
val Vector2.adjacentNodes
    get() = listOf(
        Vector2(x - 1, y),
        Vector2(x + 1, y),
        Vector2(x, y - 1),
        Vector2(x, y + 1)
    )

fun Vector2.isAdjacentTo(position: Vector2) = adjacentNodes.contains(position)
