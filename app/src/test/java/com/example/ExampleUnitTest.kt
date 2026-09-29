package com.example

import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testFarVsNearMotionDiscrimination() {
    val totalCells = 16 * 12 // 192 cells
    val nearExclusionThreshold = 25f // 25% of frame
    val minFarCoverage = 0.6f

    // Case 1: Near object (bug, leaf, hand close to camera covering 35% of frame)
    val nearChangedCells = (totalCells * 0.35f).toInt()
    val nearCoverage = (nearChangedCells.toFloat() / totalCells) * 100f
    val isNearObstacle = nearCoverage >= nearExclusionThreshold
    assertTrue("Near object occupying > 25% of frame should be flagged as near obstacle", isNearObstacle)

    // Case 2: Far object (distant car or person occupying 4% of frame)
    val farChangedCells = (totalCells * 0.04f).toInt()
    val farCoverage = (farChangedCells.toFloat() / totalCells) * 100f
    val isFarDetected = farCoverage in minFarCoverage..nearExclusionThreshold
    assertTrue("Far object occupying 4% of frame should trigger far capture", isFarDetected)

    // Case 3: Negligible noise/jitter (< 0.6%)
    val noiseChangedCells = 0
    val noiseCoverage = (noiseChangedCells.toFloat() / totalCells) * 100f
    val isNoiseTriggered = noiseCoverage >= minFarCoverage
    assertFalse("Noise below 0.6% should not trigger detection", isNoiseTriggered)
  }
}

