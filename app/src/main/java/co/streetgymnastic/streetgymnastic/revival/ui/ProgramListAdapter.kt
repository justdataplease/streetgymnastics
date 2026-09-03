package co.streetgymnastic.streetgymnastic.revival.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import co.streetgymnastic.streetgymnastic.revival.R
import co.streetgymnastic.streetgymnastic.revival.data.model.TrainingProgram
import java.util.Locale

class ProgramListAdapter(
    private val context: Context,
    private val programs: List<TrainingProgram>,
    private val completedIds: Set<String>,
    private val assignedProgramId: String?,
    private val locale: Locale,
) : BaseAdapter() {
    override fun getCount(): Int = programs.size

    override fun getItem(position: Int): TrainingProgram = programs[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val program = getItem(position)
        val title = context.displayName(program, locale)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(14), context.dp(14), context.dp(14), context.dp(14))
            background = roundedDrawable(AppColors.SURFACE, context.dp(9).toFloat())
            elevation = context.dp(1).toFloat()
            isFocusable = false
            contentDescription = buildString {
                append(title)
                val meta = context.programMeta(program)
                if (meta.isNotBlank()) append(", ").append(meta)
                if (program.id in completedIds) append(", ").append(context.getString(R.string.completed))
                else if (program.id == assignedProgramId) append(", ").append(context.getString(R.string.assigned_next))
                else append(", ").append(context.getString(R.string.locked_later))
            }
        }

        row.addView(
            context.numberCircle(
                program.number.toString(),
                context.getString(R.string.workout_number, program.number),
            ),
            LinearLayout.LayoutParams(context.dp(44), context.dp(44)),
        )

        val copy = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(14), 0, context.dp(8), 0)
            addView(context.oneLineText(title, bold = true))

            val meta = context.programMeta(program).ifBlank {
                program.description.resolve(locale).ifBlank {
                    context.getString(R.string.empty_description)
                }
            }
            addView(context.bodyText(meta, 13f).apply { maxLines = 2 })
        }
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (program.id in completedIds) {
            row.addView(
                TextView(context).apply {
                    text = "✓"
                    setTextColor(AppColors.SUCCESS)
                    textSize = 22f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(context.dp(36), context.dp(44)),
            )
        } else if (program.id == assignedProgramId) {
            row.addView(context.badge(context.getString(R.string.assigned_next), AppColors.ACTION))
        } else {
            row.addView(context.badge(context.getString(R.string.locked_later), AppColors.TEXT_SECONDARY))
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(12), context.dp(4), context.dp(12), context.dp(4))
            addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }
}
