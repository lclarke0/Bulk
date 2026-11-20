package com.bulkplan.tracker.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The 6-Month Muscle-Building Plan, as written in the plan doc. */
object DefaultPlan {
    private val ALL = listOf(0, 1, 2, 3, 4, 5, 6)
    private val TRAIN = listOf(1, 2, 4, 5)

    private fun ex(id: String, name: String, sets: Int, reps: String, muscle: String, compound: Boolean, note: String = "") =
        Exercise(id, name, sets, reps, muscle, compound, note)

    fun nextMonday(): String =
        LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toString()

    fun create(startDate: String = nextMonday()) = Plan(
        startDate = startDate,
        weeks = 26,
        profile = Profile(
            startWeight = 175.0, goalWeight = 225.0, goalMuscleLb = 15.0, sixMonthWeight = 190.0,
            lifting = "3 years of lifting, not training right now (detrained)",
            equipment = "Home gym: leg press / hack squat / calf press combo, power rack, barbell, dumbbells, high and low cable pulleys, EZ curl bar. Planning to add an adjustable bench, cable attachments and a leg curl/extension machine.",
            food = "Away from home breakfast to dinner, so lunch and snacks must be portable and need no fridge. Dislikes beans, tomatoes, onions, salsa, mustard; no spicy food; no nuts or hard crunchy foods (almond butter and rice cakes are fine). Favourites: ground beef, shoestring fries.",
        ),
        targets = Targets(),
        weakMuscles = emptyList(),
        blocks = listOf(
            Block(1, 2, "Re-entry", "two", "3", "2 sets per exercise at about 70% of your old working weights."),
            Block(3, 4, "Re-entry", "two", "2", "2 sets per exercise. Add weight each session while your reps hold."),
            Block(5, 6, "Re-entry", "c3", "1–2", "3 sets on compound lifts, 2 on isolation work."),
            Block(7, 11, "Full volume", "full", "0–2", "Full sets. Take the last set of isolation work to failure, then add 3–5 partial reps in the stretched half."),
            Block(12, 12, "Deload", "half", "3–4", "Half the sets at the same weights."),
            Block(13, 17, "Specialize", "spec", "0–2", "+1 set for your weakest muscles (pick them in Settings)."),
            Block(18, 18, "Deload", "half", "3–4", "Half the sets at the same weights."),
            Block(19, 25, "New variations", "spec", "0–2", "Swap 1–2 exercises per day: same movement, different tool."),
            Block(26, 26, "Deload + retest", "half", "3–4", OLD_RETEST_NEW),
        ),
        workouts = linkedMapOf(
            "upper" to Workout("Upper", 1, listOf(
                ex("bench", "Barbell bench press", 3, "5–8", "chest", true, "Pause briefly on the chest"),
                ex("cs-row", "Chest-supported row", 3, "6–10", "back", true, "Full stretch at the bottom"),
                ex("db-ohp", "Seated dumbbell shoulder press", 3, "6–10", "front delts", true),
                ex("pulldown", "Lat pulldown", 3, "8–12", "back", true),
                ex("cable-lat", "Cable lateral raise", 3, "10–15", "side delts", false, "Cable behind your body"),
                ex("inc-curl", "Incline dumbbell curl", 2, "10–15", "biceps", false, "Arms hanging behind your torso"),
                ex("oh-tri", "Overhead cable triceps extension", 2, "10–15", "triceps", false),
            )),
            "lower" to Workout("Lower", 2, listOf(
                ex("squat", "Back squat", 3, "5–8", "quads", true, "Squat to full depth"),
                ex("rdl", "Romanian deadlift", 3, "6–10", "hamstrings", true, "Stop where your hamstrings are at full stretch"),
                ex("leg-press", "Leg press", 3, "10–15", "quads", true, "Feet mid-platform, deep reps"),
                ex("seated-curl", "Seated leg curl", 3, "10–15", "hamstrings", false, "No machine yet: cable curl with an ankle strap"),
                ex("calf-stand", "Standing calf raise", 4, "8–12", "calves", false, "2-second pause at the bottom"),
                ex("crunch", "Cable crunch", 3, "10–15", "abs", false),
            )),
            "push" to Workout("Push", 4, listOf(
                ex("inc-db", "Incline dumbbell press", 3, "6–10", "chest", true, "Bench at 30°"),
                ex("chest-press", "Weighted dips or machine chest press", 3, "8–12", "chest", true),
                ex("fly", "Cable fly", 3, "10–15", "chest", false),
                ex("m-ohp", "Shoulder press", 2, "8–12", "front delts", true),
                ex("db-lat", "Dumbbell lateral raise", 4, "12–20", "side delts", false),
                ex("skull", "EZ-bar skull crusher", 3, "8–12", "triceps", false, "Lower the bar behind your head"),
            )),
            "pull" to Workout("Pull", 5, listOf(
                ex("pullup", "Weighted pull-up (or pulldown)", 3, "6–10", "back", true, "Dead hang at the bottom"),
                ex("tbar", "T-bar or barbell row", 3, "6–10", "back", true),
                ex("sa-row", "Single-arm cable row", 3, "10–12", "back", false, "Let the shoulder blade stretch forward"),
                ex("rev-fly", "Reverse cable fly", 3, "12–20", "rear delts", false),
                ex("bayes", "Bayesian cable curl", 3, "10–15", "biceps", false, "Face away from the cable"),
                ex("hammer", "Hammer curl", 2, "10–12", "biceps", false),
                ex("shrug", "Dumbbell shrug", 2, "10–15", "traps", false),
            )),
            "legs" to Workout("Legs", 6, listOf(
                ex("hack", "Hack squat or front squat", 3, "6–10", "quads", true),
                ex("lying-curl", "Lying leg curl", 3, "8–12", "hamstrings", false, "No machine yet: Nordic curl or cable curl"),
                ex("bss", "Bulgarian split squat", 3, "8–12 per leg", "quads", true),
                ex("leg-ext", "Leg extension", 3, "12–15", "quads", false),
                ex("calf-seat", "Seated calf raise", 4, "10–15", "calves", false),
                ex("knee-raise", "Hanging knee raise", 3, "10–15", "abs", false),
            )),
        ),
        schedule = listOf(
            ScheduleItem("wake", "06:30", "supp", "Wake, weigh in, creatine", "Weigh in after the bathroom, before eating. 5 g creatine with a glass of water.", ALL),
            ScheduleItem("skin-am", "06:40", "skin", "Skin care", "Rinse with water, then a ceramide moisturizer and SPF 30+ sunscreen.", ALL),
            ScheduleItem("breakfast", "07:00", "meal", "Breakfast", "4 eggs, a bagel with cheese, a banana, 12 oz whole milk.", ALL, protein = 35, kcal = 700),
            ScheduleItem("sat-pre", "09:00", "meal", "Pre-workout snack", "Beef jerky, a banana, a soft protein bar.", listOf(6), protein = 15, kcal = 400),
            ScheduleItem("sat-caf", "09:30", "supp", "Caffeine", "200–300 mg (coffee or pre-workout).", listOf(6)),
            ScheduleItem("snack-am", "10:00", "meal", "Snack (packed)", "Shelf-stable protein shake, 2 rice cakes with almond butter.", listOf(0, 1, 2, 3, 4, 5), protein = 25, kcal = 450),
            ScheduleItem("sat-train", "10:00", "train", "Train", "", listOf(6), dur = 90),
            ScheduleItem("groceries", "11:00", "task", "Grocery shop", "Restock for the week.", listOf(0)),
            ScheduleItem("sat-shower", "11:30", "skin", "Shower", "Lukewarm water, 5–10 minutes. Moisturize your body within 3 minutes of drying off.", listOf(6)),
            ScheduleItem("lunch", "12:30", "meal", "Lunch (packed)", "Ground beef or chicken, white rice, bell peppers, avocado.", ALL, protein = 40, kcal = 750),
            ScheduleItem("caf", "14:30", "supp", "Caffeine", "100 mg: a small coffee or a caffeine pill. None after 3:00 pm.", TRAIN),
            ScheduleItem("snack-pm", "15:30", "meal", "Afternoon snack", "Beef jerky, a banana, a soft protein bar. Your pre-workout fuel on training days.", listOf(0, 1, 2, 3, 4, 5), protein = 15, kcal = 400),
            ScheduleItem("sat-snack", "15:30", "meal", "Afternoon snack", "Shelf-stable protein shake, 2 rice cakes with almond butter.", listOf(6), protein = 25, kcal = 450),
            ScheduleItem("review", "17:00", "coach", "Weekly review with your coach", "Open Coach and start the weekly check-in.", listOf(0)),
            ScheduleItem("train", "17:30", "train", "Train", "", TRAIN, dur = 90),
            ScheduleItem("walk", "17:30", "task", "30-minute walk", "Easy pace. It counts toward your steps.", listOf(3)),
            ScheduleItem("prep-sun", "18:00", "task", "Meal prep", "Cook and pack lunches for Mon–Wed.", listOf(0)),
            ScheduleItem("prep-wed", "18:15", "task", "Meal prep", "Cook and pack lunches for Thu–Sat.", listOf(3)),
            ScheduleItem("shower", "19:05", "skin", "Shower + skin care", "Lukewarm water, 5–10 minutes, gentle cleanser on your face. Moisturize face and body within 3 minutes of drying off.", TRAIN),
            ScheduleItem("dinner", "19:30", "meal", "Dinner", "Ground beef or salmon, potatoes or fries, carrots and spinach.", ALL, protein = 40, kcal = 850),
            ScheduleItem("bed-protein", "22:00", "meal", "Before bed", "Greek yogurt with berries, or a casein shake.", ALL, protein = 35, kcal = 350),
            ScheduleItem("skin-pm", "22:15", "skin", "Skin care (night)", "Niacinamide serum, then moisturizer; a thicker cream on dry patches only.", TRAIN),
            ScheduleItem("skin-pm-wash", "22:15", "skin", "Skin care (night)", "Gentle cleanser, then niacinamide serum and moisturizer; a thicker cream on dry patches only.", listOf(0, 3, 6)),
            ScheduleItem("lights", "22:30", "sleep", "Lights out", "8 hours. Wake at 6:30.", ALL),
        ),
        prep = listOf(
            PrepItem("blood", "Bloodwork: fasted, in the morning, before you start creatine, no hard lifting for 48 hours before", "blood"),
            PrepItem("dexa", "DEXA scan for your starting lean mass and body fat", "dexa"),
            PrepItem("bench", "Adjustable bench and cable attachments (D-handles, rope, straight bar, ankle strap)"),
            PrepItem("supps", "Creatine, protein powder and shelf-stable protein shakes"),
            PrepItem("skin", "Gentle cleanser, ceramide moisturizer, SPF 30+ sunscreen, niacinamide serum"),
            PrepItem("garmin", "Garmin watch: create a Personal API key and add it in Settings"),
        ),
        milestones = listOf(
            Milestone(3, "Record your 8-rep max on the main lifts"),
            Milestone(12, "Deload week"),
            Milestone(18, "Deload week"),
            Milestone(19, "Swap 1–2 exercises per day"),
            Milestone(26, "Deload, photos and measurements"),
            Milestone(26, "DEXA scan", "dexa"),
            Milestone(26, "Repeat bloodwork", "blood"),
        ),
    )

    private const val OLD_RETEST = "Half the sets. DEXA scan, bloodwork and photos this week."
    private const val OLD_RETEST_NEW = "Half the sets. Retest week: photos, measurements and any tests you chose."

    /** Brings a plan saved by an older version of the app up to date. */
    fun migrate(p: Plan): Plan = p.copy(
        blocks = p.blocks.map { if (it.note == OLD_RETEST) it.copy(note = OLD_RETEST_NEW) else it },
        schedule = p.schedule.map { if (it.id == "review" && it.title == "Weekly review with Claude") it.copy(title = "Weekly review with your coach") else it },
        prep = p.prep.map { if (it.tag == null && (it.id == "dexa" || it.id == "blood")) it.copy(tag = it.id) else it },
        milestones = p.milestones.flatMap {
            if (it.text == "Deload, DEXA scan, repeat bloodwork, photos") listOf(
                Milestone(it.week, "Deload, photos and measurements"), Milestone(it.week, "DEXA scan", "dexa"), Milestone(it.week, "Repeat bloodwork", "blood"),
            ) else listOf(it)
        },
    )

    val MUSCLES = listOf("chest", "back", "front delts", "side delts", "rear delts", "biceps", "triceps", "quads", "hamstrings", "calves", "traps", "abs")
}
