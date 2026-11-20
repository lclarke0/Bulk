package com.bulkplan.tracker.logic

import com.bulkplan.tracker.data.DefaultPlan

/** The first-launch questionnaire and the plan-request document it produces. */
object Intake {
    enum class Type { TEXT, LONG, NUMBER, CHOICE, MULTI, DAYS, TIME }

    data class Q(
        val id: String,
        val label: String,
        val type: Type,
        val options: List<String> = emptyList(),
        val hint: String = "",
        val required: Boolean = false,
    )

    data class Step(val title: String, val blurb: String, val questions: List<Q>)

    const val SKIP = "Skip"

    val STEPS = listOf(
        Step("About you", "The basics your AI needs to set calories and training.", listOf(
            Q("name", "First name", Type.TEXT, required = true),
            Q("age", "Age", Type.NUMBER, required = true),
            Q("sex", "Sex", Type.CHOICE, listOf("Male", "Female", "Prefer not to say"), required = true),
            Q("height", "Height", Type.TEXT, hint = "e.g. 5'11\"", required = true),
            Q("weight", "Current weight (lb)", Type.NUMBER, required = true),
            Q("bodyfat", "How would you describe your build right now?", Type.CHOICE, listOf("Lean (abs visible)", "Average", "Carrying some extra fat", "Not sure")),
        )),
        Step("Your goal", "What you want this plan to get you.", listOf(
            Q("goal", "Main goal", Type.CHOICE, listOf("Build muscle (bulk)", "Lean bulk (slow, minimal fat)", "Recomp (same weight, more muscle)", "Lose fat, keep muscle", "Get stronger"), required = true),
            Q("goalWeight", "Goal weight (lb)", Type.NUMBER, hint = "Long-term, even if it takes longer than this plan"),
            Q("muscleGoal", "Muscle you want to add (lb)", Type.NUMBER, hint = "Optional"),
            Q("weeks", "Plan length", Type.CHOICE, listOf("12 weeks", "16 weeks", "26 weeks (6 months)", "52 weeks (1 year)"), required = true),
            Q("startDate", "Start date (YYYY-MM-DD)", Type.TEXT, hint = "A Monday works best"),
            Q("why", "Anything specific you’re after?", Type.LONG, hint = "e.g. bigger arms, a meet, a wedding, look better at the beach"),
        )),
        Step("Training", "So workouts fit your experience, time and gym.", listOf(
            Q("experience", "Lifting experience", Type.CHOICE, listOf("New (under 6 months)", "6 months to 2 years", "2 to 5 years", "5+ years"), required = true),
            Q("current", "Are you training right now?", Type.CHOICE, listOf("Yes, consistently", "On and off", "Not right now (taking time off)"), required = true),
            Q("daysPerWeek", "Days per week you can lift", Type.CHOICE, listOf("2", "3", "4", "5", "6"), required = true),
            Q("trainDays", "Which days work best?", Type.DAYS),
            Q("sessionLength", "Time per session", Type.CHOICE, listOf("45 min", "60 min", "75 min", "90 min", "2 hours")),
            Q("trainTime", "When do you usually train?", Type.TEXT, hint = "e.g. 5:30 pm weekdays, 10 am Saturday"),
            Q("equipment", "Equipment you have", Type.MULTI, listOf(
                "Full commercial gym", "Power rack", "Barbell and plates", "Dumbbells", "Flat bench", "Adjustable bench",
                "Cable pulleys", "Leg press / hack squat", "Leg curl / extension", "Smith machine", "Pull-up bar", "Dip station",
                "Landmine", "EZ curl bar", "Resistance bands", "Kettlebells",
            )),
            Q("equipmentNotes", "Other equipment or limits", Type.LONG, hint = "e.g. dumbbells only go to 75 lb, no spotter"),
            Q("injuries", "Injuries or anything that hurts", Type.LONG, hint = "Leave blank if none"),
            Q("priority", "Muscles you most want to bring up", Type.MULTI, DefaultPlan.MUSCLES),
            Q("exercises", "Exercises you love or hate", Type.LONG),
        )),
        Step("Daily life", "So meals, training and sleep fit around your day.", listOf(
            Q("wake", "Usual wake-up time", Type.TIME, required = true),
            Q("bed", "Usual bedtime", Type.TIME, required = true),
            Q("work", "Work or school hours", Type.TEXT, hint = "e.g. 8–5 weekdays, away from home"),
            Q("workFood", "Food storage at work", Type.CHOICE, listOf("Fridge and microwave", "Fridge only", "Nothing: needs to be shelf-stable", "I’m home all day")),
            Q("cooking", "How much do you cook?", Type.CHOICE, listOf("Most meals", "Some meals", "Rarely: I need simple options")),
            Q("prepDays", "Days you could meal prep", Type.DAYS),
            Q("steps", "Daily steps right now", Type.CHOICE, listOf("Under 4,000", "4,000–7,000", "7,000–10,000", "Over 10,000", "Not sure")),
            Q("sleepNow", "Hours of sleep you usually get", Type.NUMBER),
            Q("stress", "Stress level", Type.CHOICE, listOf("Low", "Medium", "High")),
        )),
        Step("Food", "So the meal plan is food you’ll actually eat.", listOf(
            Q("meals", "Meals and snacks per day", Type.CHOICE, listOf("3", "4", "5", "6+")),
            Q("diet", "Diet style", Type.CHOICE, listOf("Anything", "Vegetarian", "Vegan", "Pescatarian", "Halal", "Kosher", "Gluten-free", "Dairy-free")),
            Q("allergies", "Food allergies or intolerances", Type.LONG, hint = "Leave blank if none"),
            Q("dislikes", "Foods you won’t eat", Type.LONG),
            Q("favorites", "Foods you love", Type.LONG),
            Q("appetite", "Appetite", Type.CHOICE, listOf("Small: eating a lot is hard", "Normal", "Big: always hungry")),
            Q("budget", "Food budget", Type.CHOICE, listOf("Tight", "Moderate", "Flexible")),
            Q("caffeine", "Caffeine", Type.CHOICE, listOf("None", "1 coffee a day", "2–3 a day", "Pre-workout and coffee")),
            Q("supplements", "Supplements you’re open to", Type.MULTI, listOf("Creatine", "Protein powder", "Caffeine", "Vitamin D", "Fish oil", "Multivitamin", "Electrolytes", "None")),
        )),
        Step("Extras", "Optional add-ons. You can change these later in Settings.", listOf(
            Q("dexa", "DEXA body-composition scans (start and end of the plan)", Type.CHOICE, listOf("Include", SKIP), required = true),
            Q("blood", "Bloodwork (start and end of the plan)", Type.CHOICE, listOf("Include", SKIP), required = true),
            Q("skin", "Put a skin care routine on the schedule?", Type.CHOICE, listOf("Yes", "No")),
            Q("skinType", "Skin type", Type.CHOICE, listOf("Oily", "Dry", "Combination", "Normal", "Sensitive")),
            Q("tracker", "Fitness watch", Type.CHOICE, listOf("Garmin", "Other watch", "None")),
            Q("other", "Anything else your AI should know?", Type.LONG),
        )),
    )

