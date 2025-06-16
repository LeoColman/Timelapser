package br.com.colman.timelapser

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDateTime
import javax.imageio.ImageIO

private val logger = LoggerFactory.getLogger(TimelapseTaker::class.java)

class TimelapseTaker(
  val rtspUrl: String,
  val framesTempDirectory: File = File("frames"),
  val outputDirectory: File = File("output"),
  val intervalMillis: Long = 15_000L
) {
  private val converter = Java2DFrameConverter()
  var isCapturing = false
    private set
  private var captureJob: Job? = null
  private var frameCount = 0

  fun start() {
    if (isCapturing) return logAlreadyCapturing()

    createDirectories()
    startJob()
  }

  private fun logAlreadyCapturing() = logger.warn("Already capturing. Ignoring.")

  private fun createDirectories() {
    framesTempDirectory.mkdirs()
    outputDirectory.mkdirs()
  }

  private fun startJob() {
    isCapturing = true
    captureJob = CoroutineScope(Dispatchers.IO).launch {
      while (isCapturing) {
        val frame = captureFrame()
        frame?.let { persistFrame(it) }
        delay(intervalMillis)
      }
    }
  }

  private fun captureFrame(): BufferedImage? {
    var frame: BufferedImage? = null
    runCatching {
      FFmpegFrameGrabber(rtspUrl).use { grabber ->
        grabber.start()
        frame = converter.convert(grabber.grabImage())
        grabber.stop()
      }
    }.onFailure { logger.error("Error while grabbing frame: ${it.message}") }

    if (frame == null) {
      logger.warn("No frame captured.")
    }
    return frame
  }

  private fun persistFrame(bufferedImage: BufferedImage) {
    val file = File(framesTempDirectory, "frame_${frameCount.toString().padStart(5, '0')}.jpg")
    ImageIO.write(bufferedImage, "jpg", file)
    logger.info("Saved: ${file.name}")
    frameCount++
  }

  fun stop() {
    if (!isCapturing) {
      logger.warn("Not capturing. Ignoring.")
      return
    }
    isCapturing = false
    stopJob()
    buildVideoWithFfmpeg()
    removeTemporaryFrames()
  }

  private fun stopJob() {
    runBlocking { captureJob?.cancelAndJoin() }
  }

  private fun buildVideoWithFfmpeg() {
    val datetime = LocalDateTime.now().toString()
    val output = File(outputDirectory,"timelapse_${datetime}.mp4")

    ProcessBuilder(
      "ffmpeg",
      "-framerate", "30",
      "-pattern_type", "glob",
      "-i", "${framesTempDirectory.absolutePath}/frame_*.jpg",
      "-c:v", "libx264",
      "-pix_fmt", "yuv420p",
      output.absolutePath
    ).inheritIO().start().waitFor()
  }

  private fun removeTemporaryFrames() {
    framesTempDirectory.listFiles()?.forEach { it.delete() }
    frameCount = 0
  }
}
