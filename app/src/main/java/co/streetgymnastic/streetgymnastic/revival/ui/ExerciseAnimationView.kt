package co.streetgymnastic.streetgymnastic.revival.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lightweight, offline line-person demonstrations backed by exercise_animations.json.
 *
 * These illustrations are movement cues, not a substitute for the exercise's written
 * coaching and safety notes. The JSON deliberately owns the poses and mappings so the
 * artwork can be reviewed or refined without changing rendering code.
 */
class ExerciseAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val library = AnimationLibrary.load(context.applicationContext)
    private var resolved = library.fallback
    private var animationPhase = resolved.template.reducedMotionFrame
    private var animator: ValueAnimator? = null
    private var reducedMotionRequested = false
    private val visibleRect = Rect()
    private var scrollListenerAttached = false
    private val scrollChangedListener = ViewTreeObserver.OnScrollChangedListener {
        updateAnimationState()
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = library.palette.background
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = library.palette.border
    }
    private val figurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(4f)
        color = library.palette.figure
    }
    private val rearLimbPaint = Paint(figurePaint).apply {
        color = withAlpha(library.palette.figure, 145)
        strokeWidth = dp(3.25f)
    }
    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = library.palette.accent
    }
    private val apparatusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(3f)
        color = library.palette.apparatus
    }
    private val apparatusFillPaint = Paint(apparatusPaint).apply {
        style = Paint.Style.FILL
        color = withAlpha(library.palette.apparatus, 38)
    }
    private val floorPaint = Paint(apparatusPaint).apply {
        strokeWidth = dp(2f)
        color = library.palette.floor
    }
    private val accentPaint = Paint(apparatusPaint).apply {
        strokeWidth = dp(2.5f)
        color = library.palette.accent
    }
    private val torsoPath = Path()
    private val primitivePath = Path()

    init {
        minimumWidth = dp(220f).roundToInt()
        minimumHeight = dp(156f).roundToInt()
        setPadding(dp(10f).roundToInt(), dp(8f).roundToInt(), dp(10f).roundToInt(), dp(8f).roundToInt())
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isClickable = false
        isFocusable = false
        contentDescription = "Line-person exercise movement guide"
    }

    /**
     * Selects an animation. A stable catalog movement ID gives the most precise result.
     * Exercises without one fall back through exact English aliases, then JSON
     * name/category/equipment heuristics.
     */
    fun bind(
        movementId: String?,
        exerciseName: CharSequence,
        category: String? = null,
        equipment: Collection<String> = emptyList(),
        descriptor: CharSequence? = null,
    ): ExerciseAnimationView {
        val readableName = exerciseName.toString().trim().ifBlank { "this exercise" }
        resolved = library.resolve(
            movementId = movementId,
            exerciseName = readableName,
            descriptor = descriptor?.toString().orEmpty(),
            category = category,
            equipment = equipment,
        )
        contentDescription = "Line-person movement guide for $readableName. Follow the written instructions for exact technique."
        animationPhase = resolved.template.reducedMotionFrame
        restartAnimationIfNeeded()
        invalidate()
        return this
    }

    /** Lets the containing screen honor an in-app reduce-motion preference as well. */
    fun setReducedMotion(enabled: Boolean) {
        if (reducedMotionRequested == enabled) return
        reducedMotionRequested = enabled
        animationPhase = resolved.template.reducedMotionFrame
        restartAnimationIfNeeded()
        invalidate()
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.ImageView"

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = maxOf(suggestedMinimumWidth, dp(260f).roundToInt()) + paddingLeft + paddingRight
        val desiredHeight = maxOf(suggestedMinimumHeight, dp(156f).roundToInt()) + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!scrollListenerAttached) {
            viewTreeObserver.addOnScrollChangedListener(scrollChangedListener)
            scrollListenerAttached = true
        }
        restartAnimationIfNeeded()
        post { if (isAttachedToWindow) updateAnimationState() }
    }

    override fun onDetachedFromWindow() {
        if (scrollListenerAttached) {
            viewTreeObserver.takeIf(ViewTreeObserver::isAlive)
                ?.removeOnScrollChangedListener(scrollChangedListener)
            scrollListenerAttached = false
        }
        stopAnimator()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) updateAnimationState()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) updateAnimationState()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        val right = (width - paddingRight).toFloat()
        val bottom = (height - paddingBottom).toFloat()
        if (right <= left || bottom <= top) return

        val corner = dp(12f)
        canvas.drawRoundRect(left, top, right, bottom, corner, corner, backgroundPaint)
        canvas.drawRoundRect(left, top, right, bottom, corner, corner, borderPaint)

        val innerLeft = left + dp(5f)
        val innerTop = top + dp(4f)
        val innerWidth = (right - left - dp(10f)).coerceAtLeast(1f)
        val innerHeight = (bottom - top - dp(8f)).coerceAtLeast(1f)
        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        resolved.apparatus.forEach { name ->
            library.apparatus[name]?.forEach { primitive ->
                drawPrimitive(canvas, primitive, innerLeft, innerTop, innerWidth, innerHeight)
            }
        }
        drawFigure(canvas, innerLeft, innerTop, innerWidth, innerHeight)
        canvas.restore()
    }

    private fun drawPrimitive(
        canvas: Canvas,
        primitive: ApparatusPrimitive,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
    ) {
        val values = primitive.values
        val stroke = when (primitive.role) {
            "floor" -> floorPaint
            "accent" -> accentPaint
            else -> apparatusPaint
        }
        when (primitive.type) {
            "line" -> if (values.size >= 4) {
                canvas.drawLine(
                    left + values[0] * width,
                    top + values[1] * height,
                    left + values[2] * width,
                    top + values[3] * height,
                    stroke,
                )
            }
            "polyline" -> if (values.size >= 4) {
                primitivePath.rewind()
                primitivePath.moveTo(left + values[0] * width, top + values[1] * height)
                var index = 2
                while (index + 1 < values.size) {
                    primitivePath.lineTo(left + values[index] * width, top + values[index + 1] * height)
                    index += 2
                }
                canvas.drawPath(primitivePath, stroke)
            }
            "rect", "round_rect" -> if (values.size >= 4) {
                val l = left + values[0] * width
                val t = top + values[1] * height
                val r = left + values[2] * width
                val b = top + values[3] * height
                if (primitive.filled) {
                    if (primitive.type == "round_rect") {
                        canvas.drawRoundRect(l, t, r, b, dp(5f), dp(5f), apparatusFillPaint)
                    } else {
                        canvas.drawRect(l, t, r, b, apparatusFillPaint)
                    }
                }
                if (primitive.type == "round_rect") {
                    canvas.drawRoundRect(l, t, r, b, dp(5f), dp(5f), stroke)
                } else {
                    canvas.drawRect(l, t, r, b, stroke)
                }
            }
            "circle" -> if (values.size >= 3) {
                canvas.drawCircle(
                    left + values[0] * width,
                    top + values[1] * height,
                    values[2] * min(width, height),
                    stroke,
                )
            }
        }
    }

    private fun drawFigure(canvas: Canvas, left: Float, top: Float, width: Float, height: Float) {
        val template = resolved.template
        val timeline = if (isAnimationRunning()) template.timeline(animationPhase) else template.reducedMotionFrame
        val frames = template.keyframes
        var first = frames.first()
        var second = frames.last()
        for (index in 0 until frames.lastIndex) {
            if (timeline <= frames[index + 1].at) {
                first = frames[index]
                second = frames[index + 1]
                break
            }
        }
        val span = (second.at - first.at).coerceAtLeast(0.0001f)
        val raw = ((timeline - first.at) / span).coerceIn(0f, 1f)
        val fraction = if (template.easing == "linear") raw else raw * raw * (3f - 2f * raw)

        fun x(joint: Int): Float = left + lerp(first.joints[joint * 2], second.joints[joint * 2], fraction) * width
        fun y(joint: Int): Float = top + lerp(first.joints[joint * 2 + 1], second.joints[joint * 2 + 1], fraction) * height
        fun bone(from: Int, to: Int, paint: Paint) = canvas.drawLine(x(from), y(from), x(to), y(to), paint)

        // Far-side limbs are lighter, giving the tiny figure enough depth to remain legible.
        bone(NECK, RIGHT_ELBOW, rearLimbPaint)
        bone(RIGHT_ELBOW, RIGHT_HAND, rearLimbPaint)
        bone(HIP, RIGHT_KNEE, rearLimbPaint)
        bone(RIGHT_KNEE, RIGHT_FOOT, rearLimbPaint)

        val curve = lerp(first.spineCurve, second.spineCurve, fraction) * min(width, height)
        val neckX = x(NECK)
        val neckY = y(NECK)
        val hipX = x(HIP)
        val hipY = y(HIP)
        val dx = hipX - neckX
        val dy = hipY - neckY
        val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        torsoPath.rewind()
        torsoPath.moveTo(neckX, neckY)
        torsoPath.quadTo(
            (neckX + hipX) * 0.5f - dy / length * curve,
            (neckY + hipY) * 0.5f + dx / length * curve,
            hipX,
            hipY,
        )
        canvas.drawPath(torsoPath, figurePaint)
        bone(HEAD, NECK, figurePaint)
        bone(NECK, LEFT_ELBOW, figurePaint)
        bone(LEFT_ELBOW, LEFT_HAND, figurePaint)
        bone(HIP, LEFT_KNEE, figurePaint)
        bone(LEFT_KNEE, LEFT_FOOT, figurePaint)

        val radius = template.headRadius * min(width, height)
        canvas.drawCircle(x(HEAD), y(HEAD), radius, figurePaint)
        val jointRadius = dp(2.2f)
        canvas.drawCircle(x(LEFT_HAND), y(LEFT_HAND), jointRadius, jointPaint)
        canvas.drawCircle(x(RIGHT_HAND), y(RIGHT_HAND), jointRadius, jointPaint)
        canvas.drawCircle(x(LEFT_FOOT), y(LEFT_FOOT), jointRadius, jointPaint)
        canvas.drawCircle(x(RIGHT_FOOT), y(RIGHT_FOOT), jointRadius, jointPaint)
    }

    private fun restartAnimationIfNeeded() {
        stopAnimator()
        animationPhase = resolved.template.reducedMotionFrame
        updateAnimationState()
        invalidate()
    }

    private fun updateAnimationState() {
        if (!shouldAnimate()) {
            if (animator != null) {
                stopAnimator()
                animationPhase = resolved.template.reducedMotionFrame
                invalidate()
            }
            return
        }
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = resolved.template.durationMs
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                animationPhase = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    private fun shouldAnimate(): Boolean =
        isAttachedToWindow &&
            windowVisibility == VISIBLE &&
            visibility == VISIBLE &&
            isShown &&
            width > 0 &&
            height > 0 &&
            getGlobalVisibleRect(visibleRect) &&
            !visibleRect.isEmpty &&
            !reducedMotionRequested &&
            systemAnimationsEnabled()

    private fun isAnimationRunning(): Boolean = animator?.isRunning == true

    private fun stopAnimator() {
        animator?.cancel()
        animator?.removeAllUpdateListeners()
        animator = null
    }

    private fun systemAnimationsEnabled(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }
    } catch (_: RuntimeException) {
        true
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun lerp(start: Float, end: Float, fraction: Float): Float =
        start + (end - start) * fraction

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    internal companion object {
        const val JOINT_COUNT = 11
        const val HEAD = 0
        const val NECK = 1
        const val HIP = 2
        const val LEFT_ELBOW = 3
        const val LEFT_HAND = 4
        const val RIGHT_ELBOW = 5
        const val RIGHT_HAND = 6
        const val LEFT_KNEE = 7
        const val LEFT_FOOT = 8
        const val RIGHT_KNEE = 9
        const val RIGHT_FOOT = 10
    }
}

