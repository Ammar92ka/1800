package com.ottoman.empire1800.models

/** مستوى الصعوبة: يضبط وفرة الإيراد وتكرار الكوارث والمؤامرات والحروب وشدة القرارات الخاسرة. */
data class Difficulty(val level: Int) {
    val safeLevel: Int get() = level.coerceIn(0, 2)
    val name: String get() = names[safeLevel]

    /** مضاعف الإيراد المحصل. */
    val revenue: Double get() = listOf(1.06, 1.0, 0.94)[safeLevel]

    /** مضاعف احتمال الكوارث والمؤامرات والحروب والانفصال. */
    val events: Double get() = listOf(0.7, 1.0, 1.3)[safeLevel]

    /** مضاعف الآثار السلبية للقرارات (أثر سالب × هذا). */
    val losses: Double get() = listOf(0.8, 1.0, 1.25)[safeLevel]

    companion object {
        val names: List<String> = listOf("سهل", "عادي", "صعب")

        val descriptions: List<String> = listOf(
            "إيراد أوفر بـ6%، وأحداث أقل، وخسائر القرارات أخف.",
            "التوازن الأساسي للعبة.",
            "إيراد أقل بـ6%، وأحداث أكثر، وخسائر القرارات أثقل.",
        )
    }
}
