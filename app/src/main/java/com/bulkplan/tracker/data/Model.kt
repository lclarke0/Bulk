package com.bulkplan.tracker.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 * The plan and log shapes mirror the web tracker exactly (same field names),
 * so a backup from the web version imports as-is and Claude's change paths
 * (["targets","calories"], ["workouts","upper","exercises","bench","sets"] ...) work in both.
 */

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val sets: Int = 3,
    val reps: String = "8–12",
    val muscle: String = "chest",
    val compound: Boolean = false,
    val note: String = "",
)

@Serializable
data class Workout(val name: String, val day: Int, val exercises: List<Exercise> = emptyList())

@Serializable
data class Block(
    val from: Int,
    val to: Int,
    val name: String,
    val mode: String = "full",
    val rir: String = "0–2",
    val note: String = "",
)

@Serializable
data class ScheduleItem(
    val id: String,
    val time: String,
    val kind: String = "task",
    val title: String,
    val detail: String = "",
    val days: List<Int> = emptyList(),
    val protein: Int? = null,
    val kcal: Int? = null,
    val dur: Int? = null,
)

@Serializable
data class PrepItem(val id: String, val text: String, /** "dexa" or "blood" for optional tests. */ val tag: String? = null)

@Serializable
data class Milestone(val week: Int, val text: String, val tag: String? = null)

@Serializable
data class Targets(
    val calories: Int = 3500,
    val proteinPerLb: Double = 1.0,
    val fatG: Int = 100,
    val steps: Int = 7000,
    val sleepH: Double = 8.0,
    val gainEarly: Double = 0.75,
    val gainLate: Double = 0.5,
    val earlyWeeks: Int = 8,
)

@Serializable
data class Profile(
    val startWeight: Double = 175.0,
    val goalWeight: Double = 225.0,
    val goalMuscleLb: Double = 15.0,
    val sixMonthWeight: Double = 190.0,
    val lifting: String = "",
    val equipment: String = "",
    val food: String = "",
)

@Serializable
data class Plan(
    val startDate: String,
    val weeks: Int = 26,
    val profile: Profile = Profile(),
    val targets: Targets = Targets(),
    val weakMuscles: List<String> = emptyList(),
    val blocks: List<Block> = emptyList(),
    val workouts: Map<String, Workout> = emptyMap(),
    val schedule: List<ScheduleItem> = emptyList(),
    val prep: List<PrepItem> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
)

@Serializable
data class SetEntry(val w: Double? = null, val r: Double? = null, val ok: Boolean? = null)

@Serializable
data class FoodEntry(
    val id: String,
    val name: String,
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    /** Breakfast, Lunch, Dinner or Snacks. */
    val meal: String = "Snacks",
    /** plan:<scheduleId>, barcode, search, claude, label, quick, saved */
    val source: String = "quick",
    val at: String = "",
    /** Portion or note shown under the name, e.g. "1 bowl, 1 banana". */
    val detail: String = "",
)

@Serializable
data class SavedFood(
    val id: String,
    val name: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val uses: Int = 0,
)

@Serializable
data class DayLog(
    val date: String,
    val weight: Double? = null,
    val cal: Double? = null,
    val protein: Double? = null,
    val sleep: Double? = null,
    val steps: Double? = null,
    val energy: Int? = null,
    val sore: Int? = null,
    val hunger: Int? = null,
    val notes: String? = null,
    val checks: Map<String, Boolean> = emptyMap(),
    val sets: Map<String, List<SetEntry?>> = emptyMap(),
    val m: Map<String, JsonElement?> = emptyMap(),
    val workout: String? = null,
    val foods: List<FoodEntry> = emptyList(),
    /** What Garmin reported for this day, kept apart from what you typed. */
    val garmin: GarminDay? = null,
)

@Serializable
data class GarminDay(
    val steps: Double? = null,
    val sleepH: Double? = null,
    val restingHr: Double? = null,
    val weight: Double? = null,
    val bodyBattery: Double? = null,
    val syncedAt: String = "",
)

@Serializable
data class Change(
    val op: String,
    val path: List<JsonElement> = emptyList(),
    val value: JsonElement? = null,
    val why: String? = null,
)

@Serializable
data class Proposal(
    val summary: String = "Plan update",
    val changes: List<Change> = emptyList(),
    val source: String = "AI",
    val error: String? = null,
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val display: String? = null,
    val failed: Boolean? = null,
)

@Serializable
data class Coach(val messages: List<ChatMessage> = emptyList(), val pending: Proposal? = null)

@Serializable
data class ChangeLogEntry(
    val at: String,
    val summary: String,
    val count: Int = 1,
    val source: String = "you",
    val before: Plan? = null,
)

@Serializable
data class Settings(
    val name: String = "",
    val alerts: Boolean = true,
    val claudeKey: String = "",
    val claudeModel: String = "claude-sonnet-5-5",
    val garminKey: String = "",
    val garminLastSync: String = "",
    val garminStatus: String = "",
    val usdaKey: String = "",
    val weighNudge: Boolean = true,
    /** Which AI service the coach uses: gemini, openai, anthropic, deepseek, openrouter, mistral, custom. Blank = pick from older settings. */
    val aiProvider: String = "",
    /** API key per provider, so switching back and forth keeps each one. */
    val aiKeys: Map<String, String> = emptyMap(),
    val aiModels: Map<String, String> = emptyMap(),
    /** Base URL for the "custom" OpenAI-compatible provider, ending in /v1. */
    val aiBaseUrl: String = "",
    /** Schedule kinds (meal, train, supp, skin, task, coach, sleep) with reminders turned off. */
    val remindOff: List<String> = emptyList(),
    /** Optional tests left out of the plan: "dexa", "blood". */
    val skipTests: List<String> = emptyList(),
    /** "" = intake not seen yet, "skipped", "pending" (request made, plan not imported), "done". */
    val intakeStatus: String = "",
)

@Serializable
data class AppState(
    val plan: Plan = DefaultPlan.create(),
    val prepDone: Map<String, Boolean> = emptyMap(),
    val changelog: List<ChangeLogEntry> = emptyList(),
    val coach: Coach = Coach(),
    val settings: Settings = Settings(),
    val savedFoods: List<SavedFood> = emptyList(),
    /** Answers from the intake questionnaire, by question id. */
    val intake: Map<String, String> = emptyMap(),
)

/** The web tracker's backup file: {"app":"bulkplan","v":1,"S":{...},"logs":{...}} */
@Serializable
data class Backup(
    val app: String = "bulkplan",
    val v: Int = 1,
    val S: AppState,
    val logs: Map<String, DayLog> = emptyMap(),
)
