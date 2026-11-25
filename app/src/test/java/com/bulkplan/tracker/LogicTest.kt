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

    @Test fun defaultPlanRoundTripsThroughThePlanFileFormat() {
        val plan = DefaultPlan.create("2026-11-23")
        val reply = "Here you go!\n\n```bulkplan\n{\"bulkplan\":1,\"summary\":\"Upper/lower plus PPL.\",\"plan\":" +
            Repo.json.encodeToString(com.bulkplan.tracker.data.Plan.serializer(), plan) + "}\n```\n\nCopy this whole reply."
        val r = com.bulkplan.tracker.logic.PlanImport.parse(reply, today = "2026-11-20")
        assertEquals("Upper/lower plus PPL.", r.summary)
        assertEquals(plan.workouts.keys, r.plan.workouts.keys)
        assertEquals(plan.schedule.map { it.id }, r.plan.schedule.map { it.id })
        assertEquals(plan.targets, r.plan.targets)
        assertTrue("unexpected warnings: ${r.warnings}", r.warnings.isEmpty())
    }

    @Test fun planImportFixesCommonAiSlips() {
        val reply = """
Sure — here's your plan.
```bulkplan
{
  "bulkplan": 1,
  "plan": {
    "startDate": "2026-11-23", // Monday
    "weeks": 12,
    "targets": {"calories": 2800, "proteinPerLb": 1.0},
    "workouts": {
      "a": {"name": "Full body A", "day": 1, "exercises": [{"id": "squat", "name": "Squat", "sets": 3, "reps": 8, "muscle": "Quads", "compound": true}]},
      "b": {"name": "Full body B", "day": 4, "exercises": [{"id": "squat", "name": "Front squat", "sets": 3, "reps": "8–10", "muscle": "quads"}]},
    },
    "schedule": [
      {"id": "breakfast", "time": "7:00", "kind": "meal", "title": "Breakfast", "days": [], "kcal": 1400, "protein": 70},
      {"id": "breakfast", "time": "7:30 PM", "kind": "meal", "title": "Dinner", "days": [0,1,2,3,4,5,6], "kcal": 1400, "protein": 70},
      {"id": "gym", "time": "17:30", "kind": "workout", "title": "Train", "days": [1]},
      {"id": "bad", "time": "lunchtime", "kind": "task", "title": "Walk", "days": [2]},
    ]
  }
}
```
""".trimIndent()
        val r = com.bulkplan.tracker.logic.PlanImport.parse(reply, today = "2026-11-20")
        val p = r.plan
        assertEquals(listOf("squat", "squat-2"), p.workouts.values.flatMap { w -> w.exercises.map { it.id } })
        assertEquals("quads", p.workouts.getValue("a").exercises[0].muscle)
        assertEquals("8", p.workouts.getValue("a").exercises[0].reps)
        assertEquals(listOf("07:00", "19:30", "17:30"), p.schedule.map { it.time })
        assertEquals(listOf("breakfast", "breakfast-2", "gym"), p.schedule.map { it.id })
        assertEquals((0..6).toList(), p.schedule[0].days)
        assertEquals("train", p.schedule[2].kind)
        assertEquals(1, p.blocks.size)
        assertTrue(r.warnings.any { it.contains("unreadable time") })
        assertTrue(r.warnings.any { it.contains("Thursday") }) // workout B has no training time
    }

    @Test fun cutOffPlanSaysSo() {
        val cut = "```bulkplan\n{\"bulkplan\":1,\"plan\":{\"startDate\":\"2026-11-23\",\"workouts\":{\"a\":{\"name\":\"A\",\"day\":1,\"exercises\":["
        val e = runCatching { com.bulkplan.tracker.logic.PlanImport.parse(cut, today = "2026-11-20") }.exceptionOrNull()
        assertNotNull(e)
        assertTrue(e!!.message!!, e.message!!.contains("cut off"))
    }

    @Test fun optionalTestsCanBeSkipped() {
        val old = DefaultPlan.create("2026-11-23").let { p ->
            p.copy(milestones = p.milestones.filter { it.week != 26 } + com.bulkplan.tracker.data.Milestone(26, "Deload, DEXA scan, repeat bloodwork, photos"),
                prep = p.prep.map { it.copy(tag = null) })
        }
        val m = DefaultPlan.migrate(old)
        val s = com.bulkplan.tracker.data.Settings(skipTests = listOf("dexa", "blood"))
        assertFalse(PlanLogic.prepFor(m, s).any { it.id == "dexa" || it.id == "blood" })
        assertEquals(listOf("Deload, photos and measurements"), PlanLogic.milestonesFor(m, s).filter { it.week == 26 }.map { it.text })
        assertEquals(3, PlanLogic.milestonesFor(m, com.bulkplan.tracker.data.Settings()).count { it.week == 26 })
        assertFalse(Coach.rules(m, listOf("dexa")).contains("bloodwork"))
        assertTrue(Coach.rules(m, listOf("dexa")).contains("DEXA scans"))
    }

    @Test fun intakeDocumentCarriesAnswersAndFormat() {
        val a = mapOf("name" to "Liam", "weight" to "175", "wake" to "06:30", "trainDays" to "1,2,4,5", "dexa" to com.bulkplan.tracker.logic.Intake.SKIP, "blood" to "Include")
        val doc = com.bulkplan.tracker.logic.Intake.document(a, "2026-11-20")
        assertTrue(doc.contains("**First name:** Liam"))
        assertTrue(doc.contains("6:30 am"))
        assertTrue(doc.contains("Monday, Tuesday, Thursday, Friday"))
        assertTrue(doc.contains("```bulkplan"))
        assertTrue(doc.contains("\"schedule\""))
        assertEquals(listOf("dexa"), com.bulkplan.tracker.logic.Intake.skipTests(a))
    }

    @Test fun aiSettingsFallBackToTheOldClaudeKey() {
        val old = com.bulkplan.tracker.data.Settings(claudeKey = "sk-ant", claudeModel = "claude-sonnet-5-5")
        val c = com.bulkplan.tracker.net.Ai.cfg(old)
        assertEquals("anthropic", c.p.id); assertEquals("sk-ant", c.key); assertEquals("claude-sonnet-5-5", c.model)
        val fresh = com.bulkplan.tracker.net.Ai.cfg(com.bulkplan.tracker.data.Settings())
        assertEquals("gemini", fresh.p.id); assertFalse(com.bulkplan.tracker.net.Ai.hasKey(com.bulkplan.tracker.data.Settings()))
        val ds = com.bulkplan.tracker.net.Ai.cfg(com.bulkplan.tracker.data.Settings(aiProvider = "deepseek", aiKeys = mapOf("deepseek" to "k")))
        assertEquals("https://api.deepseek.com", ds.base); assertEquals("deepseek-chat", ds.model)
    }

    @Test fun coachSendsWhatYouTypedAndFreshDataAsSystem() {
        val old = listOf(
            com.bulkplan.tracker.data.ChatMessage("user", "RULES...PLAN JSON...\n---\nhi", display = "hi", failed = true),
            com.bulkplan.tracker.data.ChatMessage("note", "Couldn't reach the AI."),
            com.bulkplan.tracker.data.ChatMessage("user", "how am I doing?", display = "how am I doing?"),
        )
        assertEquals(listOf("user" to "how am I doing?"), com.bulkplan.tracker.net.Ai.prepare(old))
        val t = PlanLogic.today()
        val st = AppState(plan = DefaultPlan.create("2026-11-23"), intake = mapOf("age" to "26", "height" to "6'0\""))
        val logs = mapOf(t to DayLog(t, weight = 176.0, foods = listOf(FoodEntry("1", "Bagel", 300.0, 10.0, 55.0, 4.0, "Breakfast"))))
        val sys = Coach.systemPrompt(st, logs)
        assertTrue(sys.contains("Bagel (300 kcal"))
        assertTrue(sys.contains("Age: 26"))
        assertTrue(sys.contains("PLAN (JSON)"))
        assertTrue(sys.contains("wt 176"))
    }
}
