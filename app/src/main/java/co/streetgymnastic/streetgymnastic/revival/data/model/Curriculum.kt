package co.streetgymnastic.streetgymnastic.revival.data.model

import java.util.Locale

data class CurriculumCatalog(
    val schemaVersion: String,
    val source: CatalogSource?,
    val levels: List<TrainingLevel>,
)

data class CatalogSource(
    val sourceApkSha256: String?,
    val generatedAt: String?,
    val disclaimer: String?,
)

data class TrainingLevel(
    val id: String,
    val number: Int,
    val name: LocalizedText,
    val description: LocalizedText,
    val programs: List<TrainingProgram>,
)

data class TrainingProgram(
    val id: String,
    val number: Int,
    val levelNumber: Int,
    val name: LocalizedText,
    val description: LocalizedText,
    val origin: ContentOrigin,
    val hasDetails: Boolean,
    val proPlaceholder: Boolean,
    val week: Int?,
    val day: Int?,
    val estimatedMinutes: Int?,
    val exercises: List<TrainingExercise>,
    val readiness: LocalizedText = LocalizedText.EMPTY,
    val safety: LocalizedText = LocalizedText.EMPTY,
    val targetRpe: Int? = null,
    val dayType: String? = null,
    val focusTags: List<String> = emptyList(),
    val practice: List<TrainingExercise> = emptyList(),
    val warmup: List<TrainingExercise> = emptyList(),
    val cooldown: List<TrainingExercise> = emptyList(),
)

data class TrainingExercise(
    val id: String,
    val movementId: String? = null,
    val number: Int,
    val name: LocalizedText,
    val description: LocalizedText,
    val sets: List<TrainingSet>,
    val category: String? = null,
    val equipment: List<String> = emptyList(),
)

data class TrainingSet(
    val id: String,
    val number: Int,
    val name: LocalizedText,
    val description: LocalizedText,
    val repetitions: Int?,
    val repetitionsMax: Boolean,
    val durationSeconds: Int?,
    val breakSeconds: Int?,
)

enum class ContentOrigin(val wireValue: String) {
    APK_AUTHENTIC("apk_authentic"),
    RECONSTRUCTED_PLACEHOLDER("reconstructed_placeholder"),
    LLM_EXTENSION("llm_extension"),
    NEW_LEVEL("new_level"),
    UNKNOWN("unknown");

    companion object {
        fun fromWireValue(value: String?): ContentOrigin =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

data class LocalizedText(
    val en: String? = null,
) {
    @Suppress("UNUSED_PARAMETER")
    fun resolve(locale: Locale = Locale.ENGLISH): String =
        en?.takeIf(String::isNotBlank).orEmpty()

    companion object {
        val EMPTY = LocalizedText()
    }
}