private data class Palette(
    val background: Int,
    val border: Int,
    val figure: Int,
    val accent: Int,
    val apparatus: Int,
    val floor: Int,
)

private data class ApparatusPrimitive(
    val type: String,
    val role: String,
    val values: FloatArray,
    val filled: Boolean,
)

private data class PoseKeyframe(
    val at: Float,
    val joints: FloatArray,
    val spineCurve: Float,
)

private data class MotionTemplate(
    val name: String,
    val durationMs: Long,
    val mode: String,
    val easing: String,
    val reducedMotionFrame: Float,
    val headRadius: Float,
    val apparatus: List<String>,
    val keyframes: List<PoseKeyframe>,
) {
    fun timeline(rawPhase: Float): Float = when (mode) {
        "ping_pong" -> if (rawPhase <= 0.5f) rawPhase * 2f else (1f - rawPhase) * 2f
        else -> rawPhase
    }.coerceIn(0f, 1f)
}

private data class MotionReference(
    val templateName: String,
    val apparatus: List<String>?,
)

private data class ResolvedMotion(
    val template: MotionTemplate,
    val apparatus: List<String>,
)

private data class NameHeuristic(
    val tokens: List<String>,
    val reference: MotionReference,
)

private class AnimationLibrary(
    val palette: Palette,
    val apparatus: Map<String, List<ApparatusPrimitive>>,
    private val templates: Map<String, MotionTemplate>,
    private val movements: Map<String, MotionReference>,
    private val aliases: Map<String, MotionReference>,
    private val categoryDefaults: Map<String, MotionReference>,
    private val equipmentDefaults: Map<String, MotionReference>,
    private val heuristics: List<NameHeuristic>,
    fallbackName: String,
) {
    val fallback: ResolvedMotion = resolveReference(
        MotionReference(fallbackName, null),
    ) ?: ResolvedMotion(emergencyTemplate(), listOf("floor"))

    fun resolve(
        movementId: String?,
        exerciseName: String,
        descriptor: String,
        category: String?,
        equipment: Collection<String>,
    ): ResolvedMotion {
        val idKey = normalize(movementId.orEmpty())
        movements[idKey]?.let(::resolveReference)?.let { return it }

        val nameKey = normalize(exerciseName)
        val exactAlias = aliases[nameKey]
        if (nameKey != "compound_exercise" || descriptor.isBlank()) {
            exactAlias?.let(::resolveReference)?.let { return it }
        }
        val searchable = normalize("$exerciseName $descriptor")
        heuristics.asSequence()
            .mapIndexedNotNull { priority, heuristic ->
                heuristic.tokens
                    .map(searchable::indexOf)
                    .filter { it >= 0 }
                    .minOrNull()
                    ?.let { matchIndex -> Triple(matchIndex, priority, heuristic.reference) }
            }
            .minWithOrNull(compareBy<Triple<Int, Int, MotionReference>> { it.first }.thenBy { it.second })
            ?.third
            ?.let(::resolveReference)
            ?.let { return it }
        exactAlias?.let(::resolveReference)?.let { return it }

        equipment.asSequence()
            .map(::normalize)
            .mapNotNull(equipmentDefaults::get)
            .mapNotNull(::resolveReference)
            .firstOrNull()
            ?.let { return it }

        normalize(category.orEmpty()).takeIf(String::isNotEmpty)
            ?.let(categoryDefaults::get)
            ?.let(::resolveReference)
            ?.let { return it }

        return fallback
    }

    private fun resolveReference(reference: MotionReference): ResolvedMotion? {
        val template = templates[reference.templateName] ?: return null
        return ResolvedMotion(template, reference.apparatus ?: template.apparatus)
    }

    companion object {
        @Volatile
        private var cached: AnimationLibrary? = null

        fun load(context: Context): AnimationLibrary {
            cached?.let { return it }
            return synchronized(this) {
                cached ?: runCatching {
                    context.assets.open("exercise_animations.json").bufferedReader().use { reader ->
                        parse(JSONObject(reader.readText()))
                    }
                }.getOrElse { fallbackLibrary() }.also { cached = it }
            }
        }

        private fun parse(root: JSONObject): AnimationLibrary {
            val paletteJson = root.optJSONObject("palette") ?: JSONObject()
            val palette = Palette(
                background = paletteJson.color("background", AppColors.INFO_SURFACE),
                border = paletteJson.color("border", AppColors.DIVIDER),
                figure = paletteJson.color("figure", AppColors.NUMBER_DARK),
                accent = paletteJson.color("accent", AppColors.ACTION_LIGHT),
                apparatus = paletteJson.color("apparatus", AppColors.NUMBER),
                floor = paletteJson.color("floor", 0xFF90A4AE.toInt()),
            )
            val apparatus = root.optJSONObject("apparatus").mapObject { _, value ->
                (value as? JSONArray).mapObjects { primitive ->
                    ApparatusPrimitive(
                        type = primitive.optString("type", "line"),
                        role = primitive.optString("role", "apparatus"),
                        values = primitive.optJSONArray("values").floatArray(),
                        filled = primitive.optBoolean("filled", false),
                    )
                }
            }
            val poses = root.optJSONObject("poses").mapObjectNotNull { name, value ->
                val json = value as? JSONObject ?: return@mapObjectNotNull null
                val joints = json.optJSONArray("joints").jointArray()
                if (joints.size != ExerciseAnimationView.JOINT_COUNT * 2) return@mapObjectNotNull null
                name to PoseKeyframe(
                    at = 0f,
                    joints = joints,
                    spineCurve = json.optDouble("spine_curve", 0.0).toFloat(),
                )
            }
            val templates = root.optJSONObject("templates").mapObjectNotNull { name, value ->
                val json = value as? JSONObject ?: return@mapObjectNotNull null
                val keyframes = json.optJSONArray("keyframes").mapObjectsNotNull { frame ->
                    val namedPose = poses[frame.optString("pose")]
                    val joints = frame.optJSONArray("joints")?.jointArray() ?: namedPose?.joints ?: FloatArray(0)
                    if (joints.size != ExerciseAnimationView.JOINT_COUNT * 2) return@mapObjectsNotNull null
                    PoseKeyframe(
                        at = frame.optDouble("at", 0.0).toFloat().coerceIn(0f, 1f),
                        joints = joints,
                        spineCurve = if (frame.has("spine_curve")) {
                            frame.optDouble("spine_curve", 0.0).toFloat()
                        } else {
                            namedPose?.spineCurve ?: 0f
                        },
                    )
                }.sortedBy(PoseKeyframe::at)
                if (keyframes.isEmpty()) return@mapObjectNotNull null
                name to MotionTemplate(
                    name = name,
                    durationMs = json.optLong("duration_ms", 1_600L).coerceIn(500L, 8_000L),
                    mode = json.optString("mode", "ping_pong"),
                    easing = json.optString("easing", "smooth"),
                    reducedMotionFrame = json.optDouble("reduced_motion_frame", 0.5).toFloat().coerceIn(0f, 1f),
                    headRadius = json.optDouble("head_radius", 0.035).toFloat().coerceIn(0.02f, 0.07f),
                    apparatus = json.optJSONArray("apparatus").strings(),
                    keyframes = if (keyframes.size == 1) listOf(
                        keyframes.single().copy(at = 0f),
                        keyframes.single().copy(at = 1f),
                    ) else keyframes,
                )
            }
            require(templates.isNotEmpty()) { "No animation templates" }
            return AnimationLibrary(
                palette = palette,
                apparatus = apparatus,
                templates = templates,
                movements = root.optJSONObject("movements").references(),
                aliases = root.optJSONObject("aliases").references(normalizeKeys = true),
                categoryDefaults = root.optJSONObject("category_defaults").references(normalizeKeys = true),
                equipmentDefaults = root.optJSONObject("equipment_defaults").references(normalizeKeys = true),
                heuristics = root.optJSONArray("name_heuristics").mapObjectsNotNull { item ->
                    val reference = item.opt("animation").asReference() ?: return@mapObjectsNotNull null
                    NameHeuristic(item.optJSONArray("contains").strings().map(::normalize), reference)
                }.filter { it.tokens.isNotEmpty() },
                fallbackName = root.optString("fallback", templates.keys.first()),
            )
        }

        private fun fallbackLibrary(): AnimationLibrary {
            val template = emergencyTemplate()
            return AnimationLibrary(
                palette = Palette(
                    AppColors.INFO_SURFACE,
                    AppColors.DIVIDER,
                    AppColors.NUMBER_DARK,
                    AppColors.ACTION_LIGHT,
                    AppColors.NUMBER,
                    0xFF90A4AE.toInt(),
                ),
                apparatus = mapOf(
                    "floor" to listOf(
                        ApparatusPrimitive("line", "floor", floatArrayOf(0.08f, 0.88f, 0.92f, 0.88f), false),
                    ),
                ),
                templates = mapOf(template.name to template),
                movements = emptyMap(),
                aliases = emptyMap(),
                categoryDefaults = emptyMap(),
                equipmentDefaults = emptyMap(),
                heuristics = emptyList(),
                fallbackName = template.name,
            )
        }

        private fun emergencyTemplate(): MotionTemplate {
            val joints = floatArrayOf(
                0.50f, 0.22f, 0.50f, 0.31f, 0.50f, 0.56f,
                0.42f, 0.43f, 0.39f, 0.57f, 0.58f, 0.43f, 0.61f, 0.57f,
                0.45f, 0.71f, 0.43f, 0.86f, 0.55f, 0.71f, 0.57f, 0.86f,
            )
            return MotionTemplate(
                name = "standing_mobility",
                durationMs = 1_800L,
                mode = "ping_pong",
                easing = "smooth",
                reducedMotionFrame = 0.5f,
                headRadius = 0.035f,
                apparatus = listOf("floor"),
                keyframes = listOf(PoseKeyframe(0f, joints, 0f), PoseKeyframe(1f, joints, 0f)),
            )
        }
    }
}

