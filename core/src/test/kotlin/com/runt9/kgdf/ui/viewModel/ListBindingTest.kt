package com.runt9.kgdf.ui.viewModel

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ListBindingTest : FunSpec({
    test("a caller's list mutated in place and set again notifies every time") {
        val callerList = mutableListOf("primary")
        val binding = emptyViewModel().ListBinding(callerList)
        val notified = mutableListOf<List<String>>()
        binding.bind { notified += binding.get().toList() }

        callerList += "secondary"
        binding(callerList)
        callerList += "modifier"
        binding(callerList)

        notified shouldBe listOf(
            listOf("primary"),
            listOf("primary", "secondary"),
            listOf("primary", "secondary", "modifier"),
        )
    }
})
