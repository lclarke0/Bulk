package com.bulkplan.tracker

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.bulkplan.tracker.data.AppState
import com.bulkplan.tracker.data.ChatMessage
import com.bulkplan.tracker.data.Coach
import com.bulkplan.tracker.data.DayLog
import com.bulkplan.tracker.data.DefaultPlan
import com.bulkplan.tracker.data.FoodEntry
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.data.SetEntry
import com.bulkplan.tracker.data.Settings
import com.bulkplan.tracker.logic.PlanLogic
import com.bulkplan.tracker.ui.BulkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Renders each main screen with sample data to app/build/screens so the design can be checked without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h2000dp-xhdpi")
class ScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun seed() {
        Repo.init(ApplicationProvider.getApplicationContext())
        val t = PlanLogic.today()
        // A Monday two weeks before this week's Monday, so the plan is in week 3.
        val start = PlanLogic.addD(PlanLogic.addD(t, -((PlanLogic.dow(t) + 6) % 7).toLong()), -14)
        val plan = DefaultPlan.create(start)
        val logs = mutableMapOf<String, DayLog>()
        for (i in 1..16L) {
            val d = PlanLogic.addD(t, -i)
            logs[d] = DayLog(d, weight = 175.0 + (16 - i) * 0.11 + (i % 3) * 0.3, cal = 3300.0 + (i % 4) * 80, protein = 165.0 + (i % 5) * 6, sleep = 7.4, steps = 8200.0,
                sets = mapOf("bench" to listOf(SetEntry(165.0, 8.0, true), SetEntry(165.0, 7.0, true))))
        }
        logs[t] = DayLog(t, weight = 177.2, sleep = 7.8, energy = 4,
            checks = mapOf("wake" to true, "skin-am" to true, "breakfast" to true),
            foods = listOf(
                FoodEntry("1", "Breakfast", 700.0, 35.0, 80.0, 28.0, "Breakfast", "plan:breakfast", detail = "4 eggs, a bagel with cheese, a banana, 12 oz whole milk"),
                FoodEntry("2", "Greek yogurt", 160.0, 17.0, 9.0, 4.0, "Snacks", "quick", detail = "1 cup"),
                FoodEntry("3", "Chicken & rice bowl", 520.0, 45.0, 60.0, 9.0, "Lunch", "quick", detail = "6 oz chicken, 1 cup rice"),
            ),
            sets = mapOf("squat" to listOf(SetEntry(205.0, 8.0, true), SetEntry(205.0, 7.0, false))),
        )
        Repo.replaceAll(
            AppState(plan = plan, settings = Settings(name = "Liam", claudeKey = "sk-test"),
                coach = Coach(listOf(
                    ChatMessage("user", "Start my weekly check-in.", display = "Start my weekly check-in."),
                    ChatMessage("assistant", "Your 2-week average is up 1.4 lb against a 1.5 lb target, so calories stay at 3,500. Protein has been about 15 g short most days. How has your appetite been in the evenings?"),
                ))),
            logs,
        )
    }

    private fun shot(name: String, tab: String) {
        seed()
        rule.mainClock.autoAdvance = false
        rule.setContent { BulkTheme { App(tab) {} } }
        repeat(4) { rule.mainClock.advanceTimeBy(500); rule.waitForIdle() }
        val v = rule.activity.window.decorView.rootView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        val dir = File("build/screens").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun today() = shot("1-today", "today")
    @Test fun food() = shot("2-food", "food")
    @Test fun workout() = shot("3-workout", "workout")
    @Test fun coach() = shot("4-coach", "coach")
    @Test fun progress() = shot("5-progress", "progress")
}
