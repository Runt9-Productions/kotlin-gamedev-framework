package com.runt9.kgdf.ui.viewModel

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
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

    test("bindAdd replays every item already in the list") {
        val binding = emptyViewModel().ListBinding(listOf("primary", "secondary"))
        val added = mutableListOf<String>()

        binding.bindAdd { added += it }

        added shouldBe listOf("primary", "secondary")
    }

    test("add reports the new item to add subscribers and notifies plain binds") {
        val binding = emptyViewModel().ListBinding<String>()
        val added = mutableListOf<String>()
        val notified = mutableListOf<List<String>>()
        binding.bindAdd { added += it }
        binding.bind { notified += binding.get() }

        binding.add("primary")

        added shouldBe listOf("primary")
        notified shouldBe listOf(emptyList(), listOf("primary"))
    }

    test("bindRemove does not replay, and remove reports the removed item") {
        val binding = emptyViewModel().ListBinding(listOf("primary", "secondary"))
        val removed = mutableListOf<String>()

        binding.bindRemove { removed += it }
        removed.shouldBeEmpty()

        // Positive control: the same subscriber does fire once something is removed.
        binding.remove("primary")

        removed shouldBe listOf("primary")
        binding.get() shouldBe listOf("secondary")
    }
})
