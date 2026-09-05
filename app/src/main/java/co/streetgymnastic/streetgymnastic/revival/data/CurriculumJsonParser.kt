package co.streetgymnastic.streetgymnastic.revival.data

import co.streetgymnastic.streetgymnastic.revival.data.model.CatalogSource
import co.streetgymnastic.streetgymnastic.revival.data.model.ContentOrigin
import co.streetgymnastic.streetgymnastic.revival.data.model.CurriculumCatalog
import co.streetgymnastic.streetgymnastic.revival.data.model.LocalizedText
import co.streetgymnastic.streetgymnastic.revival.data.model.TrainingExercise
import co.streetgymnastic.streetgymnastic.revival.data.model.TrainingLevel
import co.streetgymnastic.streetgymnastic.revival.data.model.TrainingProgram
import co.streetgymnastic.streetgymnastic.revival.data.model.TrainingSet
import org.json.JSONArray
import org.json.JSONObject

/**
 * Normalizes both the technical source export and the app's compact static catalog.
 *
 * Workout choices are authored in JSON. Tuple expansion here only turns an authored
 * prescription into the exercise/set objects expected by the UI; it never selects movements,
 * doses, progressions, or variations.
 */
object CurriculumJsonParser {
    fun parse(json: String): CurriculumCatalog = parse(JSONObject(json))

    fun parse(root: JSONObject): CurriculumCatalog {
        val sourceObject = root.optJSONObject("source_baseline")
            ?: root.optJSONObject("source")
            ?: root.optJSONObject("metadata")
        val provenanceObject = root.optJSONObject("catalog_provenance")
        val movementLibrary = parseMovementLibrary(root.optJSONArray("movement_library"))

        return CurriculumCatalog(
            schemaVersion = root.optValueAsString("schema_version") ?: "1",
            source = sourceObject?.let { parseSource(it, provenanceObject) },
            levels = root.optJSONArray("levels").mapObjects { parseLevel(it, movementLibrary) },
        )
    }

    private fun parseSource(json: JSONObject, provenance: JSONObject?): CatalogSource = CatalogSource(
        sourceApkSha256 = json.optValueAsString("apk_sha256")
            ?: json.optValueAsString("source_apk_sha256")
            ?: json.optValueAsString("sha256"),
        generatedAt = json.optValueAsString("generated_at"),
        disclaimer = provenance?.optLocalized("notice")?.resolve()?.ifBlank { null }
            ?: json.optLocalized("disclaimer").resolve().ifBlank { null },
    )

    private fun parseMovementLibrary(array: JSONArray?): Map<String, JSONObject> = buildMap {
        if (array == null) return@buildMap
        for (index in 0 until array.length()) {
            val movement = array.optJSONObject(index) ?: continue
            movement.optValueAsString("id")?.let { put(it, movement) }
        }
    }

    private fun parseLevel(
        json: JSONObject,
        movementLibrary: Map<String, JSONObject>,
    ): TrainingLevel {
        val number = json.optIntOrNull("number") ?: json.optIntOrNull("level") ?: 0
        val summary = json.optJSONObject("summary")
        return TrainingLevel(
            id = json.optValueAsString("id") ?: "sg:l${number.toString().padStart(2, '0')}",
            number = number,
            name = json.optLocalized("name", fallback = "Level $number"),
            description = json.optLocalized("description").ifEmpty(
                summary?.optLocalized("description") ?: LocalizedText.EMPTY,
            ),
            programs = json.optJSONArray("programs").mapObjects {
                parseProgram(it, number, movementLibrary)
            },
        )
    }

