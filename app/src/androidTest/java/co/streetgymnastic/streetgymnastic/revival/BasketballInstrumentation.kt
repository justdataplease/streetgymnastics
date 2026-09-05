package co.streetgymnastic.streetgymnastic.revival

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CheckBox
import android.widget.AdapterView
import android.widget.TextView
import co.streetgymnastic.streetgymnastic.revival.data.AssetCurriculumRepository
import co.streetgymnastic.streetgymnastic.revival.data.BasketballSkills
import co.streetgymnastic.streetgymnastic.revival.data.progress.SharedPreferencesProgressRepository

/** Framework-only instrumented checks, with the emulator's original progress restored afterward. */
class BasketballInstrumentation : Instrumentation() {
    private lateinit var activity: Activity
    private val preferenceNames = listOf(
        "street_gymnastic_progress", "street_basketball_progress", "street_basketball_skills",
    )

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val originals = preferenceNames.associateWith { preferences(it).all.toMap() }
        var failure: Throwable? = null
        try {
            validateCatalogs()
            report("Catalogs: 600 calisthenics workouts, 48 basketball sessions, valid drills and doses.")
            preferenceNames.forEach { preferences(it).edit().clear().commit() }
            verifyTrainingFlow()
            report("Training: independent active sessions, saved sets, completion, repeat and future locks.")
            verifySkillChecks()
            report("Skills: checkpoint state survives reopening and can be cleared.")
        } catch (error: Throwable) {
            failure = error
        } finally {
            if (::activity.isInitialized) runOnMainSync { activity.finish() }
            originals.forEach { (name, values) ->
                val edit = preferences(name).edit().clear()
                values.forEach { (key, value) ->
                    when (value) {
                        is String -> edit.putString(key, value)
                        is Boolean -> edit.putBoolean(key, value)
                        is Int -> edit.putInt(key, value)
                        is Long -> edit.putLong(key, value)
                        is Float -> edit.putFloat(key, value)
                        is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
                check(edit.commit())
            }
        }
        val result = Bundle()
        if (failure == null) {
            result.putString("stream", "\nPASS: Basketball instrumented checks completed.\n")
            finish(Activity.RESULT_OK, result)
        } else {
            result.putString("stream", "\nFAIL: ${failure.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun preferences(name: String): SharedPreferences =
        targetContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun validateCatalogs() {
        val gym = AssetCurriculumRepository(targetContext).load().getOrThrow()
        val ball = AssetCurriculumRepository(targetContext, "basketball_curriculum.json").load().getOrThrow()
        val gymIds = gym.levels.flatMap { it.programs }.map { it.id }.toSet()
        check(gymIds.size == 600)
        check(ball.levels.size == 6 && BasketballSkills.checkpoints.size == 6)
        val programs = ball.levels.flatMap { it.programs }
        check(programs.size == 48 && programs.map { it.id }.toSet().size == 48)
        val setIds = mutableSetOf<String>()
        val categories = mutableSetOf<String>()
        ball.levels.forEachIndexed { index, level ->
            check(level.number == index + 1 && level.programs.size == 8)
            check(level.programs.map { it.number } == (1..8).toList())
            check((level.programs[3].targetRpe ?: 10) <= 3)
        }
        programs.forEach { program ->
            check(program.id.startsWith("bb:") && program.id !in gymIds)
            check(program.readiness.resolve().isNotBlank() && program.safety.resolve().isNotBlank())
            check(program.targetRpe in 3..6 && program.hasDetails)
            check(program.warmup.isNotEmpty() && program.cooldown.isNotEmpty())
            (program.warmup + program.exercises + program.cooldown).forEach { exercise ->
                check(exercise.movementId?.startsWith("bb_") == true)
                check(exercise.description.resolve().isNotBlank() && exercise.equipment.isNotEmpty())
                categories.add(checkNotNull(exercise.category))
                check(exercise.sets.size in 1..3)
                exercise.sets.forEach { set ->
                    check(setIds.add(set.id)) { "Duplicate set ID: ${set.id}" }
                    check(!set.repetitionsMax)
                    check((set.repetitions ?: 0) > 0 || (set.durationSeconds ?: 0) > 0 ||
                        set.description.resolve().any(Char::isDigit)) { "Missing dose: ${set.id}" }
                }
            }
        }
        check(categories.containsAll(listOf(
            "dribbling", "shooting", "finishing", "footwork", "passing", "defense", "decision", "recovery",
        )))
    }

    private fun launch() {
        activity = startActivitySync(
            Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        repeat(100) {
            if (find { it.contentDescription == targetContext.getString(R.string.nav_basketball) } != null) return
            SystemClock.sleep(100)
        }
        error("App did not load")
    }

    private fun verifyTrainingFlow() {
        val gym = SharedPreferencesProgressRepository(targetContext)
        val ball = SharedPreferencesProgressRepository(targetContext, "street_basketball_progress")
        launch()
        nav(R.string.nav_basketball)
        textExists("Meet the ball")
        clickText(R.string.basketball_start)
        check(ball.activeWorkout()?.programId == "bb:l1:p01")
        val firstSet = checkNotNull(find { it is CheckBox })
        runOnMainSync { firstSet.performClick() }
        settle()
        check(ball.activeWorkout()?.completedSetIds?.size == 1)

        nav(R.string.nav_today)
        clickText(R.string.start_today)
        val gymActive = checkNotNull(gym.activeWorkout())
        check(!gymActive.programId.startsWith("bb:"))
        check(ball.activeWorkout()?.completedSetIds?.size == 1)
        nav(R.string.nav_basketball)
        clickText(R.string.resume_workout)
        check((find { it is CheckBox } as CheckBox).isChecked)
        check(gym.activeWorkout() == gymActive)

        // Relaunch from persisted data, then exercise platform Activity state restoration.
        runOnMainSync {
            activity.finish()
        }
        settle()
        launch()
        nav(R.string.nav_basketball)
        clickText(R.string.resume_workout)
        check((find { it is CheckBox } as CheckBox).isChecked)

        val monitor = addMonitor(MainActivity::class.java.name, null, false)
        runOnMainSync { activity.recreate() }
        activity = checkNotNull(waitForMonitorWithTimeout(monitor, 10000)) { "Activity did not recreate" }
        removeMonitor(monitor)
        repeat(100) {
            if (find { it is CheckBox } != null) return@repeat
            SystemClock.sleep(100)
        }
        check((find { it is CheckBox } as? CheckBox)?.isChecked == true) { "Basketball active state was not restored" }
        check(gym.activeWorkout() == gymActive)

        clickText(R.string.finish_workout)
        clickDialog(R.string.finish)
        clickDialog(R.string.finish)
        check(ball.activeWorkout() == null && ball.sessionHistory().size == 1)
        check(ball.sessionHistory().first().effortRating == 4)
        textExists(targetContext.getString(R.string.basketball_progress))
        nav(R.string.nav_basketball)
        textExists("Eyes up, feet set")
        clickLabel("First Touch")
        clickLabel("Meet the ball")
        clickText(R.string.basketball_repeat)
        check(ball.activeWorkout()?.programId == "bb:l1:p01")
        check(ball.activeWorkout()?.completedSetIds?.isEmpty() == true)
        ball.finishWorkout(effortRating = 3)
        check(ball.completedProgramIds() == setOf("bb:l1:p01"))
        check(ball.sessionHistory().size == 2)
        check(gym.activeWorkout() == gymActive && gym.completedProgramIds().isEmpty())
        nav(R.string.nav_basketball)
        clickLabel("First Touch")
        clickLabel("The first finish")
        val locked = find { it is TextView && it.text.toString() == targetContext.getString(R.string.assigned_later) }
        check(locked != null && !locked.isEnabled) { "Future basketball session can start early" }
        check(find { it is TextView && it.text.toString() == targetContext.getString(R.string.start_workout) } == null)
    }

    private fun verifySkillChecks() {
        nav(R.string.nav_basketball)
        clickText(R.string.basketball_progress)
        val label = BasketballSkills.checkpoints.first().first()
        clickLabel(label)
        check("l1:c1" in preferences("street_basketball_skills").getStringSet("passed", emptySet()).orEmpty())
        nav(R.string.nav_today)
        nav(R.string.nav_basketball)
        clickText(R.string.basketball_progress)
        check((find { it is CheckBox && it.text.toString() == label } as CheckBox).isChecked)
        clickLabel(label)
        check(preferences("street_basketball_skills").getStringSet("passed", emptySet()).orEmpty().isEmpty())
    }

    private fun nav(resource: Int) {
        val view = checkNotNull(find { it.contentDescription == targetContext.getString(resource) })
        runOnMainSync { view.performClick() }
        settle()
    }

    private fun clickText(resource: Int) = clickLabel(targetContext.getString(resource))

    private fun clickLabel(label: String) {
        var view = checkNotNull(find { it is TextView && it.text.toString() == label }) { "Missing: $label" }
        while (!view.isClickable && view.parent is View) {
            val parent = view.parent
            if (parent is AdapterView<*>) {
                val row = view
                runOnMainSync {
                    val position = parent.getPositionForView(row)
                    parent.performItemClick(row, position, parent.getItemIdAtPosition(position))
                }
                settle()
                return
            }
            view = parent as View
        }
        check(view.isClickable) { "Not clickable: $label" }
        val target = view
        runOnMainSync { target.performClick() }
        settle()
    }

    private fun textExists(label: String) {
        check(find { it is TextView && it.text.toString() == label } != null) { "Missing: $label" }
    }

    private fun clickDialog(resource: Int) {
        val label = targetContext.getString(resource)
        val automation = uiAutomation
        repeat(30) {
            val button = automation.rootInActiveWindow?.findAccessibilityNodeInfosByText(label)
                ?.firstOrNull { it.text?.toString()?.equals(label, ignoreCase = true) == true && it.isClickable }
            if (button != null) {
                check(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                settle()
                return
            }
            SystemClock.sleep(100)
        }
        val visible = uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(label)
            ?.joinToString { "${it.text} (clickable=${it.isClickable})" }
        error("Missing dialog action: $label; matches: $visible")
    }

    private fun find(predicate: (View) -> Boolean): View? {
        var result: View? = null
        runOnMainSync {
            fun visit(view: View) {
                if (result != null) return
                if (predicate(view)) result = view
                else if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
            visit(activity.window.decorView)
        }
        return result
    }

    private fun report(message: String) {
        sendStatus(0, Bundle().apply { putString("stream", "\n$message\n") })
    }

    private fun settle() {
        // Exercise illustrations continuously invalidate; they need not become globally idle.
        SystemClock.sleep(120)
        runOnMainSync { }
    }
}
