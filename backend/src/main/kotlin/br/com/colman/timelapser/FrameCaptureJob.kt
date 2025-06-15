package br.com.colman.timelapser

import kotlinx.coroutines.*
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.videoio.VideoCapture
import java.io.File

class FrameCaptureJob(
    private val rtspUrl: String,
    private val outputDir: File,
    private val intervalMillis: Long
) {
    private var job: Job? = null
    private var count = 0
    val isRunning get() = job?.isActive == true

    fun start() {
        job = CoroutineScope(Dispatchers.IO).launch {
            val cap = VideoCapture(rtspUrl)
            if (!cap.isOpened) {
                println("Error: Cannot open stream")
                return@launch
            }

            while (isActive) {
                val frame = Mat()
                if (cap.read(frame)) {
                    val file = File(outputDir, "frame_${count.toString().padStart(5, '0')}.jpg")
                    Imgcodecs.imwrite(file.absolutePath, frame)
                    println("Saved frame: ${file.name}")
                    count++
                } else {
                    println("Failed to grab frame")
                }
                delay(intervalMillis)
            }

            cap.release()
        }
    }

    fun stop() {
        job?.cancel()
    }
}