    private fun parseProgram(
        json: JSONObject,
        parentLevel: Int,
        movementLibrary: Map<String, JSONObject>,
    ): TrainingProgram {
        val number = json.optIntOrNull("number") ?: 0
        val level = json.optIntOrNull("level") ?: parentLevel
        val programId = json.optValueAsString("id")
            ?: "sg:l${level.toString().padStart(2, '0')}:p${number.toString().padStart(4, '0')}"
        val sections = json.optJSONObject("sections")
        val content = json.optJSONObject("training_content") ?: json.optJSONObject("content")
        val schedule = json.optJSONObject("schedule")
        val coachingOverlay = json.optJSONObject("coaching_overlay")
        val legacy = json.optJSONObject("legacy")

        val morningRecovery = json.optBoolean("morning_recovery", false)
        val mainExercises = when {
            morningRecovery -> parseCompactSteps(json.optJSONArray("main"), programId, "main", movementLibrary)
            json.optJSONArray("training_main") != null ->
                parseCompactSteps(json.optJSONArray("training_main"), programId, "main", movementLibrary)
            content?.optJSONArray("exercises") != null ->
                content.optJSONArray("exercises").mapObjects(::parseExpandedExercise)
            json.optJSONArray("exercises") != null ->
                json.optJSONArray("exercises").mapObjects(::parseExpandedExercise)
            json.optJSONArray("main") != null ->
                parseCompactSteps(json.optJSONArray("main"), programId, "main", movementLibrary)
            sections?.optJSONArray("main") != null ->
                parseFlexibleSection(sections.optJSONArray("main"), programId, "main", movementLibrary)
            else -> emptyList()
        }
        val warmupArray = coachingOverlay?.optJSONArray("warmup")
            ?: json.optJSONArray("warmup")
            ?: sections?.optJSONArray("warmup")
        val cooldownArray = coachingOverlay?.optJSONArray("cooldown")
            ?: json.optJSONArray("cooldown")
            ?: sections?.optJSONArray("cooldown")

        return TrainingProgram(
            id = programId,
            number = number,
            levelNumber = level,
            name = json.optLocalized("title").ifEmpty(
                json.optLocalized("name", fallback = "Workout $number"),
            ),
            description = json.optLocalized("description"),
            origin = ContentOrigin.fromWireValue(json.optOriginValue()),
            hasDetails = mainExercises.isNotEmpty(),
            proPlaceholder = legacy?.optBoolean("pro", false) == true && mainExercises.isEmpty(),
            week = schedule?.optIntOrNull("week")
                ?: json.optIntOrNull("week")
                ?: json.optIntOrNull("week_number"),
            day = schedule?.optIntOrNull("day")
                ?: json.optIntOrNull("day")
                ?: json.optIntOrNull("day_number"),
            estimatedMinutes = if (morningRecovery) 20 else schedule?.optIntOrNull("minutes")
                ?: json.optIntOrNull("estimated_minutes"),
            exercises = mainExercises,
            readiness = json.optLocalized("readiness"),
            safety = json.optLocalized("safety"),
            targetRpe = if (morningRecovery) 3 else json.optIntOrNull("training_rpe") ?: (schedule?.optIntOrNull("rpe")
                ?: json.optIntOrNull("target_rpe"))?.takeIf { it in 1..10 },
            dayType = schedule?.optValueAsString("day_type")
                ?: json.optValueAsString("day_type"),
            focusTags = (schedule?.optJSONArray("focus")
                ?: json.optJSONArray("focus")
                ?: json.optJSONArray("focus_tags")).mapStrings(),
            practice = parseCompactSteps(json.optJSONArray("practice"), programId, "practice", movementLibrary),
            warmup = parseFlexibleSection(warmupArray, programId, "warmup", movementLibrary),
            cooldown = parseFlexibleSection(cooldownArray, programId, "cooldown", movementLibrary),
        )
    }

    private fun parseFlexibleSection(
        array: JSONArray?,
        programId: String,
        section: String,
        movementLibrary: Map<String, JSONObject>,
    ): List<TrainingExercise> {
        if (array == null || array.length() == 0) return emptyList()
        return when (array.opt(0)) {
            is JSONArray -> parseCompactSteps(array, programId, section, movementLibrary)
            is JSONObject -> array.mapObjects(::parseExpandedExercise)
            else -> emptyList()
        }
    }

