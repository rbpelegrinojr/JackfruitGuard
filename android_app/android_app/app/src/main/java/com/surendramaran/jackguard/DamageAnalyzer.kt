package com.surendramaran.jackguard

/**
 * Pure business-logic helper used by MainActivity for jackfruit disease assessment.
 * Extracted into its own class so that the logic can be verified by local JVM unit tests
 * without requiring an Android device or emulator.
 */
object DamageAnalyzer {

    /** Human-readable display names for each model label. */
    val labelDisplay: Map<String, String> = mapOf(
        "Healthy_Jackfruit" to "Healthy Jackfruit",
        "R10" to "Rhizopus Damage (R10)",
        "R25" to "Rhizopus Damage (R25)",
        "R50" to "Rhizopus Damage (R50)",
        "R100" to "Rhizopus Damage (R100)"
    )

    /**
     * Returns the numeric damage percentage that corresponds to a raw model label.
     * Used in both iterations to convert a detection result into a quantitative value.
     */
    fun damagePercentFromLabel(label: String): Int = when (label) {
        "Healthy_Jackfruit" -> 0
        "R10" -> 10
        "R25" -> 25
        "R50" -> 50
        "R100" -> 100
        else -> 0
    }

    /**
     * Maps a cumulative damage total (0–100+) back to the nearest severity bucket.
     * In the first iteration the total equals the single-image score; in the second
     * iteration it equals front + back combined (clamped to 100 before passing in).
     */
    fun bucketFromTotal(total: Int): String = when {
        total >= 100 -> "R100"
        total >= 50 -> "R50"
        total >= 25 -> "R25"
        total >= 10 -> "R10"
        else -> "Healthy_Jackfruit"
    }

    /**
     * Combines front and back damage percentages into a single total.
     * Returns null if either side has not yet been captured (null input).
     * The caller is responsible for clamping the result to 100 for display.
     */
    fun computeTotal(frontPercent: Int?, backPercent: Int?): Int? {
        if (frontPercent == null || backPercent == null) return null
        return frontPercent + backPercent
    }

    /**
     * Returns true when the given bucket warrants a treatment recommendation dialog.
     * Healthy and fully-destroyed (R100) fruit do not require the standard recommender.
     */
    fun needsRecommendation(bucket: String): Boolean =
        bucket == "R10" || bucket == "R25" || bucket == "R50"

    /**
     * Calculates the Intersection-over-Union (IoU) between two bounding boxes.
     * Used during Non-Maximum Suppression to remove overlapping detections.
     */
    fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)
        val intersectionArea = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val box1Area = box1.w * box1.h
        val box2Area = box2.w * box2.h
        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    /**
     * Applies Non-Maximum Suppression to a list of bounding boxes and returns the
     * surviving detections sorted by confidence (highest first).
     *
     * @param boxes         Raw detections from the model.
     * @param iouThreshold  Overlap threshold above which the lower-confidence box is suppressed.
     */
    fun applyNMS(boxes: List<BoundingBox>, iouThreshold: Float = 0.5f): List<BoundingBox> {
        val sorted = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selected = mutableListOf<BoundingBox>()

        while (sorted.isNotEmpty()) {
            val first = sorted.removeAt(0)
            selected.add(first)
            sorted.removeAll { calculateIoU(first, it) >= iouThreshold }
        }

        return selected
    }
}
