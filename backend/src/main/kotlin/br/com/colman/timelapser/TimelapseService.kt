package br.com.colman.timelapser

import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.imageio.ImageIO
import kotlinx.coroutines.*

object TimelapseService {
    private val outputDir = File("frames")
    private val converter = Java2DFrameConverter()
    private var grabber: FFmpegFrameGrabber? = null
    private var capturing = false
    private var job: Job? = null
    private var frameCount = 0

    fun start(rtspUrl: String) {
        if (capturing) return
        outputDir.mkdirs()
        frameCount = 0
        capturing = true

        job = CoroutineScope(Dispatchers.IO).launch {
            grabber = FFmpegFrameGrabber(rtspUrl).apply {
                start()
            }

            while (capturing) {
                val frame = grabber?.grabImage()
                if (frame != null) {
                    val img: BufferedImage = converter.convert(frame)
                    val file = File(outputDir, "frame_${frameCount.toString().padStart(5, '0')}.jpg")
                    ImageIO.write(img, "jpg", file)
                    println("Saved: ${file.name}")
                    frameCount++
                } else {
                    println("No frame captured.")
                }

                delay(5000) // 5 seconds
            }

            grabber?.stop()
        }
    }

    fun stop(): String {
        capturing = false
        runBlocking { job?.cancelAndJoin() }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val output = "timelapse_$timestamp.mp4"

        ProcessBuilder(
            "ffmpeg",
            "-framerate", "10",
            "-pattern_type", "glob",
            "-i", "${outputDir.absolutePath}/frame_*.jpg",
            "-c:v", "libx264",
            "-pix_fmt", "yuv420p",
            output
        ).inheritIO().start().waitFor()

        outputDir.listFiles()?.forEach { it.delete() }
        return output
    }
}