package co.streetgymnastic.streetgymnastic.revival.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import co.streetgymnastic.streetgymnastic.revival.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A schematic of court spacing, not a model of shooting or dribbling technique. */
class BasketballCourtView(context: Context, private val category: String) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        contentDescription = context.getString(R.string.basketball_court_description)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width / 320f, height / 170f)
        canvas.save()
        canvas.translate((width - 320f * scale) / 2f, (height - 170f * scale) / 2f)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFE5F1EA.toInt()
        canvas.drawRect(6f, 6f, 314f, 164f, paint)
        paint.color = 0xFFBCD9C7.toInt()
        canvas.drawRect(116f, 6f, 204f, 78f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = 0xFF708C7B.toInt()
        canvas.drawRect(6f, 6f, 314f, 164f, paint)
        canvas.drawRect(116f, 6f, 204f, 78f, paint)
        canvas.drawArc(116f, 48f, 204f, 108f, 0f, 180f, false, paint)
        canvas.drawArc(34f, -88f, 286f, 148f, 0f, 180f, false, paint)
        canvas.drawLine(34f, 6f, 34f, 30f, paint)
        canvas.drawLine(286f, 6f, 286f, 30f, paint)
        paint.strokeWidth = 3f
        canvas.drawLine(145f, 18f, 175f, 18f, paint)
        paint.color = 0xFFB74E25.toInt()
        canvas.drawCircle(160f, 26f, 7f, paint)

        when (category) {
            "shooting" -> {
                player(canvas, 108f, 88f)
                arrow(canvas, 116f, 81f, 151f, 37f)
                ball(canvas, 124f, 66f)
            }
            "finishing" -> {
                player(canvas, 245f, 119f)
                arrow(canvas, 233f, 109f, 181f, 43f)
                ball(canvas, 238f, 99f)
            }
            "passing" -> {
                player(canvas, 76f, 110f)
                player(canvas, 242f, 100f)
                arrow(canvas, 91f, 109f, 226f, 101f)
                ball(canvas, 158f, 105f)
            }
            "defense" -> {
                player(canvas, 220f, 124f)
                player(canvas, 198f, 89f, defense = true)
                arrow(canvas, 184f, 86f, 143f, 86f)
                arrow(canvas, 140f, 90f, 181f, 90f)
                ball(canvas, 232f, 118f)
            }
            "overview" -> {
                player(canvas, 205f, 128f)
                player(canvas, 187f, 100f, defense = true)
                arrow(canvas, 214f, 111f, 223f, 78f)
                arrow(canvas, 220f, 70f, 181f, 40f)
                ball(canvas, 220f, 131f)
            }
            "decision" -> {
                player(canvas, 74f, 103f)
                player(canvas, 160f, 137f)
                player(canvas, 248f, 102f)
                player(canvas, 104f, 77f, defense = true)
                player(canvas, 167f, 99f, defense = true)
                player(canvas, 219f, 76f, defense = true)
                arrow(canvas, 147f, 134f, 91f, 110f)
                arrow(canvas, 239f, 91f, 190f, 43f)
                ball(canvas, 175f, 140f)
            }
            "dribbling" -> {
                player(canvas, 78f, 127f)
                arrow(canvas, 91f, 119f, 129f, 81f)
                arrow(canvas, 144f, 83f, 179f, 119f)
                arrow(canvas, 194f, 119f, 237f, 76f)
                ball(canvas, 88f, 140f)
            }
            else -> {
                player(canvas, 160f, 112f)
                arrow(canvas, 137f, 113f, 97f, 113f)
                arrow(canvas, 183f, 113f, 223f, 113f)
            }
        }
        canvas.restore()
    }

    private fun player(canvas: Canvas, x: Float, y: Float, defense: Boolean = false) {
        paint.style = Paint.Style.FILL
        paint.color = if (defense) AppColors.ACTION else AppColors.NUMBER
        canvas.drawCircle(x, y, 10f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.WHITE
        if (defense) {
            canvas.drawLine(x - 3f, y - 3f, x + 3f, y + 3f, paint)
            canvas.drawLine(x + 3f, y - 3f, x - 3f, y + 3f, paint)
        } else canvas.drawCircle(x, y, 3f, paint)
    }

    private fun ball(canvas: Canvas, x: Float, y: Float) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFFF4B43C.toInt()
        canvas.drawCircle(x, y, 6f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = 0xFF714B17.toInt()
        canvas.drawCircle(x, y, 6f, paint)
        canvas.drawLine(x - 6f, y, x + 6f, y, paint)
        canvas.drawLine(x, y - 6f, x, y + 6f, paint)
    }

    private fun arrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = AppColors.NUMBER
        canvas.drawLine(x1, y1, x2, y2, paint)
        val angle = atan2(y2 - y1, x2 - x1)
        val path = Path().apply {
            moveTo(x2 - 7f * cos(angle - 0.5f), y2 - 7f * sin(angle - 0.5f))
            lineTo(x2, y2)
            lineTo(x2 - 7f * cos(angle + 0.5f), y2 - 7f * sin(angle + 0.5f))
        }
        canvas.drawPath(path, paint)
    }
}
