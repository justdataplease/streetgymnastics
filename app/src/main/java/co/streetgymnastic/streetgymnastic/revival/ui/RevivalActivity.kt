package co.streetgymnastic.streetgymnastic.revival.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import co.streetgymnastic.streetgymnastic.revival.R
import co.streetgymnastic.streetgymnastic.revival.data.AssetCurriculumRepository
import co.streetgymnastic.streetgymnastic.revival.data.BasketballSkills
import co.streetgymnastic.streetgymnastic.revival.data.model.CurriculumCatalog
import co.streetgymnastic.streetgymnastic.revival.data.model.TrainingExercise
import co.streetgymnastic.streetgymnastic.revival.data.model.TrainingLevel
import co.streetgymnastic.streetgymnastic.revival.data.model.TrainingProgram
import co.streetgymnastic.streetgymnastic.revival.data.model.TrainingSet
import co.streetgymnastic.streetgymnastic.revival.data.progress.ActiveWorkout
import co.streetgymnastic.streetgymnastic.revival.data.progress.SharedPreferencesProgressRepository
import java.text.DateFormat
import java.text.NumberFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Framework-only application shell. It intentionally avoids network and private API dependencies:
 * the catalog and exercise demonstrations are bundled with the app.
 */
open class RevivalActivity : Activity() {
    private sealed interface Screen {
        data object Dashboard : Screen
        data object Levels : Screen
        data object Basketball : Screen
        data object BasketballProgress : Screen
        data class Workouts(val levelNumber: Int, val basketball: Boolean = false) : Screen
        data class Overview(val programId: String) : Screen
        data class Active(val programId: String) : Screen
        data object Progress : Screen
        data object Settings : Screen
    }

    private data class ExerciseSection(
        val key: String,
        val titleRes: Int,
        val exercises: List<TrainingExercise>,
    )

    private lateinit var settings: AppSettings
    private lateinit var gymnasticsProgress: SharedPreferencesProgressRepository
    private lateinit var basketballProgress: SharedPreferencesProgressRepository
    private val progressRepository get() = repositoryFor(currentScreen)
    private lateinit var appRoot: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var toolbarBack: TextView
    private lateinit var toolbarTitle: TextView
    private lateinit var bodyContainer: FrameLayout
    private lateinit var bottomNavigation: LinearLayout

    private val worker = Executors.newSingleThreadExecutor()
    private val backStack = ArrayDeque<Screen>()
    private var gymnasticsCatalog: CurriculumCatalog? = null
    private var basketballCatalog: CurriculumCatalog? = null
    private val catalog get() = catalogFor(currentScreen)
    private var currentScreen: Screen? = null
    private var restTimer: CountDownTimer? = null
    private var restEndsAtEpochMillis: Long? = null
    private var restProgramId: String? = null
    private var restStatusView: TextView? = null
    private var toneGenerator: ToneGenerator? = null
    private var restoredScreen: Screen? = null

    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        gymnasticsProgress = SharedPreferencesProgressRepository(this)
        basketballProgress = SharedPreferencesProgressRepository(this, "street_basketball_progress")
        restoredScreen = savedInstanceState?.let(::screenFromBundle)
        restEndsAtEpochMillis = savedInstanceState?.getLong(STATE_REST_END)
            ?.takeIf { it > System.currentTimeMillis() }
        restProgramId = savedInstanceState?.getString(STATE_REST_PROGRAM)

