package co.streetgymnastic.streetgymnastic.revival.data

import android.content.Context
import co.streetgymnastic.streetgymnastic.revival.data.progress.SharedPreferencesProgressRepository

/** Keep history and unchanged ticks; revised drill slots start unchecked on the 1.3 update. */
object BasketballPlanMigration {
    private val revisedSlots = mapOf(
        "bb:l1:p05" to setOf(2),
        "bb:l1:p07" to setOf(3),
        "bb:l2:p01" to setOf(1),
        "bb:l2:p03" to setOf(3),
        "bb:l2:p07" to setOf(2),
        "bb:l3:p01" to setOf(1, 2),
        "bb:l3:p02" to setOf(1),
        "bb:l3:p07" to setOf(1),
        "bb:l4:p02" to setOf(1),
        "bb:l4:p05" to setOf(1),
        "bb:l4:p06" to setOf(3),
        "bb:l4:p07" to setOf(2),
        "bb:l5:p01" to setOf(1),
        "bb:l5:p02" to setOf(1),
        "bb:l5:p05" to setOf(3),
        "bb:l5:p07" to setOf(1),
        "bb:l6:p01" to setOf(3),
        "bb:l6:p02" to setOf(4),
        "bb:l6:p03" to setOf(3),
        "bb:l6:p05" to setOf(4),
        "bb:l6:p06" to setOf(3)
    )

    fun apply(context: Context, repository: SharedPreferencesProgressRepository) {
        val preferences = context.getSharedPreferences("street_basketball_plan", Context.MODE_PRIVATE)
        if (preferences.getInt("revision", 0) >= 2) return
        repository.activeWorkout()?.let { active ->
            val prefixes = revisedSlots[active.programId].orEmpty().map {
                "${active.programId}:main:e${it.toString().padStart(2, '0')}:"
            }
            active.completedSetIds.filter { id -> prefixes.any(id::startsWith) }.forEach {
                repository.setCompleted(it, false)
            }
        }
        preferences.edit().putInt("revision", 2).apply()
    }
}
