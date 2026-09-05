package co.streetgymnastic.streetgymnastic.revival.ui

import android.content.Context
import co.streetgymnastic.streetgymnastic.revival.R
import co.streetgymnastic.streetgymnastic.revival.data.model.TrainingExercise
import co.streetgymnastic.streetgymnastic.revival.data.model.TrainingLevel
import co.streetgymnastic.streetgymnastic.revival.data.model.TrainingProgram
import co.streetgymnastic.streetgymnastic.revival.data.model.TrainingSet
import java.util.Locale

fun Context.displayName(level: TrainingLevel, locale: Locale): String {
    val value = level.name.resolve(locale)
    return if (value.isBlank() || value == "Level ${level.number}") {
        getString(R.string.level_number, level.number)
    } else {
        value
    }
}

fun Context.displayName(program: TrainingProgram, locale: Locale): String {
    val value = program.name.resolve(locale)
    return if (value.isBlank() || value == "Workout ${program.number}") {
        getString(R.string.workout_number, program.number)
    } else {
        value
    }
}

fun Context.displayName(exercise: TrainingExercise, locale: Locale): String =
    exercise.name.resolve(locale).ifBlank { getString(R.string.exercise_number, exercise.number) }

fun Context.programMeta(program: TrainingProgram): String = buildList {
    add(getString(
        if (program.id.startsWith("bb:")) R.string.basketball_session_number
        else R.string.workout_sequence_position,
        program.number,
    ))
    program.estimatedMinutes?.let { add(getString(R.string.minutes_format, it)) }
    program.targetRpe?.let { add(getString(R.string.effort_item, it)) }
}.joinToString(" • ")

fun Context.setPrescription(set: TrainingSet, locale: Locale): String = buildList {
    set.name.resolve(locale).takeIf(String::isNotBlank)?.let(::add)
    when {
        set.repetitionsMax -> add(getString(R.string.maximum_repetitions))
        set.repetitions != null -> add(getString(R.string.repetitions_format, set.repetitions))
        set.durationSeconds != null -> add(getString(R.string.seconds_format, set.durationSeconds))
    }
    set.description.resolve(locale).takeIf(String::isNotBlank)?.let(::add)
    set.breakSeconds?.takeIf { it > 0 }?.let { add(getString(R.string.rest_format, it)) }
}.joinToString(" • ").ifBlank { getString(R.string.set_number, set.number) }
