package com.bulkplan.tracker

import com.bulkplan.tracker.data.AppState
import com.bulkplan.tracker.data.Change
import com.bulkplan.tracker.data.DayLog
import com.bulkplan.tracker.data.DefaultPlan
import com.bulkplan.tracker.data.FoodEntry
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.logic.Coach
import com.bulkplan.tracker.logic.PlanLogic
import com.bulkplan.tracker.net.Garmin
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private val plan = DefaultPlan.create("2026-11-23")
    private fun p(vararg s: Any) = s.map { if (it is Int) JsonPrimitive(it) else JsonPrimitive(it.toString()) }

    @Test fun everyDayOfPlannedMealsAddsUpTo3500() {
        for (i in 0..6L) {
            val d = PlanLogic.addD("2026-11-23", i)
            val kcal = PlanLogic.itemsFor(plan, d).filter { it.kind == "meal" }.sumOf { it.kcal ?: 0 }
            assertEquals("kcal on $d", 3500, kcal)
        }
    }

    @Test fun weeksAndBlocks() {
        assertEquals(0, PlanLogic.weekOf(plan, "2026-11-17"))
        assertEquals(1, PlanLogic.weekOf(plan, "2026-11-23"))
        assertEquals(1, PlanLogic.weekOf(plan, "2026-11-29"))
        assertEquals(2, PlanLogic.weekOf(plan, "2026-11-30"))
        val bench = plan.workouts.getValue("upper").exercises.first()
        assertEquals(2, PlanLogic.setsFor(plan, bench, PlanLogic.blockFor(plan, 1)))
        assertEquals(3, PlanLogic.setsFor(plan, bench, PlanLogic.blockFor(plan, 5)))
        assertEquals(2, PlanLogic.setsFor(plan, bench, PlanLogic.blockFor(plan, 12)))
    }

    @Test fun mondayTrainingTitleUsesUpperWorkout() {
        val train = PlanLogic.itemsFor(plan, "2026-11-23").first { it.kind == "train" }
        assertEquals("Train: Upper", train.title)
        assertEquals("17:30", train.time)
    }

    @Test fun claudeChangesApplyAndRollBack() {
        val changes = listOf(
            Change("set", p("targets", "calories"), JsonPrimitive(3700)),
            Change("set", p("schedule", "caf", "time"), JsonPrimitive("14:00")),
            Change("add", p("workouts", "upper", "exercises"), Repo.json.parseToJsonElement(
                """{"id":"face-pull","name":"Face pull","sets":2,"reps":"12–15","muscle":"rear delts","compound":false,"note":""}""")),
            Change("remove", p("workouts", "pull", "exercises", "shrug")),
            Change("set", p("schedule", "nope", "time"), JsonPrimitive("1")),
            Change("set", p("targets", "calories"), JsonPrimitive("lots")),
        )
        val (np, res) = Coach.apply(plan, changes)
        assertNotNull(np)
        assertEquals(listOf(true, true, true, true, false, false), res.map { it.ok })
        assertEquals(3700, np!!.targets.calories)
        assertEquals("14:00", np.schedule.first { it.id == "caf" }.time)
        assertEquals("face-pull", np.workouts.getValue("upper").exercises.last().id)
        assertFalse(np.workouts.getValue("pull").exercises.any { it.id == "shrug" })
        assertEquals("Caffeine 2:30 pm", res[1].label.split(" › ")[1])
    }

    @Test fun proposalParsing() {
        val text = "Looks good.\n\n```json\n{\"summary\":\"More food\",\"changes\":[{\"op\":\"set\",\"path\":[\"targets\",\"calories\"],\"value\":3700,\"why\":\"flat\"}]}\n```"
        val prop = Coach.parseProposal(text)
        assertEquals("More food", prop!!.summary)
        assertEquals(1, prop.changes.size)
        assertEquals("Looks good.", Coach.stripJson(text))
        assertNull(Coach.parseProposal("How has your sleep been?"))
    }

    @Test fun importsWebTrackerBackup() {
        val web = """{"app":"bulkplan","v":1,"S":{"plan":${Repo.json.encodeToString(com.bulkplan.tracker.data.Plan.serializer(), plan)},
          "prepDone":{"dexa":true},"changelog":[],"coach":{"messages":[{"role":"note","content":"hi"}],"pending":null},"settings":{"alerts":false}},
          "logs":{"2026-11-24":{"date":"2026-11-24","weight":176.4,"cal":3400,"protein":180,"checks":{"wake":true},
            "sets":{"bench":[{"w":185,"r":8,"ok":true},null,{"w":185,"r":7}]},"m":{"waist":33.5,"bp":"120/80"},"energy":4,"sore":null},
            "2026-11-25":{"weight":"oops"}}}"""
        val root = Repo.json.parseToJsonElement(web).jsonObject
        val st = Repo.json.decodeFromJsonElement<AppState>(root.getValue("S"))
        assertEquals(true, st.prepDone["dexa"])
        assertFalse(st.settings.alerts)
        val logs = Repo.decodeLogs(root.getValue("logs").jsonObject)
        val d = logs.getValue("2026-11-24")
        assertEquals(176.4, d.weight!!, 0.001)
        assertEquals(2, PlanLogic.validSets(d.sets["bench"]).size)
        assertTrue("bad day is skipped, not fatal", logs.size in 1..2)
    }

    @Test fun foodDiaryOverridesTypedCalories() {
        val l = DayLog("2026-11-24", cal = 1000.0, foods = listOf(
            FoodEntry("a", "Eggs", 300.0, 25.0), FoodEntry("b", "Rice", 400.0, 8.0, 88.0, 1.0)))
        assertEquals(700.0, PlanLogic.kcalOf(l)!!, 0.01)
        assertEquals(33.0, PlanLogic.proteinOf(l)!!, 0.01)
        assertEquals(1000.0, PlanLogic.kcalOf(DayLog("x", cal = 1000.0))!!, 0.01)
    }

    @Test fun progressionSuggestion() {
        val bench = plan.workouts.getValue("upper").exercises.first()
        val b = PlanLogic.blockFor(plan, 8)
        val top = "2026-11-24" to listOf(com.bulkplan.tracker.data.SetEntry(185.0, 8.0), com.bulkplan.tracker.data.SetEntry(185.0, 8.0))
        assertTrue(PlanLogic.suggestion(bench, b, "upper", top).contains("190"))
        val squat = plan.workouts.getValue("lower").exercises.first()
        val sq = "2026-11-24" to listOf(com.bulkplan.tracker.data.SetEntry(225.0, 8.0))
        assertTrue(PlanLogic.suggestion(squat, b, "lower", sq).contains("235"))
    }

    @Test fun garminParsingIsTolerant() {
        val dailies = """[{"calendarDate":"2026-11-24","totalSteps":8123,"restingHeartRate":58}]"""
        val sleep = """{"dailySleepDTOs":[{"calendarDate":"2026-11-24","sleepTimeSeconds":27000}]}"""
        val weight = """{"dateWeightList":[{"calendarDate":"2026-11-24","weight":80000.0}]}"""
        val a = Garmin.parse(dailies).getValue("2026-11-24")
        assertEquals(8123.0, a.getValue("steps"), 0.1)
        assertEquals(58.0, a.getValue("rhr"), 0.1)
        assertEquals(27000.0, Garmin.parse(sleep).getValue("2026-11-24").getValue("sleepS"), 0.1)
        assertEquals(176.37, Garmin.parse(weight).getValue("2026-11-24").getValue("weightLb"), 0.05)
    }
}
