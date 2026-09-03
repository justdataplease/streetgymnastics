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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Offline line-figure movement guides backed by exercise_animations.json (schema 2).
 *
 * The figure is an articulated side-view rig with fixed bone lengths. Every pose stores
 * absolute joint angles plus one pinned joint (hands on the bar, feet on the floor), so
 * limbs never stretch and contact points stay put while the body moves between keyframes.
 * The canvas is scaled uniformly, so proportions are identical on every screen size.
 *
 * These illustrations are movement cues, not a substitute for the written coaching and
 * safety notes. The JSON owns poses, timing, and mappings so the artwork can be reviewed
 * and refined without touching rendering code (see tools/preview_animations.py).
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
        color = library.palette.figure
    }
    private val farLimbPaint = Paint(figurePaint).apply {
        color = blend(library.palette.figure, library.palette.background, 0.42f)
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = library.palette.figure
    }
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = library.palette.accent
    }
    private val apparatusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = library.palette.apparatus
    }
    private val apparatusFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = blend(library.palette.apparatus, library.palette.background, 0.85f)
    }
    private val floorPaint = Paint(apparatusPaint).apply { color = library.palette.floor }
    private val accentPaint = Paint(apparatusPaint).apply { color = library.palette.accent }
    private val torsoPath = Path()
    private val primitivePath = Path()
    private val joints = FloatArray(Rig.JOINT_COUNT * 2)
    private val hipScratchA = FloatArray(Rig.JOINT_COUNT * 2)
    private val hipScratchB = FloatArray(Rig.JOINT_COUNT * 2)
    private val blendedPose = Pose()

    init {
        minimumWidth = dp(220f).roundToInt()
        minimumHeight = dp(156f).roundToInt()
        setPadding(dp(10f).roundToInt(), dp(8f).roundToInt(), dp(10f).roundToInt(), dp(8f).roundToInt())
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isClickable = false
        isFocusable = false
        contentDescription = "Line-figure exercise movement guide"
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
        contentDescription = "Line-figure movement guide for $readableName. Follow the written instructions for exact technique."
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

        // Uniform scale: the logical canvas keeps its aspect ratio and is centered.
        val innerWidth = (right - left - dp(8f)).coerceAtLeast(1f)
        val innerHeight = (bottom - top - dp(6f)).coerceAtLeast(1f)
        val scale = min(innerWidth / library.canvasWidth, innerHeight / library.canvasHeight)
        val originX = left + (right - left - library.canvasWidth * scale) * 0.5f
        val originY = top + (bottom - top - library.canvasHeight * scale) * 0.5f

        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        resolved.apparatus.forEach { name ->
            library.apparatus[name]?.forEach { primitive ->
                drawPrimitive(canvas, primitive, originX, originY, scale)
            }
        }
        drawFigure(canvas, originX, originY, scale)
        canvas.restore()
    }

    private fun drawPrimitive(canvas: Canvas, primitive: ApparatusPrimitive, ox: Float, oy: Float, scale: Float) {
        val values = primitive.values
        val stroke = when (primitive.role) {
            "floor" -> floorPaint
            "accent" -> accentPaint
            else -> apparatusPaint
        }
        stroke.strokeWidth = (if (primitive.role == "floor") 1.4f else 1.7f) * scale
        fun x(index: Int) = ox + values[index] * scale
        fun y(index: Int) = oy + values[index] * scale
        when (primitive.type) {
            "line" -> if (values.size >= 4) canvas.drawLine(x(0), y(1), x(2), y(3), stroke)
            "polyline" -> if (values.size >= 4) {
                primitivePath.rewind()
                primitivePath.moveTo(x(0), y(1))
                var index = 2
                while (index + 1 < values.size) {
                    primitivePath.lineTo(x(index), y(index + 1))
                    index += 2
                }
                canvas.drawPath(primitivePath, stroke)
            }
            "rect", "round_rect" -> if (values.size >= 4) {
                val radius = if (primitive.type == "round_rect") 2.5f * scale else 0f
                if (primitive.filled) canvas.drawRoundRect(x(0), y(1), x(2), y(3), radius, radius, apparatusFillPaint)
                canvas.drawRoundRect(x(0), y(1), x(2), y(3), radius, radius, stroke)
            }
            "circle" -> if (values.size >= 3) canvas.drawCircle(x(0), y(1), values[2] * scale, stroke)
        }
    }

    private fun drawFigure(canvas: Canvas, ox: Float, oy: Float, scale: Float) {
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

        val rig = library.rig
        blendedPose.setInterpolated(first.pose, second.pose, fraction)
        if (first.pose.pinJoint == second.pose.pinJoint) {
            rig.solve(blendedPose, joints, null)
        } else {
            rig.solve(first.pose, hipScratchA, null)
            rig.solve(second.pose, hipScratchB, null)
            val hipX = lerp(hipScratchA[Rig.HIP * 2], hipScratchB[Rig.HIP * 2], fraction)
            val hipY = lerp(hipScratchA[Rig.HIP * 2 + 1], hipScratchB[Rig.HIP * 2 + 1], fraction)
            rig.solve(blendedPose, joints, floatArrayOf(hipX, hipY))
        }

        fun x(joint: Int): Float = ox + joints[joint * 2] * scale
        fun y(joint: Int): Float = oy + joints[joint * 2 + 1] * scale
        fun bone(from: Int, to: Int, paint: Paint) = canvas.drawLine(x(from), y(from), x(to), y(to), paint)

        figurePaint.strokeWidth = 2.6f * scale
        farLimbPaint.strokeWidth = 2.2f * scale

        // Far-side limbs are lighter so the small figure keeps its depth and stays legible.
        bone(Rig.HIP, Rig.KNEE_FAR, farLimbPaint)
        bone(Rig.KNEE_FAR, Rig.FOOT_FAR, farLimbPaint)
        bone(Rig.FOOT_FAR, Rig.TOE_FAR, farLimbPaint)
        bone(Rig.SHOULDER, Rig.ELBOW_FAR, farLimbPaint)
        bone(Rig.ELBOW_FAR, Rig.HAND_FAR, farLimbPaint)

        val hipX = x(Rig.HIP)
        val hipY = y(Rig.HIP)
        val neckX = x(Rig.NECK)
        val neckY = y(Rig.NECK)
        val dx = neckX - hipX
        val dy = neckY - hipY
        val length = hypot(dx, dy).coerceAtLeast(1f)
        val curve = blendedPose.spine * scale
        val frontX = -dy / length * blendedPose.facing
        val frontY = dx / length * blendedPose.facing
        torsoPath.rewind()
        torsoPath.moveTo(hipX, hipY)
        torsoPath.quadTo(
            (hipX + neckX) * 0.5f + frontX * curve,
            (hipY + neckY) * 0.5f + frontY * curve,
            neckX,
            neckY,
        )
        canvas.drawPath(torsoPath, figurePaint)
        bone(Rig.NECK, Rig.HEAD, figurePaint)
        canvas.drawCircle(x(Rig.HEAD), y(Rig.HEAD), rig.headRadius * scale, headPaint)

        bone(Rig.HIP, Rig.KNEE_NEAR, figurePaint)
        bone(Rig.KNEE_NEAR, Rig.FOOT_NEAR, figurePaint)
        bone(Rig.FOOT_NEAR, Rig.TOE_NEAR, figurePaint)
        bone(Rig.SHOULDER, Rig.ELBOW_NEAR, figurePaint)
        bone(Rig.ELBOW_NEAR, Rig.HAND_NEAR, figurePaint)

        val handRadius = 1.6f * scale
        canvas.drawCircle(x(Rig.HAND_FAR), y(Rig.HAND_FAR), handRadius, handPaint)
        canvas.drawCircle(x(Rig.HAND_NEAR), y(Rig.HAND_NEAR), handRadius, handPaint)
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

    private fun blend(color: Int, towards: Int, amount: Float): Int {
        val keep = 1f - amount
        return Color.argb(
            255,
            (Color.red(color) * keep + Color.red(towards) * amount).roundToInt().coerceIn(0, 255),
            (Color.green(color) * keep + Color.green(towards) * amount).roundToInt().coerceIn(0, 255),
            (Color.blue(color) * keep + Color.blue(towards) * amount).roundToInt().coerceIn(0, 255),
        )
    }
}

private fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction

/** Shortest-arc interpolation between two absolute angles in degrees. */
private fun lerpAngle(start: Float, end: Float, fraction: Float): Float {
    var delta = (end - start) % 360f
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return start + delta * fraction
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

/**
 * One body configuration. Angles are absolute degrees: 0 = up, 90 = right, 180 = down.
 * [facing] is +1 when the figure's front is to the right of its torso axis, -1 otherwise.
 */
internal class Pose {
    var facing = 1f
    var torso = 0f
    var head = Float.NaN // NaN = follow the torso
    var spine = 0f
    var shrug = 0f
    var protract = 0f
    val armNear = floatArrayOf(180f, 180f)
    val armFar = floatArrayOf(180f, 180f)
    val legNear = floatArrayOf(180f, 180f)
    val legFar = floatArrayOf(180f, 180f)
    var footNear = Float.NaN // NaN = neutral ankle
    var footFar = Float.NaN
    var pinJoint = Rig.HIP
    var pinX = 100f
    var pinY = 73f

    fun copyFrom(other: Pose) {
        facing = other.facing
        torso = other.torso
        head = other.head
        spine = other.spine
        shrug = other.shrug
        protract = other.protract
        other.armNear.copyInto(armNear)
        other.armFar.copyInto(armFar)
        other.legNear.copyInto(legNear)
        other.legFar.copyInto(legFar)
        footNear = other.footNear
        footFar = other.footFar
        pinJoint = other.pinJoint
        pinX = other.pinX
        pinY = other.pinY
    }

    fun headAngle(): Float = if (head.isNaN()) torso else head

    fun footAngle(near: Boolean): Float {
        val explicit = if (near) footNear else footFar
        if (!explicit.isNaN()) return explicit
        val shin = if (near) legNear[1] else legFar[1]
        return shin - 90f * facing
    }

    fun setInterpolated(a: Pose, b: Pose, t: Float) {
        facing = if (t < 0.5f) a.facing else b.facing
        torso = lerpAngle(a.torso, b.torso, t)
        head = lerpAngle(a.headAngle(), b.headAngle(), t)
        spine = lerp(a.spine, b.spine, t)
        shrug = lerp(a.shrug, b.shrug, t)
        protract = lerp(a.protract, b.protract, t)
        for (index in 0..1) {
            armNear[index] = lerpAngle(a.armNear[index], b.armNear[index], t)
            armFar[index] = lerpAngle(a.armFar[index], b.armFar[index], t)
            legNear[index] = lerpAngle(a.legNear[index], b.legNear[index], t)
            legFar[index] = lerpAngle(a.legFar[index], b.legFar[index], t)
        }
        footNear = lerpAngle(a.footAngle(true), b.footAngle(true), t)
        footFar = lerpAngle(a.footAngle(false), b.footAngle(false), t)
        pinJoint = a.pinJoint
        pinX = lerp(a.pinX, b.pinX, t)
        pinY = lerp(a.pinY, b.pinY, t)
    }

    /** Applies JSON fields on top of the current values (used for both poses and keyframe overrides). */
    fun apply(json: JSONObject) {
        if (json.has("facing")) facing = if (json.optDouble("facing", 1.0) >= 0) 1f else -1f
        if (json.has("torso")) torso = json.optDouble("torso", 0.0).toFloat()
        if (json.has("head")) head = if (json.isNull("head")) Float.NaN else json.optDouble("head", 0.0).toFloat()
        if (json.has("spine")) spine = json.optDouble("spine", 0.0).toFloat().coerceIn(-12f, 12f)
        if (json.has("shrug")) shrug = json.optDouble("shrug", 0.0).toFloat().coerceIn(-6f, 6f)
        if (json.has("protract")) protract = json.optDouble("protract", 0.0).toFloat().coerceIn(-6f, 6f)
        json.optJSONArray("arms")?.let { pair -> readPair(pair, armNear); readPair(pair, armFar) }
        json.optJSONArray("legs")?.let { pair -> readPair(pair, legNear); readPair(pair, legFar) }
        json.optJSONArray("arm_near")?.let { readPair(it, armNear) }
        json.optJSONArray("arm_far")?.let { readPair(it, armFar) }
        json.optJSONArray("leg_near")?.let { readPair(it, legNear) }
        json.optJSONArray("leg_far")?.let { readPair(it, legFar) }
        if (json.has("feet")) {
            val value = if (json.isNull("feet")) Float.NaN else json.optDouble("feet", 0.0).toFloat()
            footNear = value
            footFar = value
        }
        if (json.has("foot_near")) footNear = if (json.isNull("foot_near")) Float.NaN else json.optDouble("foot_near", 0.0).toFloat()
        if (json.has("foot_far")) footFar = if (json.isNull("foot_far")) Float.NaN else json.optDouble("foot_far", 0.0).toFloat()
        json.optJSONObject("pin")?.let { pin ->
            pinJoint = Rig.jointIndex(pin.optString("joint", "hip"))
            pin.optJSONArray("at")?.let { at ->
                pinX = at.optDouble(0, 100.0).toFloat()
                pinY = at.optDouble(1, 73.0).toFloat()
            }
        }
        json.optJSONArray("hip")?.let { at ->
            pinJoint = Rig.HIP
            pinX = at.optDouble(0, 100.0).toFloat()
            pinY = at.optDouble(1, 73.0).toFloat()
        }
    }

    private fun readPair(array: JSONArray, target: FloatArray) {
        if (array.length() < 2) return
        target[0] = array.optDouble(0, target[0].toDouble()).toFloat()
        target[1] = array.optDouble(1, target[1].toDouble()).toFloat()
    }
}

/** Fixed bone lengths in canvas units plus the forward-kinematics solver. */
internal class Rig(
    val headRadius: Float,
    private val neck: Float,
    private val torso: Float,
    private val upperArm: Float,
    private val forearm: Float,
    private val thigh: Float,
    private val shin: Float,
    private val foot: Float,
) {
    /** Writes joint positions (x, y pairs indexed by the joint constants) into [out]. */
    fun solve(pose: Pose, out: FloatArray, hipOverride: FloatArray?) {
        fun set(joint: Int, x: Float, y: Float) {
            out[joint * 2] = x
            out[joint * 2 + 1] = y
        }
        fun step(joint: Int, fromJoint: Int, angle: Float, length: Float) {
            val radians = Math.toRadians(angle.toDouble())
            set(
                joint,
                out[fromJoint * 2] + sin(radians).toFloat() * length,
                out[fromJoint * 2 + 1] - cos(radians).toFloat() * length,
            )
        }
        set(HIP, 0f, 0f)
        step(NECK, HIP, pose.torso, torso)
        step(HEAD, NECK, pose.headAngle(), neck + headRadius)
        val torsoRadians = Math.toRadians(pose.torso.toDouble())
        val torsoX = sin(torsoRadians).toFloat()
        val torsoY = -cos(torsoRadians).toFloat()
        val frontX = -torsoY * pose.facing
        val frontY = torsoX * pose.facing
        set(
            SHOULDER,
            out[NECK * 2] + torsoX * pose.shrug + frontX * pose.protract,
            out[NECK * 2 + 1] + torsoY * pose.shrug + frontY * pose.protract,
        )
        step(ELBOW_NEAR, SHOULDER, pose.armNear[0], upperArm)
        step(HAND_NEAR, ELBOW_NEAR, pose.armNear[1], forearm)
        step(ELBOW_FAR, SHOULDER, pose.armFar[0], upperArm)
        step(HAND_FAR, ELBOW_FAR, pose.armFar[1], forearm)
        step(KNEE_NEAR, HIP, pose.legNear[0], thigh)
        step(FOOT_NEAR, KNEE_NEAR, pose.legNear[1], shin)
        step(TOE_NEAR, FOOT_NEAR, pose.footAngle(true), foot)
        step(KNEE_FAR, HIP, pose.legFar[0], thigh)
        step(FOOT_FAR, KNEE_FAR, pose.legFar[1], shin)
        step(TOE_FAR, FOOT_FAR, pose.footAngle(false), foot)
        set(HANDS, (out[HAND_NEAR * 2] + out[HAND_FAR * 2]) * 0.5f, (out[HAND_NEAR * 2 + 1] + out[HAND_FAR * 2 + 1]) * 0.5f)
        set(FEET, (out[FOOT_NEAR * 2] + out[FOOT_FAR * 2]) * 0.5f, (out[FOOT_NEAR * 2 + 1] + out[FOOT_FAR * 2 + 1]) * 0.5f)

        val offsetX: Float
        val offsetY: Float
        if (hipOverride != null) {
            offsetX = hipOverride[0]
            offsetY = hipOverride[1]
        } else {
            val anchor = pose.pinJoint.coerceIn(0, JOINT_COUNT - 1)
            offsetX = pose.pinX - out[anchor * 2]
            offsetY = pose.pinY - out[anchor * 2 + 1]
        }
        for (joint in 0 until JOINT_COUNT) {
            out[joint * 2] += offsetX
            out[joint * 2 + 1] += offsetY
        }
    }

    companion object {
        const val HIP = 0
        const val NECK = 1
        const val HEAD = 2
        const val SHOULDER = 3
        const val ELBOW_NEAR = 4
        const val HAND_NEAR = 5
        const val ELBOW_FAR = 6
        const val HAND_FAR = 7
        const val KNEE_NEAR = 8
        const val FOOT_NEAR = 9
        const val TOE_NEAR = 10
        const val KNEE_FAR = 11
        const val FOOT_FAR = 12
        const val TOE_FAR = 13
        const val HANDS = 14
        const val FEET = 15
        const val JOINT_COUNT = 16

        private val names = listOf(
            "hip", "neck", "head", "shoulder", "elbow_near", "hand_near", "elbow_far", "hand_far",
            "knee_near", "foot_near", "toe_near", "knee_far", "foot_far", "toe_far", "hands", "feet",
        )

        fun jointIndex(name: String): Int = names.indexOf(name).takeIf { it >= 0 } ?: HIP

        val DEFAULT = Rig(
            headRadius = 4.5f, neck = 3f, torso = 20f, upperArm = 13.5f,
            forearm = 12.5f, thigh = 16f, shin = 15f, foot = 5f,
        )
    }
}

private class PoseKeyframe(val at: Float, val pose: Pose)

private class MotionTemplate(
    val name: String,
    val durationMs: Long,
    val mode: String,
    val easing: String,
    val reducedMotionFrame: Float,
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

private class ResolvedMotion(
    val template: MotionTemplate,
    val apparatus: List<String>,
)

private data class NameHeuristic(
    val tokens: List<String>,
    val reference: MotionReference,
)

private class AnimationLibrary(
    val palette: Palette,
    val canvasWidth: Float,
    val canvasHeight: Float,
    val rig: Rig,
    val apparatus: Map<String, List<ApparatusPrimitive>>,
    private val templates: Map<String, MotionTemplate>,
    private val movements: Map<String, MotionReference>,
    private val aliases: Map<String, MotionReference>,
    private val categoryDefaults: Map<String, MotionReference>,
    private val equipmentDefaults: Map<String, MotionReference>,
    private val heuristics: List<NameHeuristic>,
    fallbackName: String,
) {
    val fallback: ResolvedMotion = resolveReference(MotionReference(fallbackName, null))
        ?: ResolvedMotion(emergencyTemplate(), listOf("floor"))

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
        val exactAlias = aliases[nameKey]?.let(::resolveReference)
        if (exactAlias != null && nameKey != "compound_exercise") {
            // A specific descriptor ("Hang - Hanging Shoulder Shrugs", "Push Up - Hand Release")
            // refines the generic alias; otherwise the recovered name maps directly.
            matchHeuristic(normalize(descriptor))?.let { return it }
            return exactAlias
        }
        // Compound exercises carry their real content in the set names, so heuristics go first there.
        matchHeuristic(normalize("$exerciseName $descriptor"))?.let { return it }
        exactAlias?.let { return it }

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

    /** Earliest token match in [searchable] wins; ties fall back to heuristic order (most specific first). */
    private fun matchHeuristic(searchable: String): ResolvedMotion? {
        if (searchable.isBlank()) return null
        return heuristics.asSequence()
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
            require(root.optInt("schema_version", 0) == 2) { "Unsupported animation schema" }
            val paletteJson = root.optJSONObject("palette") ?: JSONObject()
            val palette = Palette(
                background = paletteJson.color("background", AppColors.INFO_SURFACE),
                border = paletteJson.color("border", AppColors.DIVIDER),
                figure = paletteJson.color("figure", AppColors.NUMBER_DARK),
                accent = paletteJson.color("accent", AppColors.ACTION_LIGHT),
                apparatus = paletteJson.color("apparatus", AppColors.NUMBER),
                floor = paletteJson.color("floor", 0xFF90A4AE.toInt()),
            )
            val canvas = root.optJSONObject("canvas") ?: JSONObject()
            val segments = root.optJSONObject("figure")?.optJSONObject("segments") ?: JSONObject()
            fun segment(key: String, fallback: Float): Float =
                segments.optDouble(key, fallback.toDouble()).toFloat().coerceIn(0.5f, 60f)
            val rig = Rig(
                headRadius = segment("head_radius", 4.5f),
                neck = segment("neck", 3f),
                torso = segment("torso", 20f),
                upperArm = segment("upper_arm", 13.5f),
                forearm = segment("forearm", 12.5f),
                thigh = segment("thigh", 16f),
                shin = segment("shin", 15f),
                foot = segment("foot", 5f),
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
            val posesJson = root.optJSONObject("poses") ?: JSONObject()
            val poses = HashMap<String, Pose>()
            fun poseNamed(name: String, stack: Set<String>): Pose? {
                poses[name]?.let { return it }
                val json = posesJson.optJSONObject(name) ?: return null
                if (name in stack) return null
                val pose = Pose()
                json.optString("base").takeIf(String::isNotBlank)?.let { baseName ->
                    poseNamed(baseName, stack + name)?.let(pose::copyFrom)
                }
                pose.apply(json)
                poses[name] = pose
                return pose
            }
            val poseKeys = posesJson.keys()
            while (poseKeys.hasNext()) poseNamed(poseKeys.next(), emptySet())

            val templates = root.optJSONObject("templates").mapObjectNotNull { name, value ->
                val json = value as? JSONObject ?: return@mapObjectNotNull null
                val keyframes = json.optJSONArray("keyframes").mapObjectsNotNull { frame ->
                    val pose = Pose()
                    val named = frame.optString("pose").takeIf(String::isNotBlank)?.let { poses[it] }
                    if (named != null) pose.copyFrom(named) else if (frame.has("pose")) return@mapObjectsNotNull null
                    pose.apply(frame)
                    PoseKeyframe(frame.optDouble("at", 0.0).toFloat().coerceIn(0f, 1f), pose)
                }.sortedBy(PoseKeyframe::at)
                if (keyframes.isEmpty()) return@mapObjectNotNull null
                name to MotionTemplate(
                    name = name,
                    durationMs = json.optLong("duration_ms", 1_600L).coerceIn(500L, 8_000L),
                    mode = json.optString("mode", "ping_pong"),
                    easing = json.optString("easing", "smooth"),
                    reducedMotionFrame = json.optDouble("reduced_motion_frame", 0.5).toFloat().coerceIn(0f, 1f),
                    apparatus = json.optJSONArray("apparatus").strings(),
                    keyframes = if (keyframes.size == 1) listOf(
                        keyframes.single(),
                        PoseKeyframe(1f, keyframes.single().pose),
                    ) else keyframes,
                )
            }
            require(templates.isNotEmpty()) { "No animation templates" }
            return AnimationLibrary(
                palette = palette,
                canvasWidth = canvas.optDouble("width", 200.0).toFloat().coerceIn(50f, 1000f),
                canvasHeight = canvas.optDouble("height", 120.0).toFloat().coerceIn(30f, 1000f),
                rig = rig,
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
                canvasWidth = 200f,
                canvasHeight = 120f,
                rig = Rig.DEFAULT,
                apparatus = mapOf(
                    "floor" to listOf(
                        ApparatusPrimitive("line", "floor", floatArrayOf(8f, 106f, 192f, 106f), false),
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
            val stand = Pose().apply {
                pinJoint = Rig.FOOT_NEAR
                pinX = 100f
                pinY = 104f
            }
            val reach = Pose().apply {
                copyFrom(stand)
                armNear[0] = 90f
                armNear[1] = 90f
                armFar[0] = 90f
                armFar[1] = 90f
            }
            return MotionTemplate(
                name = "standing_mobility",
                durationMs = 2_000L,
                mode = "ping_pong",
                easing = "smooth",
                reducedMotionFrame = 0.5f,
                apparatus = listOf("floor"),
                keyframes = listOf(PoseKeyframe(0f, stand), PoseKeyframe(1f, reach)),
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