    private fun parseCompactSteps(
        array: JSONArray?,
        programId: String,
        section: String,
        movementLibrary: Map<String, JSONObject>,
    ): List<TrainingExercise> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val tuple = array.optJSONArray(index) ?: continue
                val movementId = tuple.optNullableString(0) ?: continue
                val movement = movementLibrary[movementId]
                val exerciseNumber = index + 1
                val exerciseId = "$programId:$section:e${exerciseNumber.toString().padStart(2, '0')}"
                val setCount = tuple.optInt(1, 1).coerceAtLeast(1)
                val repetitionsValue = tuple.optNullable(2)
                val repetitionNumber = (repetitionsValue as? Number)?.toInt()
                val repetitionText = (repetitionsValue as? String)?.takeIf(String::isNotBlank)
                val repetitionSeries = parseDoseSeries(repetitionText, setCount)
                val secondsValue = tuple.optNullable(3)
                val secondsNumber = (secondsValue as? Number)?.toInt()
                val secondsText = (secondsValue as? String)?.takeIf(String::isNotBlank)
                val secondsSeries = parseDoseSeries(secondsText, setCount)
                val restSeconds = tuple.optNullableInt(4)
                val tempo = tuple.optNullableString(5)
                val regressionId = tuple.optNullableString(6)
                    ?: movement?.optValueAsString("regression_id")
                val regression = regressionId?.let(movementLibrary::get)
                val description = movementDescription(movement, regression)

