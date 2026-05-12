package com.surendramaran.jackguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Second Iteration – Unit Tests
 *
 * The second iteration introduced a dual-image capture mechanism: the user
 * photographs both the FRONT and BACK of the jackfruit.  The damage scores
 * from each side are summed to produce a more reliable total assessment.
 *
 * These tests verify:
 *
 *  • Total damage calculation from two independent side scores
 *  • Clamping behaviour (total may not exceed 100 %)
 *  • Correct severity-bucket assignment based on the combined total
 *  • Treatment recommendation visibility logic for all combined totals
 *  • Representative end-to-end paths (front + back → total → bucket → recommendation)
 *  • Edge cases: both sides healthy, one side missing, R100 combinations
 */
class Iteration2DualImageTest {

    // -----------------------------------------------------------------------
    // 1. computeTotal – addition of front and back scores
    // -----------------------------------------------------------------------

    @Test
    fun `computeTotal returns null when front is missing`() {
        assertNull(DamageAnalyzer.computeTotal(null, 25))
    }

    @Test
    fun `computeTotal returns null when back is missing`() {
        assertNull(DamageAnalyzer.computeTotal(10, null))
    }

    @Test
    fun `computeTotal returns null when both sides are missing`() {
        assertNull(DamageAnalyzer.computeTotal(null, null))
    }

    @Test
    fun `computeTotal returns correct sum for healthy front and healthy back`() {
        val total = DamageAnalyzer.computeTotal(0, 0)
        assertNotNull(total)
        assertEquals(0, total)
    }

    @Test
    fun `computeTotal returns correct sum for R10 front and healthy back`() {
        val total = DamageAnalyzer.computeTotal(10, 0)
        assertNotNull(total)
        assertEquals(10, total)
    }

    @Test
    fun `computeTotal returns correct sum for R10 front and R10 back`() {
        val total = DamageAnalyzer.computeTotal(10, 10)
        assertNotNull(total)
        assertEquals(20, total)
    }

    @Test
    fun `computeTotal returns correct sum for R25 front and R25 back`() {
        val total = DamageAnalyzer.computeTotal(25, 25)
        assertNotNull(total)
        assertEquals(50, total)
    }

    @Test
    fun `computeTotal returns correct sum for R50 front and R50 back`() {
        val total = DamageAnalyzer.computeTotal(50, 50)
        assertNotNull(total)
        assertEquals(100, total)
    }

    @Test
    fun `computeTotal can exceed 100 before clamping`() {
        val total = DamageAnalyzer.computeTotal(100, 100)
        assertNotNull(total)
        assertEquals(200, total)
    }

    // -----------------------------------------------------------------------
    // 2. Clamping (caller responsibility, verified here to document contract)
    // -----------------------------------------------------------------------

    @Test
    fun `clamping total to 100 gives correct value when sum exceeds limit`() {
        val raw = DamageAnalyzer.computeTotal(50, 75) ?: 0
        val clamped = raw.coerceAtMost(100)
        assertEquals(100, clamped)
    }

    @Test
    fun `clamping total to 100 leaves value unchanged when sum is below limit`() {
        val raw = DamageAnalyzer.computeTotal(25, 10) ?: 0
        val clamped = raw.coerceAtMost(100)
        assertEquals(35, clamped)
    }

    // -----------------------------------------------------------------------
    // 3. bucketFromTotal – classification of combined damage
    // -----------------------------------------------------------------------

    @Test
    fun `combined total 0 maps to healthy bucket`() {
        assertEquals("Healthy_Jackfruit", DamageAnalyzer.bucketFromTotal(0))
    }

    @Test
    fun `combined total 20 maps to R10 bucket`() {
        assertEquals("R10", DamageAnalyzer.bucketFromTotal(20))
    }

    @Test
    fun `combined total 35 maps to R25 bucket`() {
        assertEquals("R25", DamageAnalyzer.bucketFromTotal(35))
    }

    @Test
    fun `combined total 75 maps to R50 bucket`() {
        assertEquals("R50", DamageAnalyzer.bucketFromTotal(75))
    }

    @Test
    fun `combined total 100 maps to R100 bucket`() {
        assertEquals("R100", DamageAnalyzer.bucketFromTotal(100))
    }

    // -----------------------------------------------------------------------
    // 4. Treatment recommendation visibility for combined totals
    // -----------------------------------------------------------------------

    @Test
    fun `no recommendation when combined total is healthy`() {
        val bucket = DamageAnalyzer.bucketFromTotal(0)
        assertFalse(DamageAnalyzer.needsRecommendation(bucket))
    }

    @Test
    fun `recommendation shown when combined total falls in R10 range`() {
        val bucket = DamageAnalyzer.bucketFromTotal(15)
        assertTrue(DamageAnalyzer.needsRecommendation(bucket))
    }

    @Test
    fun `recommendation shown when combined total falls in R25 range`() {
        val bucket = DamageAnalyzer.bucketFromTotal(30)
        assertTrue(DamageAnalyzer.needsRecommendation(bucket))
    }

    @Test
    fun `recommendation shown when combined total falls in R50 range`() {
        val bucket = DamageAnalyzer.bucketFromTotal(60)
        assertTrue(DamageAnalyzer.needsRecommendation(bucket))
    }

    @Test
    fun `no recommendation when combined total is R100`() {
        val bucket = DamageAnalyzer.bucketFromTotal(100)
        assertFalse(DamageAnalyzer.needsRecommendation(bucket))
    }

    // -----------------------------------------------------------------------
    // 5. End-to-end dual-image paths (label → percent → total → bucket → recommendation)
    // -----------------------------------------------------------------------

