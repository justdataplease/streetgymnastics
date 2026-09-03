package co.streetgymnastic.streetgymnastic.revival.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Space
import android.widget.TextView

object AppColors {
    const val PRIMARY = 0xFF2196F3.toInt()
    const val PRIMARY_DARK = 0xFF1976D2.toInt()
    const val NUMBER = 0xFF1565C0.toInt()
    const val NUMBER_DARK = 0xFF0D47A1.toInt()
    const val ACTION = 0xFFB71C1C.toInt()
    const val ACTION_LIGHT = 0xFFEF5350.toInt()
    const val BACKGROUND = 0xFFF3F3F3.toInt()
    const val SURFACE = Color.WHITE
    const val TEXT = 0xFF212121.toInt()
    const val TEXT_SECONDARY = 0xFF666666.toInt()
    const val DIVIDER = 0xFFE0E0E0.toInt()
    const val SUCCESS = 0xFF2E7D32.toInt()
    const val WARNING_SURFACE = 0xFFFFF3E0.toInt()
    const val INFO_SURFACE = 0xFFE3F2FD.toInt()
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

fun roundedDrawable(
    color: Int,
    radiusPx: Float,
    strokeColor: Int? = null,
    strokeWidthPx: Int = 0,
): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    setColor(color)
    cornerRadius = radiusPx
    if (strokeColor != null && strokeWidthPx > 0) setStroke(strokeWidthPx, strokeColor)
}

fun Context.verticalPage(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(16), dp(16), dp(16), dp(28))
}

fun Context.card(clickable: Boolean = false): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(16), dp(16), dp(16), dp(16))
    background = roundedDrawable(AppColors.SURFACE, dp(10).toFloat())
    elevation = dp(2).toFloat()
    if (clickable) {
        isClickable = true
        isFocusable = true
        foreground = selectableForeground()
    }
}

fun Context.titleText(text: CharSequence, sizeSp: Float = 22f): TextView = TextView(this).apply {
    this.text = text
    setTextColor(AppColors.TEXT)
    textSize = sizeSp
    typeface = Typeface.DEFAULT_BOLD
}

fun Context.bodyText(text: CharSequence, sizeSp: Float = 15f): TextView = TextView(this).apply {
    this.text = text
    setTextColor(AppColors.TEXT_SECONDARY)
    textSize = sizeSp
    setLineSpacing(0f, 1.12f)
}

fun Context.sectionLabel(text: CharSequence): TextView = TextView(this).apply {
    this.text = text.toString().uppercase()
    setTextColor(AppColors.NUMBER_DARK)
    textSize = 13f
    typeface = Typeface.DEFAULT_BOLD
    letterSpacing = 0.08f
    setPadding(0, dp(8), 0, dp(8))
}

fun Context.numberCircle(number: String, contentDescriptionText: String): TextView = TextView(this).apply {
    text = number
    gravity = Gravity.CENTER
    setTextColor(Color.WHITE)
    textSize = 16f
    typeface = Typeface.DEFAULT_BOLD
    background = roundedDrawable(AppColors.NUMBER, dp(22).toFloat())
    contentDescription = contentDescriptionText
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
}

fun Context.badge(text: CharSequence, color: Int = AppColors.NUMBER): TextView = TextView(this).apply {
    this.text = text.toString().uppercase()
    setTextColor(Color.WHITE)
    textSize = 11f
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER
    letterSpacing = 0.06f
    setPadding(dp(9), dp(4), dp(9), dp(4))
    background = roundedDrawable(color, dp(12).toFloat())
}

fun Context.primaryButton(
    text: CharSequence,
    color: Int = AppColors.ACTION,
    onClick: () -> Unit,
): Button = Button(this).apply {
    this.text = text
    setTextColor(Color.WHITE)
    textSize = 14f
    typeface = Typeface.DEFAULT_BOLD
    isAllCaps = false
    minHeight = dp(48)
    backgroundTintList = ColorStateList.valueOf(color)
    setOnClickListener { onClick() }
}

fun Context.secondaryButton(text: CharSequence, onClick: () -> Unit): Button = Button(this).apply {
    this.text = text
    setTextColor(AppColors.NUMBER_DARK)
    textSize = 14f
    typeface = Typeface.DEFAULT_BOLD
    isAllCaps = false
    minHeight = dp(48)
    backgroundTintList = ColorStateList.valueOf(Color.WHITE)
    setOnClickListener { onClick() }
}

fun Context.roundIconButton(
    iconRes: Int,
    description: CharSequence,
    color: Int = AppColors.ACTION,
    onClick: () -> Unit,
): ImageButton = ImageButton(this).apply {
    setImageResource(iconRes)
    contentDescription = description
    background = roundedDrawable(color, dp(28).toFloat())
    setPadding(dp(16), dp(16), dp(16), dp(16))
    elevation = dp(6).toFloat()
    isFocusable = true
    setOnClickListener { onClick() }
}

fun Context.horizontalProgress(percent: Int): ProgressBar = ProgressBar(
    this,
    null,
    android.R.attr.progressBarStyleHorizontal,
).apply {
    max = 100
    progress = percent.coerceIn(0, 100)
    progressTintList = ColorStateList.valueOf(AppColors.PRIMARY)
    progressBackgroundTintList = ColorStateList.valueOf(0xFFDCEAF5.toInt())
    minimumHeight = dp(8)
}

fun Context.spacer(heightDp: Int): Space = Space(this).apply {
    layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
}

fun ViewGroup.addWithMargins(
    view: View,
    width: Int = ViewGroup.LayoutParams.MATCH_PARENT,
    height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    leftDp: Int = 0,
    topDp: Int = 0,
    rightDp: Int = 0,
    bottomDp: Int = 0,
) {
    val context = view.context
    addView(
        view,
        LinearLayout.LayoutParams(width, height).apply {
            setMargins(context.dp(leftDp), context.dp(topDp), context.dp(rightDp), context.dp(bottomDp))
        },
    )
}

fun Context.oneLineText(text: CharSequence, bold: Boolean = false): TextView = TextView(this).apply {
    this.text = text
    setTextColor(AppColors.TEXT)
    textSize = 16f
    maxLines = 1
    ellipsize = TextUtils.TruncateAt.END
    if (bold) typeface = Typeface.DEFAULT_BOLD
}

private fun Context.selectableForeground() = TypedValue().let { value ->
    theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
    getDrawable(value.resourceId)
}
