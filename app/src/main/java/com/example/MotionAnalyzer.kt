package com.example

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs

class MotionAnalyzer(
    private val threshold: Int,
    private val isFarOnlyMode: Boolean = false,
    private val nearExclusionThreshold: Int = 25, // % coverage above which is considered near foreground object
    private val onEvent: (isMotionDetected: Boolean, isFar: Boolean, coveragePercent: Float) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "MotionAnalyzer"
        private const val GRID_COLS = 16
        private const val GRID_ROWS = 12
        private const val TOTAL_CELLS = GRID_COLS * GRID_ROWS
        private const val MIN_FAR_COVERAGE = 0.6f // % minimum area for distant target
    }

    private val previousGrid = FloatArray(TOTAL_CELLS)
    private var hasPreviousGrid = false
    private var lastHistogram: IntArray? = null

    override fun analyze(image: ImageProxy) {
        try {
            if (isFarOnlyMode) {
                analyzeFarDistance(image)
            } else {
                analyzeStandard(image)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Analysis error", e)
        } finally {
            image.close()
        }
    }

    /**
     * Spatial grid-based motion analyzer specifically distinguishing far movements from near obstacles.
     * Near obstacles (bugs, hands, close leaves, raindrops) cover a large percentage of the frame (> nearExclusionThreshold%).
     * Far targets (distant people, vehicles, animals) cover a small-to-moderate localized area (MIN_FAR_COVERAGE% to nearExclusionThreshold%).
     */
    private fun analyzeFarDistance(image: ImageProxy) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height

        val cellWidth = width / GRID_COLS
        val cellHeight = height / GRID_ROWS

        val currentGrid = FloatArray(TOTAL_CELLS)
        var cellIndex = 0

        // Calculate average luminance for each grid cell
        for (r in 0 until GRID_ROWS) {
            val startY = r * cellHeight
            val endY = (r + 1) * cellHeight
            for (c in 0 until GRID_COLS) {
                val startX = c * cellWidth
                val endX = (c + 1) * cellWidth

                var sumLuma = 0L
                var samples = 0

                // Fast strided sampling inside cell
                var y = startY
                while (y < endY) {
                    val rowOffset = y * rowStride
                    var x = startX
                    while (x < endX) {
                        val offset = rowOffset + x * pixelStride
                        if (offset < buffer.limit()) {
                            val luma = buffer.get(offset).toInt() and 0xFF
                            sumLuma += luma
                            samples++
                        }
                        x += 4
                    }
                    y += 4
                }

                currentGrid[cellIndex] = if (samples > 0) sumLuma.toFloat() / samples else 0f
                cellIndex++
            }
        }

        if (hasPreviousGrid) {
            var changedCells = 0
            // Sensitivity delta threshold for each cell (scaled by motion sensitivity)
            val cellDeltaThreshold = (threshold * 0.35f).coerceIn(4f, 25f)

            for (i in 0 until TOTAL_CELLS) {
                val diff = abs(currentGrid[i] - previousGrid[i])
                if (diff > cellDeltaThreshold) {
                    changedCells++
                }
            }

            val coveragePercent = (changedCells.toFloat() / TOTAL_CELLS) * 100f

            if (coveragePercent >= nearExclusionThreshold) {
                // NEAR FOREGROUND INTERFERENCE DETECTED!
                // Bug, hand, or object right at the lens covering large field of view.
                // IGNORE near movement!
                onEvent(false, false, coveragePercent)
            } else if (coveragePercent >= MIN_FAR_COVERAGE) {
                // FAR DISTANCE TARGET DETECTED!
                // Distant subject moving within frame.
                onEvent(true, true, coveragePercent)
            } else {
                // Background calm
                onEvent(false, true, coveragePercent)
            }
        }

        System.arraycopy(currentGrid, 0, previousGrid, 0, TOTAL_CELLS)
        hasPreviousGrid = true
    }

    /**
     * Standard overall histogram-based motion analysis.
     */
    private fun analyzeStandard(image: ImageProxy) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val limit = buffer.limit()
        val histogram = IntArray(256)
        var totalSamples = 0

        var i = 0
        while (i < limit) {
            val luma = buffer.get(i).toInt() and 0xFF
            histogram[luma]++
            totalSamples++
            i += 4
        }

        if (lastHistogram != null && totalSamples > 0) {
            var diff = 0
            for (h in 0 until 256) {
                diff += abs(histogram[h] - lastHistogram!![h])
            }
            val normalizedDiff = (diff.toDouble() / totalSamples) * 100.0
            if (normalizedDiff > threshold) {
                onEvent(true, false, normalizedDiff.toFloat())
            } else {
                onEvent(false, false, normalizedDiff.toFloat())
            }
        }

        lastHistogram = histogram
    }
}
