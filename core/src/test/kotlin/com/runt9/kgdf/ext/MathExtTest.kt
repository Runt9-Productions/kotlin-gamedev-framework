package com.runt9.kgdf.ext

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.*

// The format follows the default locale, so every test pins one and the suite reads the same on any machine.
class MathExtTest : FunSpec({
    val originalLocale = Locale.getDefault()
    beforeTest { Locale.setDefault(Locale.US) }
    afterSpec { Locale.setDefault(originalLocale) }

    context("displayDecimal keeping trailing zeros") {
        test("pads to the requested places") {
            3.0.displayDecimal(2) shouldBe "3.00"
            2.2.displayDecimal(2) shouldBe "2.20"
        }

        test("is the default") {
            2.2.displayDecimal() shouldBe "2.20"
        }
    }

    context("displayDecimal trimming trailing zeros") {
        test("a whole number shows no separator") {
            3.0.displayDecimal(2, trimTrailingZeros = true) shouldBe "3"
            10.0.displayDecimal(2, trimTrailingZeros = true) shouldBe "10"
            (-3.0).displayDecimal(2, trimTrailingZeros = true) shouldBe "-3"
        }

        test("drops only the zeros after the last significant place") {
            2.2.displayDecimal(2, trimTrailingZeros = true) shouldBe "2.2"
            2.22.displayDecimal(2, trimTrailingZeros = true) shouldBe "2.22"
            100.5.displayDecimal(2, trimTrailingZeros = true) shouldBe "100.5"
        }

        test("rounds to the requested places before trimming") {
            2.999.displayDecimal(2, trimTrailingZeros = true) shouldBe "3"
            2.204.displayDecimal(2, trimTrailingZeros = true) shouldBe "2.2"
        }

        test("leaves zeros in the whole number alone when there are no decimal places") {
            10.0.displayDecimal(0, trimTrailingZeros = true) shouldBe "10"
        }

        test("drops a comma separator as well as a point") {
            Locale.setDefault(Locale.GERMANY)

            3.0.displayDecimal(2, trimTrailingZeros = true) shouldBe "3"
            2.2.displayDecimal(2, trimTrailingZeros = true) shouldBe "2,2"
        }

        test("a Float trims the same as a Double") {
            2.2f.displayDecimal(2, trimTrailingZeros = true) shouldBe "2.2"
            3f.displayDecimal(2, trimTrailingZeros = true) shouldBe "3"
        }

        test("NaN and the infinities show as themselves") {
            Double.NaN.displayDecimal(2, trimTrailingZeros = true) shouldBe "NaN"
            Double.POSITIVE_INFINITY.displayDecimal(2, trimTrailingZeros = true) shouldBe "Infinity"
            Double.NEGATIVE_INFINITY.displayDecimal(2, trimTrailingZeros = true) shouldBe "-Infinity"
        }

        test("a negative that rounds to zero shows as zero with no sign") {
            (-0.001).displayDecimal(2, trimTrailingZeros = true) shouldBe "0"
            (-0.0).displayDecimal(2, trimTrailingZeros = true) shouldBe "0"
            (-0.3).displayDecimal(0, trimTrailingZeros = true) shouldBe "0"
        }
    }
})
