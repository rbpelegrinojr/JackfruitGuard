package com.surendramaran.jackguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * First Iteration – Unit Tests
 *
 * The first iteration of JackfruitGuard used a single-image capture approach.
 * The user photographed the jackfruit once and the app classified the disease
 * using the YOLOv8 / TFLite model.  These tests verify the core classification
 * logic that was in place during that iteration:
 *
 *  • Mapping raw model labels to human-readable names
 *  • Converting a label to its numeric damage percentage
 *  • Placing a damage score into the correct severity bucket
 *  • Bounding-box IoU calculation used during Non-Maximum Suppression
 *  • NMS itself – ensuring the highest-confidence box survives when two
 *    detections overlap significantly
 */
class Iteration1SingleImageTest {

    // -----------------------------------------------------------------------
    // 1. Label → display name mapping
    // -----------------------------------------------------------------------

    @Test
    fun `labelDisplay contains all expected disease labels`() {
        val expected = listOf(
            "Healthy_Jackfruit",
            "R10",
            "R25",
            "R50",
            "R100"
        )
        expected.forEach { label ->
            assertTrue(
                "labelDisplay should contain key '$label'",
                DamageAnalyzer.labelDisplay.containsKey(label)
            )
        }
    }

    @Test
    fun `labelDisplay maps healthy label correctly`() {
        assertEquals("Healthy Jackfruit", DamageAnalyzer.labelDisplay["Healthy_Jackfruit"])
    }

    @Test
    fun `labelDisplay maps R10 label correctly`() {
        assertEquals("Rhizopus Damage (R10)", DamageAnalyzer.labelDisplay["R10"])
    }

    @Test
    fun `labelDisplay maps R25 label correctly`() {
        assertEquals("Rhizopus Damage (R25)", DamageAnalyzer.labelDisplay["R25"])
    }

    @Test
    fun `labelDisplay maps R50 label correctly`() {
        assertEquals("Rhizopus Damage (R50)", DamageAnalyzer.labelDisplay["R50"])
    }

    @Test
    fun `labelDisplay maps R100 label correctly`() {
        assertEquals("Rhizopus Damage (R100)", DamageAnalyzer.labelDisplay["R100"])
    }

    // -----------------------------------------------------------------------
    // 2. Label → damage percentage
    // -----------------------------------------------------------------------

    @Test
    fun `damagePercentFromLabel returns 0 for healthy jackfruit`() {
        assertEquals(0, DamageAnalyzer.damagePercentFromLabel("Healthy_Jackfruit"))
    }

    @Test
    fun `damagePercentFromLabel returns 10 for R10`() {
        assertEquals(10, DamageAnalyzer.damagePercentFromLabel("R10"))
    }

    @Test
    fun `damagePercentFromLabel returns 25 for R25`() {
        assertEquals(25, DamageAnalyzer.damagePercentFromLabel("R25"))
    }

    @Test
    fun `damagePercentFromLabel returns 50 for R50`() {
        assertEquals(50, DamageAnalyzer.damagePercentFromLabel("R50"))
    }

    @Test
    fun `damagePercentFromLabel returns 100 for R100`() {
        assertEquals(100, DamageAnalyzer.damagePercentFromLabel("R100"))
    }

    @Test
    fun `damagePercentFromLabel returns 0 for unknown label`() {
        assertEquals(0, DamageAnalyzer.damagePercentFromLabel("UNKNOWN"))
    }

    // -----------------------------------------------------------------------
    // 3. Damage total → severity bucket
    // -----------------------------------------------------------------------

    @Test
    fun `bucketFromTotal classifies 0 as healthy`() {
        assertEquals("Healthy_Jackfruit", DamageAnalyzer.bucketFromTotal(0))
    }

    @Test
    fun `bucketFromTotal classifies 9 as healthy`() {
        assertEquals("Healthy_Jackfruit", DamageAnalyzer.bucketFromTotal(9))
    }

    @Test
    fun `bucketFromTotal classifies 10 as R10`() {
        assertEquals("R10", DamageAnalyzer.bucketFromTotal(10))
    }

    @Test
    fun `bucketFromTotal classifies 24 as R10`() {
        assertEquals("R10", DamageAnalyzer.bucketFromTotal(24))
    }

    @Test
    fun `bucketFromTotal classifies 25 as R25`() {
        assertEquals("R25", DamageAnalyzer.bucketFromTotal(25))
    }

    @Test
    fun `bucketFromTotal classifies 49 as R25`() {
        assertEquals("R25", DamageAnalyzer.bucketFromTotal(49))
    }

    @Test
    fun `bucketFromTotal classifies 50 as R50`() {
        assertEquals("R50", DamageAnalyzer.bucketFromTotal(50))
    }

    @Test
    fun `bucketFromTotal classifies 99 as R50`() {
        assertEquals("R50", DamageAnalyzer.bucketFromTotal(99))
    }

    @Test
    fun `bucketFromTotal classifies 100 as R100`() {
        assertEquals("R100", DamageAnalyzer.bucketFromTotal(100))
    }

    @Test
    fun `bucketFromTotal classifies values above 100 as R100`() {
        assertEquals("R100", DamageAnalyzer.bucketFromTotal(150))
    }

    // -----------------------------------------------------------------------
    // 4. Recommendation flag (first-iteration single-image check)
    // -----------------------------------------------------------------------

    @Test
    fun `needsRecommendation is false for healthy jackfruit`() {
        assertFalse(DamageAnalyzer.needsRecommendation("Healthy_Jackfruit"))
    }

    @Test
    fun `needsRecommendation is true for R10`() {
        assertTrue(DamageAnalyzer.needsRecommendation("R10"))
    }