    val ALL: List<Q> = STEPS.flatMap { it.questions }

    fun missing(step: Step, a: Map<String, String>) = step.questions.filter { it.required && a[it.id].isNullOrBlank() }

    /** Answers prefilled from the current plan and settings, so redoing the intake is quick. */
    fun prefill(name: String, plan: com.bulkplan.tracker.data.Plan, skip: List<String>): Map<String, String> = buildMap {
        if (name.isNotBlank()) put("name", name)
        put("weight", PlanLogic.fmtN(plan.profile.startWeight).replace(",", ""))
        put("goalWeight", PlanLogic.fmtN(plan.profile.goalWeight).replace(",", ""))
        put("startDate", DefaultPlan.nextMonday())
        put("wake", "06:30"); put("bed", "22:30")
        put("dexa", if ("dexa" in skip) SKIP else "Include")
        put("blood", if ("blood" in skip) SKIP else "Include")
    }

    fun skipTests(a: Map<String, String>) = listOfNotNull("dexa".takeIf { a["dexa"] == SKIP }, "blood".takeIf { a["blood"] == SKIP })

    private fun pretty(q: Q, v: String): String = when (q.type) {
        Type.TIME -> runCatching { PlanLogic.fmtT(v) }.getOrDefault(v)
        Type.DAYS -> v.split(",").mapNotNull { it.trim().toIntOrNull() }.sortedBy { (it + 6) % 7 }.joinToString(", ") { PlanLogic.DAYN[it] }
        else -> v
    }