private fun normalize(value: String): String = value
    .lowercase(Locale.ENGLISH)
    .replace(Regex("[^a-z0-9]+"), "_")
    .trim('_')

private fun JSONObject.color(key: String, fallback: Int): Int =
    runCatching { Color.parseColor(optString(key)) }.getOrDefault(fallback)

private fun JSONObject?.references(normalizeKeys: Boolean = false): Map<String, MotionReference> =
    mapObjectNotNull { key, value ->
        value.asReference()?.let { (if (normalizeKeys) normalize(key) else key) to it }
    }

private fun Any?.asReference(): MotionReference? = when (this) {
    is String -> takeIf(String::isNotBlank)?.let { MotionReference(it, null) }
    is JSONObject -> optString("template").takeIf(String::isNotBlank)?.let { name ->
        MotionReference(
            templateName = name,
            apparatus = optJSONArray("apparatus")?.strings(),
        )
    }
    else -> null
}

private inline fun <T> JSONObject?.mapObject(transform: (String, Any?) -> T): Map<String, T> {
    if (this == null) return emptyMap()
    return buildMap {
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            put(key, transform(key, opt(key)))
        }
    }
}

private inline fun <T> JSONObject?.mapObjectNotNull(transform: (String, Any?) -> Pair<String, T>?): Map<String, T> {
    if (this == null) return emptyMap()
    return buildMap {
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            transform(key, opt(key))?.let { (mappedKey, value) -> put(mappedKey, value) }
        }
    }
}

private inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (index in 0 until length()) optJSONObject(index)?.let { add(transform(it)) }
    }
}

private inline fun <T : Any> JSONArray?.mapObjectsNotNull(transform: (JSONObject) -> T?): List<T> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (index in 0 until length()) optJSONObject(index)?.let(transform)?.let(::add)
    }
}

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
    }
}

private fun JSONArray?.floatArray(): FloatArray {
    if (this == null) return FloatArray(0)
    return FloatArray(length()) { index -> optDouble(index, 0.0).toFloat() }
}

private fun JSONArray?.jointArray(): FloatArray {
    if (this == null) return FloatArray(0)
    val flattened = FloatArray(length() * 2)
    for (index in 0 until length()) {
        val pair = optJSONArray(index) ?: return FloatArray(0)
        flattened[index * 2] = pair.optDouble(0, 0.5).toFloat().coerceIn(-0.2f, 1.2f)
        flattened[index * 2 + 1] = pair.optDouble(1, 0.5).toFloat().coerceIn(-0.2f, 1.2f)
    }
    return flattened
}