        configureWindow()
        buildShell()
        renderLoading()
        loadCatalog()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentScreen?.let { screen ->
            outState.putString(STATE_SCREEN, screen.javaClass.simpleName)
            when (screen) {
                is Screen.Workouts -> {
                    outState.putInt(STATE_LEVEL, screen.levelNumber)
                    outState.putBoolean(STATE_BASKETBALL, screen.basketball)
                }
                is Screen.Overview -> outState.putString(STATE_PROGRAM, screen.programId)
                is Screen.Active -> outState.putString(STATE_PROGRAM, screen.programId)
                else -> Unit
            }
        }
        restEndsAtEpochMillis?.let { outState.putLong(STATE_REST_END, it) }
        restProgramId?.let { outState.putString(STATE_REST_PROGRAM, it) }
    }

    override fun onDestroy() {
        restTimer?.cancel()
        toneGenerator?.release()
        worker.shutdownNow()
        super.onDestroy()
    }

    @Deprecated("Uses platform back navigation for minSdk 23 compatibility")
    override fun onBackPressed() {
        when (val screen = currentScreen) {
            Screen.Dashboard, null -> super.onBackPressed()
            Screen.Levels, Screen.Progress, Screen.Settings -> openRoot(Screen.Dashboard)
            Screen.Basketball -> openRoot(Screen.Dashboard)
            Screen.BasketballProgress -> openRoot(Screen.Basketball)
            else -> {
                val prior = backStack.pollLast()
                if (prior != null) showScreen(prior, pushCurrent = false) else openRoot(Screen.Dashboard)
            }
        }
    }

    private fun configureWindow() {
        window.statusBarColor = AppColors.PRIMARY_DARK
        window.navigationBarColor = AppColors.PRIMARY_DARK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun buildShell() {
        appRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.BACKGROUND)
        }

        toolbarBack = TextView(this).apply {
            text = "‹"
            gravity = Gravity.CENTER
            textSize = 38f
            setTextColor(Color.WHITE)
            contentDescription = getString(R.string.back)
            isClickable = true
            isFocusable = true
            setOnClickListener { onBackPressed() }
        }
        toolbarTitle = TextView(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(AppColors.PRIMARY)
            elevation = dp(4).toFloat()
            addView(toolbarBack, LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(toolbarTitle, LinearLayout.LayoutParams(0, dp(56), 1f))
            addView(View(context), LinearLayout.LayoutParams(dp(16), dp(56)))
        }

        bodyContainer = FrameLayout(this).apply {
            setBackgroundColor(AppColors.BACKGROUND)
        }
        bottomNavigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat()
        }

        appRoot.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        appRoot.addView(bodyContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        appRoot.addView(
            bottomNavigation,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)),
        )
        setContentView(appRoot)
        applySystemBarInsets()
    }

    private fun applySystemBarInsets() {
        appRoot.setOnApplyWindowInsetsListener { _, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                top = bars.top
                bottom = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            toolbar.setPadding(0, top, 0, 0)
            toolbar.layoutParams = (toolbar.layoutParams as LinearLayout.LayoutParams).apply {
                height = dp(56) + top
            }
            bottomNavigation.setPadding(0, 0, 0, bottom)
            bottomNavigation.layoutParams =
                (bottomNavigation.layoutParams as LinearLayout.LayoutParams).apply {
                    height = dp(58) + bottom
                }
            insets
        }
        appRoot.requestApplyInsets()
    }

    private fun loadCatalog() {
        toolbar.visibility = View.GONE
        bottomNavigation.visibility = View.GONE
        renderLoading()
        worker.execute {
            val result = runCatching {
                AssetCurriculumRepository(this).load().getOrThrow() to
                    AssetCurriculumRepository(this, "basketball_curriculum.json").load().getOrThrow()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.fold(
                    onSuccess = {
                        gymnasticsCatalog = it.first
                        basketballCatalog = it.second
                        val destination = restoredScreen?.takeIf(::isValidScreen) ?: Screen.Dashboard
                        restoredScreen = null
                        showScreen(destination, pushCurrent = false)
                    },
                    onFailure = { renderLoadError() },
                )
            }
        }
    }

    private fun renderLoading() {
        bodyContainer.removeAllViews()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.logo)
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    contentDescription = getString(R.string.logo_content_description)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120)),
            )
            addView(spacer(24))
            addView(ProgressBar(context), LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            })
            addView(spacer(16))
            addView(bodyText(getString(R.string.loading_catalog)).apply { gravity = Gravity.CENTER })
        }
        bodyContainer.addView(
            layout,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun renderLoadError() {
        toolbar.visibility = View.VISIBLE
        bottomNavigation.visibility = View.GONE
        toolbarBack.visibility = View.INVISIBLE
        toolbarTitle.text = getString(R.string.app_name)
        bodyContainer.removeAllViews()
        val page = verticalPage().apply {
            gravity = Gravity.CENTER
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.logo)
                    adjustViewBounds = true
                    contentDescription = getString(R.string.logo_content_description)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(100)),
            )
            addView(spacer(20))
            addView(titleText(getString(R.string.load_error_title)).apply { gravity = Gravity.CENTER })
            addView(spacer(8))
            addView(bodyText(getString(R.string.load_error_message)).apply { gravity = Gravity.CENTER })
            addView(spacer(20))
            addView(primaryButton(getString(R.string.retry), AppColors.PRIMARY) { loadCatalog() })
        }
        setBody(page)
    }

    private fun showScreen(screen: Screen, pushCurrent: Boolean = true) {
        if (!isValidScreen(screen)) {
            openRoot(Screen.Dashboard)
            return
        }
        if (pushCurrent) currentScreen?.let(backStack::addLast)
        currentScreen = screen
        toolbar.visibility = View.VISIBLE
        bottomNavigation.visibility = View.VISIBLE
        toolbarBack.visibility = if (isNested(screen)) View.VISIBLE else View.INVISIBLE
        toolbarTitle.text = screenTitle(screen)
        rebuildBottomNavigation(screen)

        val active = screen is Screen.Active
        if (active && settings.keepScreenAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (!active) {
            restTimer?.cancel()
            restStatusView = null
        }

        when (screen) {
            Screen.Dashboard -> renderDashboard()
            Screen.Levels -> renderLevels()
            Screen.Basketball -> renderBasketball()
            Screen.BasketballProgress -> renderProgress()
            is Screen.Workouts -> renderWorkouts(screen.levelNumber)
            is Screen.Overview -> renderOverview(screen.programId)
            is Screen.Active -> renderActive(screen.programId)
            Screen.Progress -> renderProgress()
            Screen.Settings -> renderSettings()
        }
    }

    private fun openRoot(screen: Screen) {
        backStack.clear()
        showScreen(screen, pushCurrent = false)
    }

    private fun navigate(screen: Screen) = showScreen(screen, pushCurrent = true)

    private fun rebuildBottomNavigation(screen: Screen) {
        bottomNavigation.removeAllViews()
        val selected = rootFor(screen)
        listOf(
            Triple(Screen.Dashboard, R.string.nav_today, "●"),
            Triple(Screen.Levels, R.string.nav_levels, "▦"),
            Triple(Screen.Basketball, R.string.nav_basketball, ""),
            Triple(Screen.Progress, R.string.nav_progress, "↗"),
            Triple(Screen.Settings, R.string.nav_settings, "⚙"),
        ).forEach { (destination, labelRes, glyph) ->
            val isSelected = selected == destination
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = getString(labelRes)
                setOnClickListener { openRoot(destination) }
                if (destination == Screen.Basketball) addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_basketball)
                    imageTintList = ColorStateList.valueOf(
                        if (isSelected) AppColors.PRIMARY_DARK else AppColors.TEXT_SECONDARY,
                    )
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(22), dp(22)))
                else addView(TextView(context).apply {
                    text = glyph
                    gravity = Gravity.CENTER
                    textSize = 17f
                    setTextColor(if (isSelected) AppColors.PRIMARY_DARK else AppColors.TEXT_SECONDARY)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
                addView(TextView(context).apply {
                    text = getString(labelRes)
                    gravity = Gravity.CENTER
                    textSize = if (destination == Screen.Basketball) 11f else 12f
                    maxLines = 1
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setAutoSizeTextTypeUniformWithConfiguration(9, 12, 1,
                            android.util.TypedValue.COMPLEX_UNIT_SP)
                    }
                    typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    setTextColor(if (isSelected) AppColors.PRIMARY_DARK else AppColors.TEXT_SECONDARY)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
            }
            bottomNavigation.addView(item, LinearLayout.LayoutParams(0, dp(58), 1f))
        }
    }

    private fun renderDashboard() {
        val catalog = catalog ?: return
        val locale = uiLocale()
        val allPrograms = allPrograms(catalog)
        val completedIds = progressRepository.completedProgramIds()
        val active = progressRepository.activeWorkout()
        val nextProgram = assignedProgram()

        val page = verticalPage()
        page.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.logo)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = getString(R.string.logo_content_description)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)),
        )
        page.addView(sectionLabel(getString(R.string.today_section)))

        val todayCard = card(clickable = nextProgram != null)
        if (nextProgram == null) {
            todayCard.addView(titleText(getString(R.string.all_complete_title), 20f))
            todayCard.addWithMargins(bodyText(getString(R.string.all_complete_body)), topDp = 8)
        } else {
            val isResume = active?.programId == nextProgram.id
            todayCard.addView(titleText(displayName(nextProgram, locale), 22f))
            val level = catalog.levels.firstOrNull { it.number == nextProgram.levelNumber }
            val meta = buildList {
                add(level?.let { displayName(it, locale) } ?: getString(R.string.level_number, nextProgram.levelNumber))
                programMeta(nextProgram).takeIf(String::isNotBlank)?.let(::add)
            }.joinToString(" • ")
            todayCard.addWithMargins(bodyText(meta, 14f), topDp = 4)
            todayCard.addWithMargins(
                bodyText(
                    nextProgram.description.resolve(locale)
                        .ifBlank { getString(R.string.empty_description) },
                ),
                topDp = 10,
            )
            val action = primaryButton(
                getString(if (isResume) R.string.resume_workout else R.string.start_today),
            ) { requestStart(nextProgram) }
            action.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play_arrow_white_24dp, 0, 0, 0)
            action.compoundDrawablePadding = dp(8)
            todayCard.addWithMargins(action, topDp = 16)
            todayCard.setOnClickListener { navigate(Screen.Overview(nextProgram.id)) }
        }
        page.addWithMargins(todayCard, bottomDp = 18)

        page.addView(sectionLabel(getString(R.string.overall_progress)))
        val progressCard = card()
        val total = allPrograms.size
        val complete = allPrograms.count { it.id in completedIds }
        val percent = percentage(complete, total)
        progressCard.addView(titleText("$percent%", 30f))
        progressCard.addWithMargins(
            bodyText(getString(R.string.workouts_completed_format, complete, total)),
            topDp = 2,
        )
        progressCard.addWithMargins(horizontalProgress(percent), topDp = 12, height = dp(10))
        page.addWithMargins(progressCard, bottomDp = 18)

        page.addView(sectionLabel(getString(R.string.latest_completion)))
        val latestCard = card()
        val latest = progressRepository.sessionHistory().firstOrNull()
        if (latest == null) {
            latestCard.addView(bodyText(getString(R.string.no_completion)))
        } else {
            val program = programById(latest.programId)
            latestCard.addView(
                titleText(
                    program?.let { displayName(it, locale) }
                        ?: getString(R.string.workout_overview),
                    18f,
                ),
            )
            latestCard.addWithMargins(
                bodyText(
                    getString(
                        R.string.completed_on,
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
                            .format(Date(latest.completedAtEpochMillis)),
                        latest.durationSeconds / 60L,
                    ),
                    13f,
                ),
                topDp = 4,
            )
        }
        page.addWithMargins(latestCard, bottomDp = 18)

        page.addView(sectionLabel(getString(R.string.levels_title)))
        catalog.levels.sortedBy { it.number }.forEach { level ->
            page.addWithMargins(levelCard(level, locale, completedIds), bottomDp = 10)
        }
        val browseButton = primaryButton(getString(R.string.browse_levels), AppColors.PRIMARY) {
                openRoot(Screen.Levels)
            }
        browseButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_list_white_24dp, 0, 0, 0)
        browseButton.compoundDrawablePadding = dp(8)
        page.addWithMargins(browseButton, topDp = 4)

        setBody(scroll(page))
    }

    private fun renderBasketball() {
        val plan = basketballCatalog ?: return
        val completed = basketballProgress.completedProgramIds()
        val active = basketballProgress.activeWorkout()
        val next = active?.programId?.let(::programById)?.takeIf { canStart(it.id) }
            ?: assignedProgram()
        val page = verticalPage()
        page.addView(titleText(getString(R.string.basketball_title), 26f))
        page.addWithMargins(bodyText(getString(R.string.basketball_subtitle)), topDp = 4)
        page.addWithMargins(BasketballCourtView(this, "overview"),
            height = dp(128), topDp = 12, bottomDp = 8)
        page.addView(sectionLabel(getString(R.string.basketball_next)))
        if (next != null) {
            page.addView(titleText(displayName(next, uiLocale()), 21f))
            page.addWithMargins(bodyText(programMeta(next), 13f), topDp = 4)
            page.addWithMargins(bodyText(next.description.resolve()), topDp = 8)
            val start = primaryButton(getString(
                if (active?.programId == next.id) R.string.resume_workout else R.string.basketball_start,
            )) { requestStart(next) }
            start.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play_arrow_white_24dp, 0, 0, 0)
            start.compoundDrawablePadding = dp(8)
            page.addWithMargins(start, topDp = 12)
            page.addView(secondaryButton(getString(R.string.basketball_session_details)) {
                navigate(Screen.Overview(next.id))
            })
        } else {
            page.addView(titleText(getString(R.string.basketball_complete), 20f))
            page.addWithMargins(bodyText(getString(R.string.basketball_keep_practicing)), topDp = 6)
        }
        val all = allPrograms(plan)
        page.addWithMargins(bodyText(getString(
            R.string.workouts_completed_format, all.count { it.id in completed }, all.size,
        )), topDp = 14)
        page.addWithMargins(horizontalProgress(percentage(all.count { it.id in completed }, all.size)),
            height = dp(8), topDp = 7)
        page.addWithMargins(secondaryButton(getString(R.string.basketball_progress)) {
            navigate(Screen.BasketballProgress)
        }, topDp = 6)
        page.addView(sectionLabel(getString(R.string.basketball_levels)))
        plan.levels.forEach { level ->
            page.addWithMargins(levelCard(level, uiLocale(), completed), bottomDp = 10)
        }
        page.addView(sectionLabel(getString(R.string.basketball_rhythm_title)))
        page.addView(bodyText(getString(R.string.basketball_rhythm)))
        page.addWithMargins(secondaryButton(getString(R.string.basketball_court_rules)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.basketball_court_rules)
                .setMessage(R.string.basketball_rules)
                .setPositiveButton(android.R.string.ok, null).show()
        }, topDp = 8)
        setBody(scroll(page))
    }

    private fun renderLevels() {
        val catalog = catalog ?: return
        val locale = uiLocale()
        val completed = progressRepository.completedProgramIds()
        val page = verticalPage()
        page.addView(titleText(getString(R.string.levels_title), 26f))
        page.addWithMargins(bodyText(getString(R.string.empty_description)), topDp = 5, bottomDp = 16)
        catalog.levels.sortedBy { it.number }.forEach { level ->
            page.addWithMargins(levelCard(level, locale, completed), bottomDp = 12)
        }
        setBody(scroll(page))
    }

    private fun levelCard(
        level: TrainingLevel,
        locale: Locale,
        completedIds: Set<String>,
    ): LinearLayout = card(clickable = true).apply {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            numberCircle(level.number.toString(), getString(R.string.level_number, level.number)),
            LinearLayout.LayoutParams(dp(44), dp(44)),
        )
        val text = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            addView(titleText(displayName(level, locale), 19f))
            val description = level.description.resolve(locale)
            if (description.isNotBlank()) addView(bodyText(description, 13f).apply { maxLines = 2 })
        }
        header.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(header)
        val complete = level.programs.count { it.id in completedIds }
        val percent = percentage(complete, level.programs.size)
        addWithMargins(
            bodyText(getString(R.string.level_progress_format, complete, level.programs.size), 13f),
            topDp = 12,
        )
        addWithMargins(horizontalProgress(percent), topDp = 7, height = dp(8))
        contentDescription = "${displayName(level, locale)}, ${getString(R.string.level_progress_format, complete, level.programs.size)}"
        setOnClickListener { navigate(Screen.Workouts(level.number, isBasketball(currentScreen))) }
    }

    private fun renderWorkouts(levelNumber: Int) {
        val level = catalog?.levels?.firstOrNull { it.number == levelNumber } ?: return
        val locale = uiLocale()
        val completed = progressRepository.completedProgramIds()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.BACKGROUND)
        }
        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(10))
            addView(titleText(displayName(level, locale), 23f))
            level.description.resolve(locale).takeIf(String::isNotBlank)?.let {
                addWithMargins(bodyText(it, 14f), topDp = 4)
            }
            val count = level.programs.count { it.id in completed }
            addWithMargins(
                bodyText(getString(R.string.level_progress_format, count, level.programs.size), 13f),
                topDp = 8,
            )
            addWithMargins(
                horizontalProgress(percentage(count, level.programs.size)),
                topDp = 6,
                height = dp(8),
            )
        }
        container.addView(summary)
        val programs = level.programs.sortedBy { it.number }
        val list = ListView(this).apply {
            adapter = ProgramListAdapter(context, programs, completed, assignedProgram()?.id, locale)
            divider = ColorDrawable(Color.TRANSPARENT)
            dividerHeight = 0
            clipToPadding = false
            setPadding(0, dp(4), 0, dp(16))
            setBackgroundColor(AppColors.BACKGROUND)
            setOnItemClickListener { _, _, position, _ ->
                navigate(Screen.Overview(programs[position].id))
            }
        }
        container.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setBody(container)
    }

    private fun renderOverview(programId: String) {
        val program = programById(programId) ?: return
        val locale = uiLocale()
        val page = verticalPage()

        val hero = card()
        hero.addView(titleText(displayName(program, locale), 25f))
        val meta = buildList {
            add(getString(R.string.level_number, program.levelNumber))
            programMeta(program).takeIf(String::isNotBlank)?.let(::add)
        }.joinToString(" • ")
        hero.addWithMargins(bodyText(meta, 14f), topDp = 4)
        hero.addWithMargins(
            bodyText(
                program.description.resolve(locale).ifBlank { getString(R.string.empty_description) },
            ),
            topDp = 12,
        )
        page.addWithMargins(hero, bottomDp = 14)

        page.addWithMargins(workoutSafetyCard(program), bottomDp = 16)

        val sections = sections(program)
        if (sections.all { it.exercises.isEmpty() }) {
            page.addWithMargins(card().apply { addView(bodyText(getString(R.string.no_exercises))) }, bottomDp = 14)
        } else {
            sections.filter { it.exercises.isNotEmpty() }.forEach { section ->
                addExerciseSection(page, section, locale)
            }
            val assigned = assignedProgram()
            if (assigned?.id == program.id || canReplay(program.id)) {
                val isResume = progressRepository.activeWorkout()?.programId == program.id
                val start = primaryButton(
                    getString(when {
                        isResume -> R.string.resume_workout
                        canReplay(program.id) -> R.string.basketball_repeat
                        else -> R.string.start_workout
                    }),
                ) { requestStart(program) }
                start.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play_arrow_white_24dp, 0, 0, 0)
                start.compoundDrawablePadding = dp(8)
                page.addWithMargins(start, topDp = 8)
            } else {
                val completed = progressRepository.isProgramCompleted(program.id)
                page.addWithMargins(
                    bodyText(
                        getString(
                            if (completed) R.string.sequence_completed_note
                            else R.string.sequence_locked_note,
                        ),
                        14f,
                    ),
                    topDp = 8,
                )
                val locked = primaryButton(
                    getString(
                        if (completed) R.string.workout_completed
                        else R.string.assigned_later,
                    ),
                    AppColors.TEXT_SECONDARY,
                ) {}
                locked.isEnabled = false
                page.addWithMargins(locked, topDp = 8)
            }
        }
        setBody(scroll(page))
    }

    private fun addExerciseSection(
        page: LinearLayout,
        section: ExerciseSection,
        locale: Locale,
    ) {
        page.addView(sectionLabel(getString(section.titleRes)))
        section.exercises.forEachIndexed { index, exercise ->
            val exerciseNumber = exercise.number.takeIf { it > 0 } ?: index + 1
            val exerciseName = displayName(exercise, locale)
            val exerciseCard = card()
            val heading = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    numberCircle(
                        exerciseNumber.toString(),
                        getString(R.string.exercise_number, exerciseNumber),
                    ),
                    LinearLayout.LayoutParams(dp(40), dp(40)),
                )
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(12), 0, 0, 0)
                        addView(titleText(exerciseName, 18f))
                        addView(bodyText(resources.getQuantityString(R.plurals.sets_count, exercise.sets.size, exercise.sets.size), 13f))
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
            }
            exerciseCard.addView(heading)
            exerciseCard.addWithMargins(
                exerciseAnimation(exercise, exerciseName, locale),
                height = ViewGroup.LayoutParams.WRAP_CONTENT,
                topDp = 10,
            )
            exercise.description.resolve(locale).takeIf(String::isNotBlank)?.let {
                exerciseCard.addWithMargins(bodyText(it, 14f), topDp = 10)
            }
            exercise.sets.forEach { set ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                    setPadding(0, dp(8), 0, 0)
                    addView(
                        TextView(context).apply {
                            text = NumberFormat.getIntegerInstance(locale).format(set.number)
                            gravity = Gravity.CENTER
                            textSize = 12f
                            setTextColor(Color.WHITE)
                            background = roundedDrawable(AppColors.NUMBER_DARK, dp(13).toFloat())
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        },
                        LinearLayout.LayoutParams(dp(26), dp(26)),
                    )
                    addView(
                        bodyText(setPrescription(set, locale), 13f).apply {
                            setPadding(dp(10), dp(2), 0, 0)
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                }
                exerciseCard.addView(row)
            }
            page.addWithMargins(exerciseCard, bottomDp = 10)
        }
    }

    private fun requestStart(program: TrainingProgram) {
        if (!canStart(program.id)) {
            Toast.makeText(this, R.string.sequence_locked_note, Toast.LENGTH_LONG).show()
            return
        }
        val current = progressRepository.activeWorkout()
        if (current != null && current.programId != program.id) {
            AlertDialog.Builder(this)
                .setTitle(R.string.replace_workout_title)
                .setMessage(R.string.replace_workout_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.replace) { _, _ ->
                    clearRest()
                    progressRepository.abandonWorkout()
                    progressRepository.startWorkout(program.id)
                    navigate(Screen.Active(program.id))
                }
                .show()
            return
        }
        if (current == null) progressRepository.startWorkout(program.id)
        navigate(Screen.Active(program.id))
    }

    private fun renderActive(programId: String) {
        val program = programById(programId) ?: return
        val active = progressRepository.activeWorkout()
        if (active?.programId != program.id || !canStart(program.id)) {
            openRoot(Screen.Dashboard)
            return
        }
        val locale = uiLocale()
        val sections = sections(program).filter { it.exercises.isNotEmpty() }
        if (restProgramId != program.id) clearRest()
        val allSetKeys = buildList {
            sections.forEach { section ->
                section.exercises.forEachIndexed { exerciseIndex, exercise ->
                    exercise.sets.forEachIndexed { setIndex, set ->
                        add(scopedSetId(program, section.key, exercise, exerciseIndex, set, setIndex))
                    }
                }
            }
        }
        val doneCount = allSetKeys.count { it in active.completedSetIds }

        val page = verticalPage()
        val status = card().apply {
            background = roundedDrawable(AppColors.INFO_SURFACE, dp(10).toFloat())
            addView(titleText(displayName(program, locale), 22f))
            val minutes = ((System.currentTimeMillis() - active.startedAtEpochMillis) / 60_000L)
                .coerceAtLeast(0L)
            addWithMargins(bodyText(getString(R.string.started_minutes_ago, minutes), 13f), topDp = 3)
            addWithMargins(
                bodyText(getString(R.string.active_sets_progress, doneCount, allSetKeys.size), 14f).apply {
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(AppColors.NUMBER_DARK)
                },
                topDp = 10,
            )
            addWithMargins(
                horizontalProgress(percentage(doneCount, allSetKeys.size)),
                topDp = 7,
                height = dp(9),
            )
            restStatusView = bodyText(getString(R.string.rest_ready), 14f).apply {
                gravity = Gravity.CENTER
                setTextColor(AppColors.ACTION)
                typeface = Typeface.DEFAULT_BOLD
                background = roundedDrawable(Color.WHITE, dp(8).toFloat())
                setPadding(dp(10), dp(10), dp(10), dp(10))
            }
            addWithMargins(restStatusView!!, topDp = 12)
        }
        page.addWithMargins(status, bottomDp = 14)

        page.addWithMargins(workoutSafetyCard(program), bottomDp = 14)

        sections.forEach { section ->
            page.addView(sectionLabel(getString(section.titleRes)))
            section.exercises.forEachIndexed { exerciseIndex, exercise ->
                val exerciseName = displayName(exercise, locale)
                val exerciseCard = card()
                exerciseCard.addView(titleText(exerciseName, 18f))
                exerciseCard.addWithMargins(
                    exerciseAnimation(exercise, exerciseName, locale),
                    height = ViewGroup.LayoutParams.WRAP_CONTENT,
                    topDp = 8,
                )
                exercise.description.resolve(locale).takeIf(String::isNotBlank)?.let {
                    exerciseCard.addWithMargins(bodyText(it, 13f), topDp = 4)
                }
                exercise.sets.forEachIndexed { setIndex, set ->
                    val setKey = scopedSetId(
                        program,
                        section.key,
                        exercise,
                        exerciseIndex,
                        set,
                        setIndex,
                    )
                    val checked = setKey in active.completedSetIds
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        minimumHeight = dp(52)
                        setPadding(0, dp(4), 0, dp(4))
                    }
                    val checkBox = CheckBox(this).apply {
                        isChecked = checked
                        buttonTintList = ColorStateList.valueOf(
                            if (checked) AppColors.SUCCESS else AppColors.TEXT_SECONDARY,
                        )
                        contentDescription = getString(
                            R.string.set_complete_description,
                            set.number.takeIf { it > 0 } ?: setIndex + 1,
                            exerciseName,
                        )
                    }
                    val copy = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(
                            oneLineText(
                                getString(
                                    R.string.set_number,
                                    set.number.takeIf { it > 0 } ?: setIndex + 1,
                                ),
                                bold = true,
                            ),
                        )
                        addView(bodyText(setPrescription(set, locale), 13f))
                    }
                    row.addView(checkBox, LinearLayout.LayoutParams(dp(48), dp(48)))
                    row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    checkBox.setOnClickListener {
                        progressRepository.setCompleted(setKey, checkBox.isChecked)
                        if (checkBox.isChecked) {
                            if (doneCount + 1 >= allSetKeys.size) clearRest()
                            else set.breakSeconds?.takeIf { it > 0 }?.let(::startRest)
                        }
                        renderActive(program.id)
                    }
                    row.setOnClickListener { checkBox.performClick() }
                    exerciseCard.addView(row)
                }
                page.addWithMargins(exerciseCard, bottomDp = 10)
            }
        }

        page.addWithMargins(primaryButton(getString(R.string.finish_workout)) {
            requestFinish(program, doneCount, allSetKeys.size)
        }, topDp = 8)
        page.addWithMargins(secondaryButton(getString(R.string.abandon_workout)) {
            requestAbandon()
        }, topDp = 6)
        setBody(scroll(page))
        scheduleRestUpdates()
    }

    private fun requestFinish(program: TrainingProgram, completedSets: Int, totalSets: Int) {
        val finishAction = { askEffortAndFinish(program) }
        if (completedSets < totalSets) {
            AlertDialog.Builder(this)
                .setTitle(R.string.finish_early_title)
                .setMessage(R.string.finish_early_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.finish) { _, _ -> finishAction() }
                .show()
        } else {
            finishAction()
        }
    }

    private fun askEffortAndFinish(program: TrainingProgram) {
        val values = (1..10).toList()
        val labels = values.map { getString(R.string.effort_item, it) }.toTypedArray()
        var selected = (program.targetRpe ?: 7) - 1
        AlertDialog.Builder(this)
            .setTitle(R.string.effort_title)
            .setSingleChoiceItems(labels, selected) { _, which -> selected = which }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.finish) { _, _ ->
                progressRepository.finishWorkout(effortRating = values[selected])
                clearRest()
                Toast.makeText(this, R.string.workout_saved, Toast.LENGTH_SHORT).show()
                backStack.clear()
                showScreen(
                    if (program.id.startsWith("bb:")) Screen.BasketballProgress else Screen.Progress,
                    pushCurrent = false,
                )
            }
            .show()
    }

    private fun requestAbandon() {
        AlertDialog.Builder(this)
            .setTitle(R.string.abandon_title)
            .setMessage(R.string.abandon_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.abandon) { _, _ ->
                progressRepository.abandonWorkout()
                clearRest()
                val destination = backStack.pollLast() ?: Screen.Dashboard
                showScreen(destination, pushCurrent = false)
            }
            .show()
    }

    private fun startRest(seconds: Int) {
        restProgramId = progressRepository.activeWorkout()?.programId
        restEndsAtEpochMillis = System.currentTimeMillis() + seconds * 1_000L
    }

    private fun clearRest() {
        restProgramId = null
        restEndsAtEpochMillis = null
        restTimer?.cancel()
        restTimer = null
        restStatusView?.text = getString(R.string.rest_ready)
    }

    private fun scheduleRestUpdates() {
        restTimer?.cancel()
        val end = restEndsAtEpochMillis ?: run {
            restStatusView?.text = getString(R.string.rest_ready)
            return
        }
        val remaining = end - System.currentTimeMillis()
        if (remaining <= 0L) {
            onRestFinished()
            return
        }
        restTimer = object : CountDownTimer(remaining, 250L) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = ((millisUntilFinished + 999L) / 1_000L).coerceAtLeast(1L)
                restStatusView?.text = getString(R.string.rest_countdown, seconds)
            }

            override fun onFinish() = onRestFinished()
        }.start()
    }

    private fun onRestFinished() {
        restEndsAtEpochMillis = null
        restStatusView?.text = getString(R.string.rest_ready)
        if (settings.soundCues) {
            runCatching {
                if (toneGenerator == null) {
                    toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
                }
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
            }
        }
    }

    private fun renderProgress() {
        val catalog = catalog ?: return
        val locale = uiLocale()
        val all = allPrograms(catalog)
        val completedIds = progressRepository.completedProgramIds()
        val complete = all.count { it.id in completedIds }
        val percent = percentage(complete, all.size)
        val page = verticalPage()
        page.addView(titleText(getString(
            if (isBasketball(currentScreen)) R.string.basketball_progress else R.string.progress_title,
        ), 26f))

        val overall = card().apply {
            addView(titleText("$percent%", 34f))
            addWithMargins(
                bodyText(getString(R.string.workouts_completed_format, complete, all.size)),
                topDp = 3,
            )
            addWithMargins(horizontalProgress(percent), topDp = 12, height = dp(10))
        }
        page.addWithMargins(overall, topDp = 14, bottomDp = 16)

        page.addView(sectionLabel(getString(R.string.level_breakdown)))
        catalog.levels.sortedBy { it.number }.forEach { level ->
            val levelComplete = level.programs.count { it.id in completedIds }
            val row = card(clickable = true).apply {
                addView(titleText(displayName(level, locale), 17f))
                addWithMargins(
                    bodyText(
                        getString(R.string.level_progress_format, levelComplete, level.programs.size),
                        13f,
                    ),
                    topDp = 3,
                )
                addWithMargins(
                    horizontalProgress(percentage(levelComplete, level.programs.size)),
                    topDp = 7,
                    height = dp(7),
                )
                setOnClickListener { navigate(Screen.Workouts(level.number, isBasketball(currentScreen))) }
            }
            page.addWithMargins(row, bottomDp = 8)
        }

        if (isBasketball(currentScreen)) addBasketballChecks(page)
        page.addView(sectionLabel(getString(R.string.history)))
        val history = progressRepository.sessionHistory()
        if (history.isEmpty()) {
            page.addWithMargins(card().apply { addView(bodyText(getString(R.string.no_history))) }, bottomDp = 10)
        } else {
            val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
            history.take(100).forEach { session ->
                val program = programById(session.programId)
                val row = card(clickable = program != null).apply {
                    addView(
                        titleText(
                            program?.let { displayName(it, locale) } ?: session.programId,
                            17f,
                        ),
                    )
                    addWithMargins(
                        bodyText(
                            getString(
                                R.string.completed_on,
                                formatter.format(Date(session.completedAtEpochMillis)),
                                session.durationSeconds / 60L,
                            ),
                            13f,
                        ),
                        topDp = 3,
                    )
                    program?.let { target ->
                        setOnClickListener { navigate(Screen.Overview(target.id)) }
                    }
                }
                page.addWithMargins(row, bottomDp = 8)
            }
        }
        setBody(scroll(page))
    }

    private fun addBasketballChecks(page: LinearLayout) {
        val preferences = getSharedPreferences("street_basketball_skills", Context.MODE_PRIVATE)
        val passed = preferences.getStringSet("passed", emptySet()).orEmpty().toMutableSet()
        page.addView(sectionLabel(getString(R.string.basketball_checkpoints)))
        page.addView(bodyText(getString(R.string.basketball_checkpoint_note), 14f))
        BasketballSkills.checkpoints.forEachIndexed { levelIndex, checks ->
            val level = basketballCatalog?.levels?.getOrNull(levelIndex) ?: return@forEachIndexed
            page.addWithMargins(titleText(level.name.resolve(), 18f), topDp = 16, bottomDp = 4)
            checks.forEachIndexed { checkIndex, label ->
                val key = "l${levelIndex + 1}:c${checkIndex + 1}"
                page.addView(CheckBox(this).apply {
                    text = label
                    textSize = 14f
                    setTextColor(AppColors.TEXT)
                    minHeight = dp(52)
                    setPadding(0, dp(6), 0, dp(6))
                    isChecked = key in passed
                    buttonTintList = ColorStateList.valueOf(AppColors.SUCCESS)
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) passed.add(key) else passed.remove(key)
                        preferences.edit().putStringSet("passed", passed.toSet()).apply()
                    }
                })
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun renderSettings() {
        val page = verticalPage()
        page.addView(titleText(getString(R.string.settings_title), 26f))

        val preferencesCard = card()
        preferencesCard.addView(settingSwitch(
            getString(R.string.keep_screen_awake),
            settings.keepScreenAwake,
        ) { settings.keepScreenAwake = it })
        preferencesCard.addView(settingSwitch(
            getString(R.string.sound_cues),
            settings.soundCues,
        ) { settings.soundCues = it })
        page.addWithMargins(preferencesCard, bottomDp = 16)

        page.addView(sectionLabel(getString(R.string.safety_title)))
        val safety = card().apply {
            background = roundedDrawable(AppColors.WARNING_SURFACE, dp(10).toFloat())
            addView(titleText(getString(R.string.safety_title), 19f))
            addWithMargins(bodyText(getString(R.string.safety_body), 14f), topDp = 7)
        }
        page.addWithMargins(safety, bottomDp = 16)

        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        val about = card().apply {
            addView(titleText(getString(R.string.about_title), 19f))
            addWithMargins(bodyText(getString(R.string.about_body), 14f), topDp = 7)
            addWithMargins(bodyText(getString(R.string.version_format, version), 12f), topDp = 10)
        }
        page.addView(about)
        setBody(scroll(page))
    }

    private fun settingSwitch(label: String, checked: Boolean, onChanged: (Boolean) -> Unit): Switch =
        Switch(this).apply {
            text = label
            textSize = 15f
            setTextColor(AppColors.TEXT)
            isChecked = checked
            minHeight = dp(56)
            setPadding(0, dp(4), 0, dp(4))
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        }

    private fun sections(program: TrainingProgram): List<ExerciseSection> = listOf(
        ExerciseSection("warmup", R.string.warm_up, program.warmup),
        ExerciseSection("practice", R.string.skill_practice, program.practice),
        ExerciseSection("main",
            if (program.id.startsWith("bb:")) R.string.basketball_drills else R.string.main_training,
            program.exercises),
        ExerciseSection("cooldown", R.string.cool_down, program.cooldown),
    )

    private fun exerciseAnimation(
        exercise: TrainingExercise,
        exerciseName: CharSequence,
        locale: Locale,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (exercise.movementId?.startsWith("bb_") == true) {
            addView(BasketballCourtView(context, exercise.category ?: "footwork"),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(152)))
            return@apply
        }
        val preview = ExerciseAnimationView(context)
        val variations = exercise.sets.map { it.name.resolve(locale) }
            .filter(String::isNotBlank).distinct()
        fun showVariation(index: Int) {
            preview.bind(
                movementId = exercise.movementId,
                exerciseName = exerciseName,
                category = exercise.category,
                equipment = exercise.equipment,
                descriptor = variations.getOrNull(index) ?: exercise.description.resolve(locale),
            )
        }
        showVariation(0)
        addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(168)))
        addView(bodyText(getString(R.string.animation_tempo_note), 12f))
        val controls = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        var paused = false
        var slow = false
        val pause = secondaryButton(getString(R.string.animation_pause)) { }
        pause.setOnClickListener {
            paused = !paused
            preview.setPlaybackPaused(paused)
            pause.text = getString(if (paused) R.string.animation_play else R.string.animation_pause)
        }
        val speed = secondaryButton(getString(R.string.animation_slow)) { }
        speed.setOnClickListener {
            slow = !slow
            preview.setSlowPlayback(slow)
            speed.text = getString(if (slow) R.string.animation_normal else R.string.animation_slow)
        }
        controls.addView(pause, LinearLayout.LayoutParams(0, dp(48), 1f))
        controls.addView(speed, LinearLayout.LayoutParams(0, dp(48), 1f))
        addView(controls)
        if (exercise.movementId == null && variations.size > 1) {
            var variation = 0
            val change = secondaryButton(getString(R.string.animation_variation, 1, variations.size, variations[0])) { }
            change.setOnClickListener {
                variation = (variation + 1) % variations.size
                showVariation(variation)
                change.text = getString(R.string.animation_variation, variation + 1, variations.size, variations[variation])
            }
            addView(change)
        }
    }

    private fun workoutSafetyCard(program: TrainingProgram): LinearLayout = card().apply {
        if (program.id.startsWith("bb:")) {
            addView(titleText(getString(R.string.basketball_session_focus), 18f))
            addWithMargins(bodyText(program.readiness.resolve(), 14f), topDp = 7)
            addWithMargins(secondaryButton(getString(R.string.class_guidance_details)) {
                AlertDialog.Builder(this@RevivalActivity)
                    .setTitle(R.string.class_guidance_details)
                    .setMessage(getString(R.string.basketball_rhythm) + "\n\n" + program.safety.resolve())
                    .setPositiveButton(android.R.string.ok, null).show()
            }, topDp = 4)
            return@apply
        }
        addView(titleText(getString(R.string.class_guidance_title), 18f))
        addWithMargins(bodyText(getString(R.string.class_guidance_summary), 14f), topDp = 7)
        addWithMargins(secondaryButton(getString(R.string.class_guidance_details)) {
            val notes = buildList {
                add(getString(R.string.readiness_note))
                if (program.practice.isNotEmpty()) add(getString(R.string.skill_practice_note))
                add(getString(R.string.flexibility_note))
                program.readiness.resolve().takeIf(String::isNotBlank)?.let(::add)
                program.safety.resolve().takeIf(String::isNotBlank)?.let(::add)
            }.joinToString("\n\n")
            AlertDialog.Builder(this@RevivalActivity)
                .setTitle(R.string.class_guidance_details)
                .setMessage(notes)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }, topDp = 4)
    }

    private fun scopedSetId(
        program: TrainingProgram,
        section: String,
        exercise: TrainingExercise,
        exerciseIndex: Int,
        set: TrainingSet,
        setIndex: Int,
    ): String = buildString {
        append(program.id).append(':').append(section).append(':')
        append(exercise.id).append('-').append(exerciseIndex).append(':')
        append(set.id).append('-').append(setIndex)
    }

    private fun isBasketball(screen: Screen?): Boolean = when (screen) {
        Screen.Basketball, Screen.BasketballProgress -> true
        is Screen.Workouts -> screen.basketball
        is Screen.Overview -> screen.programId.startsWith("bb:")
        is Screen.Active -> screen.programId.startsWith("bb:")
        else -> false
    }

    private fun catalogFor(screen: Screen?): CurriculumCatalog? =
        if (isBasketball(screen)) basketballCatalog else gymnasticsCatalog

    private fun repositoryFor(screen: Screen?): SharedPreferencesProgressRepository =
        if (isBasketball(screen)) basketballProgress else gymnasticsProgress

    private fun programById(id: String): TrainingProgram? =
        (if (id.startsWith("bb:")) basketballCatalog else gymnasticsCatalog)
            ?.levels?.asSequence()?.flatMap { it.programs.asSequence() }?.firstOrNull { it.id == id }

    private fun canReplay(id: String): Boolean =
        id.startsWith("bb:") && basketballProgress.isProgramCompleted(id)

    private fun canStart(id: String, screen: Screen? = currentScreen): Boolean =
        assignedProgram(screen)?.id == id || canReplay(id)

    private fun allPrograms(catalog: CurriculumCatalog): List<TrainingProgram> =
        catalog.levels.sortedBy { it.number }.flatMap { level -> level.programs.sortedBy { it.number } }

    private fun assignedProgram(screen: Screen? = currentScreen): TrainingProgram? {
        val loadedCatalog = catalogFor(screen) ?: return null
        val completed = repositoryFor(screen).completedProgramIds()
        // The assignment is derived only from ordered completion state. An active session may
        // come from an older build, so it must never promote a later workout past an incomplete one.
        return allPrograms(loadedCatalog).firstOrNull { it.id !in completed }
    }

    private fun percentage(part: Int, whole: Int): Int =
        if (whole <= 0) 0 else ((part.toDouble() / whole.toDouble()) * 100.0).toInt().coerceIn(0, 100)

    private fun uiLocale(): Locale = settings.selectedLocale()

    private fun scroll(content: View): ScrollView = ScrollView(this).apply {
        isFillViewport = true
        clipToPadding = false
        addView(
            content,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun setBody(view: View) {
        bodyContainer.removeAllViews()
        bodyContainer.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun screenTitle(screen: Screen): String = when (screen) {
        Screen.Dashboard -> getString(R.string.dashboard_title)
        Screen.Levels -> getString(R.string.levels_title)
        Screen.Basketball -> getString(R.string.basketball_title)
        Screen.BasketballProgress -> getString(R.string.basketball_progress)
        is Screen.Workouts -> getString(R.string.workouts_title, screen.levelNumber)
        is Screen.Overview -> getString(R.string.workout_overview)
        is Screen.Active -> getString(R.string.active_workout)
        Screen.Progress -> getString(R.string.progress_title)
        Screen.Settings -> getString(R.string.settings_title)
    }

    private fun isNested(screen: Screen): Boolean =
        screen is Screen.Workouts || screen is Screen.Overview || screen is Screen.Active ||
            screen == Screen.BasketballProgress

    private fun rootFor(screen: Screen): Screen = if (isBasketball(screen)) Screen.Basketball else when (screen) {
        is Screen.Workouts, is Screen.Overview, is Screen.Active -> Screen.Levels
        else -> screen
    }

    private fun isValidScreen(screen: Screen): Boolean = when (screen) {
        is Screen.Workouts -> catalogFor(screen)?.levels?.any { it.number == screen.levelNumber } == true
        is Screen.Overview -> programById(screen.programId) != null
        is Screen.Active -> programById(screen.programId) != null &&
            repositoryFor(screen).activeWorkout()?.programId == screen.programId &&
            canStart(screen.programId, screen)
        else -> true
    }

    private fun screenFromBundle(bundle: Bundle): Screen? = when (bundle.getString(STATE_SCREEN)) {
        Screen.Dashboard.javaClass.simpleName -> Screen.Dashboard
        Screen.Levels.javaClass.simpleName -> Screen.Levels
        Screen.Basketball.javaClass.simpleName -> Screen.Basketball
        Screen.BasketballProgress.javaClass.simpleName -> Screen.BasketballProgress
        Screen.Progress.javaClass.simpleName -> Screen.Progress
        Screen.Settings.javaClass.simpleName -> Screen.Settings
        Screen.Workouts::class.java.simpleName -> Screen.Workouts(
            bundle.getInt(STATE_LEVEL, -1), bundle.getBoolean(STATE_BASKETBALL),
        )
        Screen.Overview::class.java.simpleName -> bundle.getString(STATE_PROGRAM)?.let(Screen::Overview)
        Screen.Active::class.java.simpleName -> bundle.getString(STATE_PROGRAM)?.let(Screen::Active)
        else -> null
    }

    companion object {
        private const val STATE_SCREEN = "screen"
        private const val STATE_LEVEL = "level"
        private const val STATE_PROGRAM = "program"
        private const val STATE_REST_END = "rest_end_epoch_millis"
        private const val STATE_REST_PROGRAM = "rest_program"
        private const val STATE_BASKETBALL = "basketball"
    }
}