    // ---------------------------------------------------------------- the document

    const val FENCE = "bulkplan"

    val SCHEMA = """
```text
{
  "bulkplan": 1,
  "summary": "2–4 sentences on the approach, for the person",
  "plan": {
    "startDate": "YYYY-MM-DD",          // a Monday, on or after today
    "weeks": 26,                        // plan length in weeks
    "profile": {
      "startWeight": 175,               // lb today
      "goalWeight": 225,                // long-term goal, lb
      "goalMuscleLb": 15,               // long-term muscle goal, lb (0 if none)
      "sixMonthWeight": 190,            // expected weight at the END of this plan, lb
      "lifting": "one line on experience and current training",
      "equipment": "one line listing their equipment",
      "food": "one line: dislikes, allergies, favourites, where they eat"
    },
    "targets": {
      "calories": 3200,                 // kcal per day; the sum of the day's meal kcal should match within ~5%
      "proteinPerLb": 1.0,              // grams of protein per lb of bodyweight (0.7–1.1)
      "fatG": 90,                       // grams of fat per day
      "steps": 7000,                    // daily step target
      "sleepH": 8,                      // hours of sleep target
      "gainEarly": 0.5,                 // lb per week in the first phase; NEGATIVE to lose weight
      "gainLate": 0.375,                // lb per week after that
      "earlyWeeks": 8                   // how many weeks the first phase lasts
    },
    "weakMuscles": ["side delts"],      // muscles to prioritise; each from the muscle list below
    "blocks": [                         // training phases; must cover week 1 to "weeks" with no gaps
      {"from": 1, "to": 4, "name": "Ramp-in", "mode": "c3", "rir": "2–3", "note": "what to do in this phase"}
    ],
    "workouts": {                       // one entry per training day; key = short lowercase id
      "upper": {
        "name": "Upper",
        "day": 1,                       // 0 = Sunday, 1 = Monday … 6 = Saturday; one workout per day
        "exercises": [
          {"id": "bench", "name": "Barbell bench press", "sets": 3, "reps": "6–10",
           "muscle": "chest", "compound": true, "note": "short coaching cue"}
        ]
      }
    },
    "schedule": [                       // the repeating weekly routine, shown and reminded in the app
      {"id": "breakfast", "time": "07:00", "kind": "meal", "title": "Breakfast",
       "detail": "4 eggs, bagel with cheese, banana, 12 oz whole milk", "days": [0,1,2,3,4,5,6],
       "protein": 35, "kcal": 700},
      {"id": "train", "time": "17:30", "kind": "train", "title": "Train", "detail": "", "days": [1,2,4,5], "dur": 75}
    ],
    "prep": [                           // one-off things to do before week 1
      {"id": "supps", "text": "Buy creatine and protein powder"}
    ],
    "milestones": [                     // notable weeks shown in the app
      {"week": 12, "text": "Deload week"}
    ]
  }
}
```

Field rules:
- Muscle list (use exactly these words for "muscle" and "weakMuscles"): ${DefaultPlan.MUSCLES.joinToString(", ")}.
- Block "mode" sets how many sets the app shows: "two" = 2 sets of everything (ramp-in); "c3" = 3 sets on compound lifts, 2 on isolation; "full" = sets as written; "half" = half the sets (deload); "spec" = as written plus 1 set for weakMuscles. "rir" = reps in reserve, e.g. "0–2".
- "kind" is one of: "meal", "train", "supp" (supplements and caffeine), "skin" (skin care and showers), "task" (meal prep, groceries, walks, check-ins with yourself), "coach" (weekly AI check-in), "sleep" (lights out).
- Meals need "protein" (g) and "kcal". Training items need "dur" (minutes). Times are 24-hour "HH:MM".
- Every day that has a workout needs a schedule item of kind "train" on that day; title it just "Train" (the app shows the workout name). Different train times on different days are fine as separate items.
- Include one "coach" item a week (e.g. Sunday evening) titled "Weekly check-in with your coach".
- ids: lowercase-with-dashes, unique within their list; exercise ids unique across ALL workouts.
- Optional tests: put a DEXA scan or bloodwork in "prep" and "milestones" ONLY if the person said "Include", and give those items "tag": "dexa" or "tag": "blood".
- Plain JSON inside the block: no comments, no trailing commas, no "..." or placeholders. Write every array out in full.
""".trimIndent()

