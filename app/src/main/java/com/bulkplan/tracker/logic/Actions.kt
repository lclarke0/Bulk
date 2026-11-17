package com.bulkplan.tracker.logic

import com.bulkplan.tracker.data.FoodEntry
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.data.SavedFood
import com.bulkplan.tracker.data.ScheduleItem
import java.time.Instant

/** Shared actions used by the screens and by notification buttons. */
object Actions {
    val MEALS = listOf("Breakfast", "Lunch", "Dinner", "Snacks")

    fun slotFor(time: String): String {
        val m = PlanLogic.mins(time)
        return when {
            m < 10 * 60 -> "Breakfast"
            m in 12 * 60 until 15 * 60 -> "Lunch"
            m in 19 * 60 until 21 * 60 + 30 -> "Dinner"
            else -> "Snacks"
        }
    }

    fun newId() = "f" + System.currentTimeMillis().toString(36) + (100..999).random()

    /** Ticks or unticks a schedule item. Ticking a planned meal also logs it in the food diary. */
    fun setChecked(date: String, item: ScheduleItem, on: Boolean) {
        Repo.updateLog(date) { l ->
            val checks = if (on) l.checks + (item.id to true) else l.checks - item.id
            var foods = l.foods
            if (item.kind == "meal" && item.kcal != null) {
                val src = "plan:${item.id}"
                foods = foods.filterNot { it.source == src }
                if (on) foods = foods + FoodEntry(
                    id = newId(), name = item.detail.removeSuffix(".").replaceFirstChar { it.uppercase() },
                    kcal = item.kcal.toDouble(), protein = (item.protein ?: 0).toDouble(),
                    meal = slotFor(item.time), source = src, at = Instant.now().toString(),
                    detail = "From your plan · ${item.protein ?: 0} g protein",
                )
            }
            l.copy(checks = checks, foods = foods)
        }
    }

    fun addFood(date: String, f: FoodEntry, save: Boolean = false) {
        Repo.updateLog(date) { it.copy(foods = it.foods + f) }
        if (save) saveFood(f)
        else Repo.update { s ->
            // Count uses of foods that are already saved, so favourites rise to the top.
            val i = s.savedFoods.indexOfFirst { it.name.equals(f.name, true) }
            if (i < 0) s else s.copy(savedFoods = s.savedFoods.toMutableList().also { it[i] = it[i].copy(uses = it[i].uses + 1) })
        }
    }

    fun saveFood(f: FoodEntry) = Repo.update { s ->
        if (s.savedFoods.any { it.name.equals(f.name, true) }) s
        else s.copy(savedFoods = s.savedFoods + SavedFood(newId(), f.name, f.kcal, f.protein, f.carbs, f.fat, 1))
    }

    fun moveFood(date: String, id: String, meal: String) =
        Repo.updateLog(date) { d -> d.copy(foods = d.foods.map { if (it.id == id) it.copy(meal = meal) else it }) }

    /** Planned calories per meal slot for a day, used as each meal card's budget. */
    fun mealBudgets(plan: com.bulkplan.tracker.data.Plan, date: String): Map<String, Int> =
        PlanLogic.itemsFor(plan, date).filter { it.kind == "meal" && it.kcal != null }
            .groupBy { slotFor(it.time) }.mapValues { (_, l) -> l.sumOf { it.kcal ?: 0 } }

    fun removeFood(date: String, id: String) {
        val l = Repo.log(date)
        val f = l.foods.firstOrNull { it.id == id } ?: return
        Repo.updateLog(date) { d ->
            val checks = if (f.source.startsWith("plan:")) d.checks - f.source.removePrefix("plan:") else d.checks
            d.copy(foods = d.foods.filterNot { it.id == id }, checks = checks)
        }
    }
}