    @Test
    fun `healthy front and healthy back yields healthy classification`() {
        val frontLabel = "Healthy_Jackfruit"
        val backLabel  = "Healthy_Jackfruit"

        val frontPct = DamageAnalyzer.damagePercentFromLabel(frontLabel)
        val backPct  = DamageAnalyzer.damagePercentFromLabel(backLabel)
        val total    = DamageAnalyzer.computeTotal(frontPct, backPct) ?: 0
        val clamped  = total.coerceAtMost(100)
        val bucket   = DamageAnalyzer.bucketFromTotal(clamped)

        assertEquals("Healthy_Jackfruit", bucket)
        assertFalse(DamageAnalyzer.needsRecommendation(bucket))
    }

    @Test
    fun `R10 front and healthy back upgrades classification to R10 and shows recommendation`() {
        val frontPct = DamageAnalyzer.damagePercentFromLabel("R10")
        val backPct  = DamageAnalyzer.damagePercentFromLabel("Healthy_Jackfruit")
        val total    = DamageAnalyzer.computeTotal(frontPct, backPct) ?: 0
        val clamped  = total.coerceAtMost(100)
        val bucket   = DamageAnalyzer.bucketFromTotal(clamped)

        assertEquals("R10", bucket)
        assertTrue(DamageAnalyzer.needsRecommendation(bucket))
    }

    @Test
    fun `R10 front and R10 back combined stays in R10 bucket and shows recommendation`() {
        val frontPct = DamageAnalyzer.damagePercentFromLabel("R10")
        val backPct  = DamageAnalyzer.damagePercentFromLabel("R10")
        val total    = DamageAnalyzer.computeTotal(frontPct, backPct) ?: 0
        val clamped  = total.coerceAtMost(100)
        val bucket   = DamageAnalyzer.bucketFromTotal(clamped)

        // 10 + 10 = 20, which still falls in the R10 severity range (10–24)
        assertEquals("R10", bucket)
        assertTrue(DamageAnalyzer.needsRecommendation(bucket))
    }

    @Test
    fun `R25 front and R25 back upgrades classification to R50 and shows recommendation`() {
        val frontPct = DamageAnalyzer.damagePercentFromLabel("R25")
        val backPct  = DamageAnalyzer.damagePercentFromLabel("R25")
        val total    = DamageAnalyzer.computeTotal(frontPct, backPct) ?: 0
        val clamped  = total.coerceAtMost(100)
        val bucket   = DamageAnalyzer.bucketFromTotal(clamped)

        assertEquals("R50", bucket)
        assertTrue(DamageAnalyzer.needsRecommendation(bucket))
    }

    @Test
    fun `R50 front and R50 back yields R100 classification and no recommendation`() {
        val frontPct = DamageAnalyzer.damagePercentFromLabel("R50")
        val backPct  = DamageAnalyzer.damagePercentFromLabel("R50")
        val total    = DamageAnalyzer.computeTotal(frontPct, backPct) ?: 0
        val clamped  = total.coerceAtMost(100)
        val bucket   = DamageAnalyzer.bucketFromTotal(clamped)

        assertEquals("R100", bucket)
        assertFalse(DamageAnalyzer.needsRecommendation(bucket))
    }

    @Test
    fun `R100 front with any back clamps to R100 classification`() {
        val frontPct = DamageAnalyzer.damagePercentFromLabel("R100")
        val backPct  = DamageAnalyzer.damagePercentFromLabel("R25")
        val total    = DamageAnalyzer.computeTotal(frontPct, backPct) ?: 0
        val clamped  = total.coerceAtMost(100)
        val bucket   = DamageAnalyzer.bucketFromTotal(clamped)

        assertEquals("R100", bucket)
        assertFalse(DamageAnalyzer.needsRecommendation(bucket))
    }

    // -----------------------------------------------------------------------
    // 6. Dual-image improves detection: asymmetric damage across sides
    // -----------------------------------------------------------------------

    @Test
    fun `asymmetric damage R10 front and R25 back produces R25 bucket`() {
        val frontPct = DamageAnalyzer.damagePercentFromLabel("R10")
        val backPct  = DamageAnalyzer.damagePercentFromLabel("R25")
        val total    = DamageAnalyzer.computeTotal(frontPct, backPct) ?: 0
        val clamped  = total.coerceAtMost(100)
        val bucket   = DamageAnalyzer.bucketFromTotal(clamped)

        // 10 + 25 = 35 → R25 range
        assertEquals("R25", bucket)
        assertTrue(DamageAnalyzer.needsRecommendation(bucket))
    }

    @Test
    fun `asymmetric damage healthy front and R50 back produces R50 bucket`() {
        val frontPct = DamageAnalyzer.damagePercentFromLabel("Healthy_Jackfruit")
        val backPct  = DamageAnalyzer.damagePercentFromLabel("R50")
        val total    = DamageAnalyzer.computeTotal(frontPct, backPct) ?: 0
        val clamped  = total.coerceAtMost(100)
        val bucket   = DamageAnalyzer.bucketFromTotal(clamped)

        assertEquals("R50", bucket)
        assertTrue(DamageAnalyzer.needsRecommendation(bucket))
    }

    @Test
    fun `dual-image total damage text format is correct`() {
        val frontPct = DamageAnalyzer.damagePercentFromLabel("R25")
        val backPct  = DamageAnalyzer.damagePercentFromLabel("R10")
        val total    = DamageAnalyzer.computeTotal(frontPct, backPct) ?: 0
        val clamped  = total.coerceAtMost(100)

        val expectedText = "Total Damage: ${clamped}% (Front ${frontPct}% + Back ${backPct}%)"
        assertEquals("Total Damage: 35% (Front 25% + Back 10%)", expectedText)
    }
}