    fun document(a: Map<String, String>, today: String = PlanLogic.today()): String = buildString {
        val name = a["name"].orEmpty().ifBlank { "me" }
        appendLine("# Custom plan request for $name")
        appendLine()
        appendLine("> **How to use this:** paste this whole document into any AI chat (ChatGPT, Gemini, Claude, DeepSeek…) or attach it as a file. When the AI replies with the plan, copy its whole reply, open the **Bulk Plan** app and go to **Settings › Import a plan from AI**, then paste it.")
        appendLine()
        appendLine("## Instructions for the AI")
        appendLine()
        appendLine("You are an expert strength coach and sports nutritionist. Build a complete, personal training, nutrition and daily-schedule plan for the person described under **About me**, and deliver it as a **plan file** that the Bulk Plan Android app imports automatically. Today is $today.")
        appendLine()
        appendLine("Do this in order:")
        appendLine("1. Read every answer. If something essential is missing or contradicts itself (for example a bulk with a goal weight below their current weight, or an injury that rules out a main lift), ask me up to 5 short questions and wait. Otherwise skip straight to step 2.")
        appendLine("2. Design the plan with the principles below.")
        appendLine("3. Reply with ONE fenced code block that starts with ```$FENCE on its own line and contains only a JSON object in the exact format under **Plan file format**.")
        appendLine("4. After the code block, add a short plain-English summary (under 200 words) and end with: \"Copy this whole reply and paste it into Bulk Plan › Settings › Import a plan from AI.\"")
        appendLine("5. If your reply gets cut off, I’ll say \"continue\". Then resend the whole code block from the start, not just the rest.")
        appendLine()
        appendLine("### Design principles")
        appendLine("- **Calories:** estimate maintenance from their stats, steps and training, then set a surplus or deficit that matches the goal rate. Lean bulk ≈ 0.25–0.5% of bodyweight a week; faster for a detrained or very lean beginner; recomp ≈ maintenance; fat loss ≈ 0.5–1% a week. Put the rate in gainEarly / gainLate.")
        appendLine("- **Protein** 0.8–1.0 g per lb of bodyweight, split over 4+ feedings of 30–50 g. **Fat** at least 0.3 g per lb. Carbs fill the rest, with more around training.")
        appendLine("- **Training:** 10–20 hard sets per muscle per week, each muscle trained about twice a week, mostly 5–30 reps taken 0–3 reps from failure, progressive overload, long ranges of motion with emphasis on the stretched position. Use only the equipment they have and work around injuries. Ramp in for 2–6 weeks if they’re new or coming back from a break. Deload every 5–8 weeks. Swap in exercise variations in the last third of a long plan.")
        appendLine("- **Schedule:** build a realistic weekly routine around their wake time, bedtime, work and training days. Space meals 3–5 hours apart; the day’s meal kcal should add up to the calorie target (±5%) and the protein to the protein target. Respect allergies, dislikes, diet style, budget and cooking skill; if they’re away from home without a fridge, use packable shelf-stable food. No caffeine within 8 hours of bedtime. Lights out at a time that gives the sleep target.")
        appendLine("- **Extras:** add supplements only from the ones they’re open to (creatine 3–5 g daily is the best supported). Add skin care only if they asked for it, matched to their skin type. Add meal prep on the days they picked.")
        appendLine("- Keep it safe: send anything medical to a doctor, and don’t go below 1,500 kcal (men) or 1,200 kcal (women).")
        appendLine()
        appendLine("## Plan file format")
        appendLine()
        appendLine(SCHEMA)
        appendLine()
        appendLine("## About me")
        appendLine()
        for (st in STEPS) {
            val rows = st.questions.mapNotNull { q -> a[q.id]?.trim()?.takeIf { it.isNotBlank() }?.let { q to pretty(q, it) } }
            if (rows.isEmpty()) continue
            appendLine("### ${st.title}")
            rows.forEach { (q, v) -> appendLine("- **${q.label}:** ${v.replace("\n", " ")}") }
            appendLine()
        }
        appendLine("_Made with Bulk Plan on $today._")
    }
}