    @Test
    fun `needsRecommendation is true for R25`() {
        assertTrue(DamageAnalyzer.needsRecommendation("R25"))
    }

    @Test
    fun `needsRecommendation is true for R50`() {
        assertTrue(DamageAnalyzer.needsRecommendation("R50"))
    }

    @Test
    fun `needsRecommendation is false for R100`() {
        assertFalse(DamageAnalyzer.needsRecommendation("R100"))
    }

    // -----------------------------------------------------------------------
    // 5. Bounding-box IoU calculation
    // -----------------------------------------------------------------------

    private fun box(x1: Float, y1: Float, x2: Float, y2: Float, cnf: Float = 0.9f) =
        BoundingBox(
            x1 = x1, y1 = y1, x2 = x2, y2 = y2,
            cx = (x1 + x2) / 2, cy = (y1 + y2) / 2,
            w = x2 - x1, h = y2 - y1,
            cnf = cnf, cls = 0, clsName = "R25"
        )

    @Test
    fun `calculateIoU returns 1_0 for identical boxes`() {
        val b = box(0.1f, 0.1f, 0.5f, 0.5f)
        assertEquals(1.0f, DamageAnalyzer.calculateIoU(b, b), 1e-5f)
    }

    @Test
    fun `calculateIoU returns 0 for non-overlapping boxes`() {
        val b1 = box(0.0f, 0.0f, 0.2f, 0.2f)
        val b2 = box(0.5f, 0.5f, 0.9f, 0.9f)
        assertEquals(0.0f, DamageAnalyzer.calculateIoU(b1, b2), 1e-5f)
    }

    @Test
    fun `calculateIoU returns correct value for partially overlapping boxes`() {
        // box1: [0,0,0.4,0.4]  area = 0.16
        // box2: [0.2,0.2,0.6,0.6]  area = 0.16
        // intersection: [0.2,0.2,0.4,0.4]  area = 0.04
        // union = 0.16 + 0.16 - 0.04 = 0.28
        // IoU = 0.04 / 0.28 ≈ 0.1429
        val b1 = box(0.0f, 0.0f, 0.4f, 0.4f)
        val b2 = box(0.2f, 0.2f, 0.6f, 0.6f)
        assertEquals(0.04f / 0.28f, DamageAnalyzer.calculateIoU(b1, b2), 1e-5f)
    }

    // -----------------------------------------------------------------------
    // 6. Non-Maximum Suppression (NMS)
    // -----------------------------------------------------------------------

    @Test
    fun `applyNMS returns single box when only one detection exists`() {
        val boxes = listOf(box(0.1f, 0.1f, 0.5f, 0.5f, cnf = 0.9f))
        val result = DamageAnalyzer.applyNMS(boxes)
        assertEquals(1, result.size)
        assertEquals(0.9f, result[0].cnf, 1e-5f)
    }

    @Test
    fun `applyNMS keeps highest-confidence box when two boxes overlap above threshold`() {
        val highConf = box(0.1f, 0.1f, 0.5f, 0.5f, cnf = 0.95f)
        val lowConf  = box(0.1f, 0.1f, 0.5f, 0.5f, cnf = 0.60f) // same region, lower score
        val result = DamageAnalyzer.applyNMS(listOf(highConf, lowConf), iouThreshold = 0.5f)
        assertEquals(1, result.size)
        assertEquals(0.95f, result[0].cnf, 1e-5f)
    }

    @Test
    fun `applyNMS keeps both boxes when they do not overlap`() {
        val b1 = box(0.0f, 0.0f, 0.2f, 0.2f, cnf = 0.9f)
        val b2 = box(0.5f, 0.5f, 0.9f, 0.9f, cnf = 0.8f)
        val result = DamageAnalyzer.applyNMS(listOf(b1, b2), iouThreshold = 0.5f)
        assertEquals(2, result.size)
    }

    @Test
    fun `applyNMS result is ordered by descending confidence`() {
        val b1 = box(0.0f, 0.0f, 0.2f, 0.2f, cnf = 0.7f)
        val b2 = box(0.5f, 0.5f, 0.9f, 0.9f, cnf = 0.95f)
        val result = DamageAnalyzer.applyNMS(listOf(b1, b2), iouThreshold = 0.5f)
        assertTrue(result[0].cnf >= result[1].cnf)
    }

    // -----------------------------------------------------------------------
    // 7. Single-image path: label → percent → bucket (end-to-end)
    // -----------------------------------------------------------------------

    @Test
    fun `single image R25 label produces R25 bucket`() {
        val label = "R25"
        val pct = DamageAnalyzer.damagePercentFromLabel(label)
        val bucket = DamageAnalyzer.bucketFromTotal(pct)
        assertEquals("R25", bucket)
    }

    @Test
    fun `single image healthy label produces healthy bucket and no recommendation`() {
        val label = "Healthy_Jackfruit"
        val pct = DamageAnalyzer.damagePercentFromLabel(label)
        val bucket = DamageAnalyzer.bucketFromTotal(pct)
        assertEquals("Healthy_Jackfruit", bucket)
        assertFalse(DamageAnalyzer.needsRecommendation(bucket))
    }

    @Test
    fun `single image R100 label produces R100 bucket and no recommendation`() {
        val label = "R100"
        val pct = DamageAnalyzer.damagePercentFromLabel(label)
        val bucket = DamageAnalyzer.bucketFromTotal(pct)
        assertEquals("R100", bucket)
        assertFalse(DamageAnalyzer.needsRecommendation(bucket))
    }

    @Test
    fun `computeTotal returns null when only front is available`() {
        assertNull(DamageAnalyzer.computeTotal(25, null))
    }

    @Test
    fun `computeTotal returns non-null when both sides are available`() {
        assertNotNull(DamageAnalyzer.computeTotal(25, 10))
    }
}
