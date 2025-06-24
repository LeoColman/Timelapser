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


  private var hlsProcess: Process? = null
  private val hlsDir: File = File(outputDirectory, "hls")
  private val hlsPlaylist get() = File(hlsDir, "stream.m3u8")

  fun start() {
    if (isCapturing) return logAlreadyCapturing()

    createDirectories()
    startTimelapseJob()
    startLivestream()
  }

  private fun logAlreadyCapturing() = logger.warn("Already capturing. Ignoring.")

  private fun createDirectories() {
    framesTempDirectory.mkdirs()
    outputDirectory.mkdirs()
    hlsDir.mkdirs()
  }

  private fun startTimelapseJob() {
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

  private fun startLivestream() {
    val cmd = listOf(
      "ffmpeg",
      "-fflags", "nobuffer", "-rtsp_transport", "tcp",
      "-i", rtspUrl,
      "-an",
      "-c:v", "libx264",
      "-preset", "veryfast",
      "-tune", "zerolatency",
      "-r", 25.toString(),
      "-f", "hls",
      "-hls_time", 1.toString(),
      "-hls_list_size", "5",
      "-hls_flags", "delete_segments+append_list",
      hlsPlaylist.absolutePath
    )
    hlsProcess = ProcessBuilder(cmd)
      .redirectError(ProcessBuilder.Redirect.INHERIT)
      .redirectOutput(ProcessBuilder.Redirect.INHERIT)
      .start()

    logger.info("Started HLS stream at ${hlsPlaylist.absolutePath}")
  }

  private fun stopLiveStream() {
    hlsProcess?.let {
      logger.info("Stopping HLS ffmpeg (pid ${it.pid()})")
      it.destroy()
      it.waitFor()
    }
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
      "-framerate", "10",
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
