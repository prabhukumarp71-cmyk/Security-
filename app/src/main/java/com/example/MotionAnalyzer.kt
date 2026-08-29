package com.example

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.abs

class MotionAnalyzer(
    private val threshold: Int,
    private val onMotionDetected: () -> Unit
) : ImageAnalysis.Analyzer {

    private var lastHistogram: IntArray? = null

    override fun analyze(image: ImageProxy) {
        val buffer = image.planes[0].buffer
        val data = buffer.toByteArray()
        val pixels = data.map { it.toInt() and 0xFF }
        
        val histogram = IntArray(256)
        for (i in pixels.indices step 4) {
            histogram[pixels[i]]++
        }

        if (lastHistogram != null) {
            var diff = 0
            for (i in 0 until 256) {
                diff += abs(histogram[i] - lastHistogram!![i])
            }
            
            val normalizedDiff = diff / (pixels.size / 4.0) * 100
            
            if (normalizedDiff > threshold) {
                onMotionDetected()
            }
        }
        
        lastHistogram = histogram
        image.close()
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    
        val data = ByteArray(remaining())
        get(data)   
        return data
    }
}
