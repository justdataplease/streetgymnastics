package co.streetgymnastic.streetgymnastic.revival.data.progress

data class WorkoutSession(
    val programId: String,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long,
    val durationSeconds: Long,
    val effortRating: Int? = null,
)

data class ActiveWorkout(
    val programId: String,
    val startedAtEpochMillis: Long,
    val completedSetIds: Set<String> = emptySet(),
)

interface ProgressRepository {
    fun completedProgramIds(): Set<String>
    fun isProgramCompleted(programId: String): Boolean
    fun sessionHistory(): List<WorkoutSession>
    fun activeWorkout(): ActiveWorkout?
    fun startWorkout(programId: String, startedAtEpochMillis: Long = System.currentTimeMillis())
    fun setCompleted(setId: String, completed: Boolean)
    fun finishWorkout(
        completedAtEpochMillis: Long = System.currentTimeMillis(),
        effortRating: Int? = null,
    ): WorkoutSession?
    fun abandonWorkout()
    fun markProgramIncomplete(programId: String)
}
