package com.deadline.domain

/**
 * Universal headline limit and dynamic scaling engine.
 *
 * Enforces a strict 20-character maximum limit for all note, project, and task
 * headlines across Notes, Tasks, and Plan sections, dynamically scaling visual font
 * sizes to prevent overflow and maintain clean layout consistency.
 */
object HeadlineScaler {
    const val MAX_HEADLINE_LENGTH = 20

    /**
     * Enforces a strict maximum length (default 20 characters) on any headline.
     */
    fun clamp(headline: String, maxLength: Int = MAX_HEADLINE_LENGTH): String {
        return headline.take(maxLength)
    }

    /**
     * Clamps the first line of a multi-line note text to the headline limit (20 chars),
     * leaving subsequent lines unaffected.
     */
    fun clampFirstLine(text: String, maxLength: Int = MAX_HEADLINE_LENGTH): String {
        val lines = text.split("\n", limit = 2)
        val clampedFirstLine = lines[0].take(maxLength)
        return if (lines.size > 1) "$clampedFirstLine\n${lines[1]}" else clampedFirstLine
    }

    /**
     * Computes the dynamic font size scale factor (0.75f to 1.0f) based on length.
     * Length <= 10: 1.0f (Base scale)
     * Length 11..15: 0.86f (Intermediate scale)
     * Length 16..20: 0.75f (Compact scale)
     */
    fun getScaleFactor(text: String): Float {
        val len = clamp(text).length
        return when {
            len <= 10 -> 1.0f
            len <= 15 -> 0.86f
            else -> 0.75f
        }
    }

    /**
     * Calculates a scaled float size based on the given base size and minimum size.
     */
    fun calculateScaledSize(
        text: String,
        baseSize: Float,
        minSize: Float = baseSize * 0.75f
    ): Float {
        val len = clamp(text).length
        return when {
            len <= 10 -> baseSize
            len <= 15 -> (baseSize * 2f + minSize) / 3f
            else -> minSize
        }
    }
}