                add(
                    TrainingExercise(
                        id = exerciseId,
                        movementId = movementId,
                        number = exerciseNumber,
                        name = movement?.optLocalized("name")
                            ?: LocalizedText(en = movementId.humanize()),
                        description = description,
                        sets = List(setCount) { setIndex ->
                            val repetitionNote = when {
                                repetitionText.equals("max", ignoreCase = true) -> null
                                repetitionSeries?.perSide == true -> "per side"
                                repetitionSeries != null -> null
                                else -> formatRepetitionNote(repetitionText)
                            }
                            val durationNote = when {
                                secondsSeries?.perSide == true -> "per side"
                                secondsSeries != null -> null
                                secondsText != null -> "Duration $secondsText"
                                else -> null
                            }
                            TrainingSet(
                                id = "$exerciseId:s${(setIndex + 1).toString().padStart(2, '0')}",
                                number = setIndex + 1,
                                name = LocalizedText.EMPTY,
                                description = compactSetDescription(
                                    repetitionNote,
                                    durationNote,
                                    tempo,
                                ),
                                repetitions = repetitionNumber
                                    ?: repetitionSeries?.values?.get(setIndex),
                                repetitionsMax = repetitionText.equals("max", ignoreCase = true),
                                durationSeconds = secondsNumber
                                    ?: secondsSeries?.values?.get(setIndex),
                                breakSeconds = restSeconds,
                            )
                        },
                        category = movement?.optValueAsString("category"),
                        equipment = movement?.optJSONArray("equipment").mapStrings(),
                    ),
                )
            }
        }
    }

    private fun parseExpandedExercise(json: JSONObject): TrainingExercise = TrainingExercise(
        id = json.optValueAsString("id") ?: "exercise-${json.optInt("number", 0)}",
        movementId = json.optValueAsString("movement_id")
            ?: json.optValueAsString("animation_id"),
        number = json.optInt("number", 0),
        name = json.optLocalized("name"),
        description = json.optLocalized("description"),
        sets = json.optJSONArray("sets").mapObjects(::parseExpandedSet),
        category = json.optValueAsString("category"),
        equipment = json.optJSONArray("equipment").mapStrings(),
    )

    private fun parseExpandedSet(json: JSONObject): TrainingSet = TrainingSet(
        id = json.optValueAsString("id") ?: "set-${json.optInt("number", 0)}",
        number = json.optInt("number", 0),
        name = json.optLocalized("name"),
        description = json.optLocalized("description"),
        repetitions = json.optIntOrNull("repetitions"),
        repetitionsMax = json.optBoolean("repetitions_max", false),
        durationSeconds = json.optIntOrNull("duration_seconds"),
        breakSeconds = json.optIntOrNull("break_seconds"),
    )

    private fun movementDescription(
        movement: JSONObject?,
        regression: JSONObject?,
    ): LocalizedText {
        if (movement == null) return LocalizedText.EMPTY
        val base = movement.optLocalized("description")
        val safety = movement.optLocalized("safety")
        val regressionName = regression?.optLocalized("name") ?: LocalizedText.EMPTY
        return LocalizedText(
            en = joinNotes(
                base.en,
                safety.en?.let { "Safety: $it" },
                regressionName.en?.let { "Regression: $it" },
            ),
        )
    }

    private fun compactSetDescription(
        repetitionNote: String?,
        durationNote: String?,
        tempo: String?,
    ): LocalizedText {
        val tempoNote = tempo?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        return LocalizedText(
            en = joinNotes(repetitionNote, durationNote, tempoNote?.let { raw ->
                if (Regex("[0-9Xx]{4}").matches(raw)) {
                    val lift = if (raw[2].equals('X', ignoreCase = true)) "brisk lift" else "${raw[2]}s lift"
                    "${raw[0]}s lower, ${raw[1]}s pause, $lift, ${raw[3]}s reset"
                } else if (raw.equals("isometric", ignoreCase = true)) {
                    "Hold steady and breathe"
                } else {
                    raw.replace('_', ' ')
                }
            }),
        )
    }

    /**
     * Accepts an integer, an N-N-... ladder with exactly one item per set, and either form
     * followed by /side. Other authored notation remains visible as descriptive text.
     */
    private fun parseDoseSeries(raw: String?, setCount: Int): CompactDoseSeries? {
        if (raw.isNullOrBlank() || setCount <= 0) return null
        val match = Regex("^(\\d+(?:-\\d+)*)(/side)?$", RegexOption.IGNORE_CASE)
            .matchEntire(raw.trim()) ?: return null
        val parsed = match.groupValues[1].split('-').map { token ->
            token.toIntOrNull() ?: return null
        }
        if (parsed.size != 1 && parsed.size != setCount) return null
        return CompactDoseSeries(
            values = if (parsed.size == 1) List(setCount) { parsed.single() } else parsed,
            perSide = match.groupValues[2].isNotEmpty(),
        )
    }

    private data class CompactDoseSeries(
        val values: List<Int>,
        val perSide: Boolean,
    )

    /**
     * Plus-separated values are clusters within every set, not per-set ladders. Keep that
     * prescription intact while making the unit and supported side qualifier explicit.
     */
    private fun formatRepetitionNote(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("^([0-9]+(?:[+][0-9]+)+)(/side(?:-focus)?)?$", RegexOption.IGNORE_CASE)
            .matchEntire(raw.trim()) ?: return raw
        val cluster = match.groupValues[1].replace("+", " + ")
        val qualifier = when (match.groupValues[2].lowercase()) {
            "/side" -> " per side"
            "/side-focus" -> " on the focus side"
            else -> ""
        }
        return "$cluster reps$qualifier"
    }

    private fun joinNotes(vararg values: String?): String? = values
        .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        .distinct()
        .joinToString("; ")
        .ifBlank { null }

    private fun String.humanize(): String = split('_')
        .joinToString(" ") { token -> token.replaceFirstChar { it.titlecase() } }

    private fun JSONObject.optOriginValue(): String? = when (val origin = opt("origin")) {
        is JSONObject -> origin.optValueAsString("type")
        else -> origin?.takeUnless { it == JSONObject.NULL }?.toString()
    }

    private fun JSONObject.optLocalized(key: String, fallback: String? = null): LocalizedText {
        return when (val value = opt(key)) {
            is JSONObject -> LocalizedText(en = value.optValueAsString("en"))
            is String -> LocalizedText(en = value)
            else -> LocalizedText(en = fallback)
        }
    }

    private fun LocalizedText.ifEmpty(fallback: LocalizedText): LocalizedText =
        if (en.isNullOrBlank()) fallback else this

    private fun JSONObject.optValueAsString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return opt(key)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun JSONArray.optNullable(index: Int): Any? =
        opt(index)?.takeUnless { it == JSONObject.NULL }

    private fun JSONArray.optNullableString(index: Int): String? =
        optNullable(index)?.toString()?.takeIf { it.isNotBlank() && it != "null" }

    private fun JSONArray.optNullableInt(index: Int): Int? = when (val value = optNullable(index)) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    private inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) {
                optJSONObject(index)?.let { add(transform(it)) }
            }
        }
    }

    private fun JSONArray?.mapStrings(): List<String> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) {
                optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }
}
