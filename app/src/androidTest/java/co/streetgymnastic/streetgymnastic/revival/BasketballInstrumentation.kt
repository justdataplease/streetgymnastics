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
import android.widget.EditText
import android.widget.Switch
import co.streetgymnastic.streetgymnastic.revival.ui.AppSettings
import co.streetgymnastic.streetgymnastic.revival.data.BasketballPlanMigration
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.tasks.Tasks
import java.util.UUID
import java.util.concurrent.TimeUnit
import co.streetgymnastic.streetgymnastic.revival.data.AssetCurriculumRepository
import co.streetgymnastic.streetgymnastic.revival.data.BasketballSkills
import co.streetgymnastic.streetgymnastic.revival.data.progress.SharedPreferencesProgressRepository

/** Framework-only instrumented checks, with the emulator's original progress restored afterward. */
class BasketballInstrumentation : Instrumentation() {
    private lateinit var activity: Activity
    private var liveAuth = false
    private var testEmail: String? = null
    private var testPassword: String? = null
    private val preferenceNames = listOf(
        "street_gymnastic_progress", "street_basketball_progress", "street_basketball_skills",
        "street_gymnastic_settings", "street_basketball_plan",
    )

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        liveAuth = arguments?.getString("liveAuth") == "true"
        start()
    }

    override fun onStart() {
        val originals = preferenceNames.associateWith { preferences(it).all.toMap() }
        var failure: Throwable? = null
        try {
            validateCatalogs()
            report("Catalogs: 600 calisthenics workouts, 48 basketball sessions, valid drills and doses.")
            preferenceNames.forEach { preferences(it).edit().clear().commit() }
            verifyPlanMigration()
            report("Upgrade: revised drill ticks clear once, unchanged ticks and history persist.")
            verifyTrainingFlow()
            report("Training: independent active sessions, saved sets, completion, repeat and future locks.")
            verifySkillChecks()
            report("Skills: checkpoint state survives reopening and can be cleared.")
            verifySettings()
            report("Settings: preferences persist, invalid email is rejected, form modes work.")
            if (liveAuth) {
                verifyLiveAuth()
                report("Firebase: sign-up, sign-out, wrong-password handling, sign-in and session restoration passed.")
            }
        } catch (error: Throwable) {
            failure = error
        } finally {
            try {
                testEmail?.let { email ->
                    val auth = FirebaseAuth.getInstance()
                    val user = auth.currentUser ?: Tasks.await(auth.signInWithEmailAndPassword(email, checkNotNull(testPassword)), 30, TimeUnit.SECONDS).user
                    check(user?.email == email)
                    Tasks.await(checkNotNull(user).delete(), 30, TimeUnit.SECONDS)
                    runOnMainSync { auth.signOut() }
                    settle()
                    check(auth.currentUser == null)
                    report("Firebase: disposable test account deleted.")
                }
            } catch (cleanup: Throwable) {
                failure = failure ?: cleanup
            }
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

    private fun verifyPlanMigration() {
        val ball = SharedPreferencesProgressRepository(targetContext, "street_basketball_progress")
        ball.startWorkout("bb:l1:p01", 1000)
        ball.finishWorkout(2000, 4)
        ball.startWorkout("bb:l2:p01", 3000)
        val changed = "bb:l2:p01:main:e01:s01"
        val unchanged = "bb:l2:p01:main:e02:s01"
        ball.setCompleted(changed, true)
        ball.setCompleted(unchanged, true)
        BasketballPlanMigration.apply(targetContext, ball)
        check(ball.activeWorkout()?.completedSetIds == setOf(unchanged))
        check(ball.sessionHistory().size == 1 && ball.completedProgramIds() == setOf("bb:l1:p01"))
        ball.setCompleted(changed, true)
        BasketballPlanMigration.apply(targetContext, ball)
        check(ball.activeWorkout()?.completedSetIds == setOf(changed, unchanged))
        preferences("street_basketball_progress").edit().clear().commit()
    }

    private fun verifySettings() {
        nav(R.string.nav_settings)
        val initial = AppSettings(targetContext).soundCues
        val toggle = checkNotNull(find { it is Switch && it.text == targetContext.getString(R.string.sound_cues) })
        runOnMainSync { toggle.performClick() }
        nav(R.string.nav_today)
        nav(R.string.nav_settings)
        check(AppSettings(targetContext).soundCues != initial)
        check((find { it is Switch && it.text == targetContext.getString(R.string.sound_cues) } as Switch).isChecked != initial)
        if (FirebaseAuth.getInstance().currentUser == null) {
            clickText(R.string.account_sign_in)
            check((find { it is EditText && it.hint == targetContext.getString(R.string.account_email) } as EditText).error != null)
            clickText(R.string.account_new_account)
            check(find { it is EditText && it.hint == targetContext.getString(R.string.account_confirm_password) && it.visibility == View.VISIBLE } != null)
            clickText(R.string.account_have_account)
        }
    }

    private fun fill(label: Int, value: String) {
        val input = find { it is EditText && it.hint == targetContext.getString(label) } as EditText
        runOnMainSync { input.setText(value) }
    }

    private fun awaitText(label: Int) {
        val text = targetContext.getString(label)
        repeat(300) {
            if (find { it is TextView && it.text.toString() == text } != null) return
            SystemClock.sleep(100)
        }
        error("Timed out waiting for: $text")
    }

    private fun verifyLiveAuth() {
        check(FirebaseAuth.getInstance().currentUser == null) { "Live auth test requires a signed-out emulator" }
        val email = "streetgym-test-${UUID.randomUUID()}@example.invalid"
        val password = "Sg!9-${UUID.randomUUID()}"
        clickText(R.string.account_new_account)
        fill(R.string.account_email, email)
        fill(R.string.account_password, password)
        fill(R.string.account_confirm_password, password)
        clickText(R.string.account_create)
        awaitText(R.string.account_signed_in)
        testEmail = email
        testPassword = password
        check(FirebaseAuth.getInstance().currentUser?.email == email)
        val history = SharedPreferencesProgressRepository(targetContext, "street_basketball_progress").sessionHistory()
        clickText(R.string.account_sign_out)
        fill(R.string.account_email, email)
        fill(R.string.account_password, "incorrect-password")
        clickText(R.string.account_sign_in)
        awaitText(R.string.account_invalid_credentials)
        fill(R.string.account_password, password)
        clickText(R.string.account_sign_in)
        awaitText(R.string.account_signed_in)
        runOnMainSync { activity.finish() }
        settle()
        launch()
        nav(R.string.nav_settings)
        awaitText(R.string.account_signed_in)
        check(FirebaseAuth.getInstance().currentUser?.email == email)
        check(SharedPreferencesProgressRepository(targetContext, "street_basketball_progress").sessionHistory() == history)
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
