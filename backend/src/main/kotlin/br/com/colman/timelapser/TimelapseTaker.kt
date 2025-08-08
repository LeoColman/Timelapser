package br.com.colman.timelapser

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.imageio.ImageIO

private val logger = LoggerFactory.getLogger(TimelapseTaker::class.java)

class TimelapseTaker(
  val rtspUrl: String,
  val bambuMqttClient: BambuMqttClient,
  val framesTempDirectory: File = File("frames"),
  val outputDirectory: File = File("output"),
) {
  private lateinit var job: Job
  private val converter = Java2DFrameConverter()
  var isCapturing = false
    private set

  private val scope = CoroutineScope(Dispatchers.IO)
  private var frameCount = 0

  // Preview grabber to keep an always-fresh snapshot
  private var previewJob: Job? = null
  @Volatile private var latestFrame: BufferedImage? = null

  init {
    framesTempDirectory.mkdirs()
    outputDirectory.mkdirs()

    // Auto start and coordinate with capture job for stopping
    scope.launch {
      combine(bambuMqttClient.layer, bambuMqttClient.totalLayers) { layer, total -> layer to total }
        .collect { (layer, total) ->
          if (!isCapturing && layer == 0) {
            logger.info("Auto-starting timelapse at layer 0")
            start()
          }
          if (isCapturing && layer >= total) {
            // Let the capture job handle the 5s delay and final capture, then it will request stop()
            logger.info("Final layer reached ($layer/$total) – deferring stop until after final 5s capture")
          }
        }
    }
  }

  fun start() {
    if(isCapturing) return
    isCapturing = true
    startPreviewGrabber()
    startTimelapseJob()
  }

  private fun startTimelapseJob() {
    job = CoroutineScope(Dispatchers.IO).launch {
      combine(bambuMqttClient.layer, bambuMqttClient.totalLayers) { layer, total -> layer to total }
        .collect { (layer, total) ->
          if (!isCapturing) return@collect
          if (layer < total) {
            captureFrame(layer)
          } else {
            logger.info("Reached final layer $layer/$total – waiting 5s before capturing final frame…")
            kotlinx.coroutines.delay(5000)
            if (isCapturing) {
              captureFrame(layer)
              logger.info("Final frame captured after 5s; stopping timelapse…")
              scope.launch { stop() }
            }
          }
        }
    }
  }

  private fun startPreviewGrabber() {
    if (previewJob != null) return
    previewJob = CoroutineScope(Dispatchers.IO).launch {
      while (isCapturing) {
        val grabber = FFmpegFrameGrabber(rtspUrl)
        grabber.setOption("rtsp_transport", "tcp")
        grabber.setOption("stimeout", "5000000")
        grabber.setOption("rw_timeout", "5000000")
        try {
          grabber.start()
          while (isCapturing) {
            val frame = withTimeoutOrNull(2000) { grabber.grabImage() }
            if (frame != null) {
              val img = converter.convert(frame)
              if (img != null) {
                latestFrame = img
              }
            } else {
              // timeout: break to restart the grabber
              logger.debug("Preview grab timed out; restarting RTSP grabber")
              break
            }
          }
        } catch (e: Exception) {
          logger.warn("Preview grabber error: ${e.message}")
        } finally {
          runCatching { grabber.stop() }
          runCatching { grabber.release() }
        }
        // small backoff before retry
        try { Thread.sleep(300) } catch (_: InterruptedException) { }
      }
      logger.info("Preview grabber stopped")
    }
  }

  private fun stopPreviewGrabber() {
    previewJob?.cancel()
    previewJob = null
  }

  suspend fun captureFrame(layer: Int) {
    val frame = captureFrame()
    frame?.let { persist(it, layer) }
    logger.info("Captured frame for layer $layer")
  }

  private suspend fun captureFrame(): BufferedImage? {
    // Prefer the always-fresh frame from the preview grabber
    latestFrame?.let { src ->
      return try {
        // Deep copy to avoid any concurrent use of the backing buffer
        val copy = BufferedImage(src.width, src.height, src.type.takeIf { it != 0 } ?: BufferedImage.TYPE_3BYTE_BGR)
        val g = copy.createGraphics()
        g.drawImage(src, 0, 0, null)
        g.dispose()
        copy
      } catch (e: Exception) {
        logger.warn("Failed to copy latest preview frame: ${e.message}")
        null
      }
    }

    // Fallback: one-off grab (in case preview hasn't produced a frame yet)
    val grabber = FFmpegFrameGrabber(rtspUrl)
    grabber.setOption("rtsp_transport", "tcp")
    grabber.setOption("stimeout", "5000000")     // 5s connect timeout
    grabber.setOption("rw_timeout", "5000000")   // 5s IO timeout

    return try {
      val frame = withTimeoutOrNull(6000) {
        grabber.start()
        val img = converter.convert(grabber.grabImage())
        grabber.stop()
        img
      }
      if (frame == null) {
        logger.warn("RTSP grab timed out (fallback); skipping this frame")
      }
      frame
    } catch (e: Exception) {
      logger.warn("RTSP grab failed (fallback): ${e.message}", e)
      null
    } finally {
      runCatching { grabber.release() }.onFailure { /* ignore */ }
    }
  }

  private fun persist(frame: BufferedImage, layerIndex: Int) {
    val name = layerIndex.toString().padStart(5, '0')
    val file = File(framesTempDirectory, "frame_${name}.jpg")
    ImageIO.write(frame, "jpg", file)
    logger.info("Saved: ${file.name}")
    // Ensure frameCount reflects the highest layer index + 1
    if (layerIndex + 1 > frameCount) frameCount = layerIndex + 1
  }
  
  fun stop() {
    if (!isCapturing) return
    isCapturing = false
    runBlocking { job.cancelAndJoin() }
    stopPreviewGrabber()
    buildVideoWithFfmpeg()
    removeTemporaryFrames()
  }

  private fun buildVideoWithFfmpeg() {
    logger.info("Building video with FFMPEG")
    val outputFps = 30
    val targetDurationSec = 5

    if (frameCount == 0) {
      logger.warn("No frames captured. Skipping video build.")
      return
    }

    val output = File(outputDirectory, composeOutputFileName())

    // How long would the video be with no speed-up/down?
    val actualDurationSec = frameCount.toDouble() / outputFps

    // Factor < 1 ⇒ speed up, factor > 1 ⇒ slow down
    val speedFactor = targetDurationSec / actualDurationSec
    val setptsExpr = "setpts=${String.format(Locale.US, "%.6f", speedFactor)}*PTS"

    // Prefer numeric pattern over glob for reliability
    val inputPattern = "${framesTempDirectory.absolutePath}/frame_%05d.jpg"

    // Prepare the last frame path for the 5s still segment
    val lastIndex = frameCount - 1
    val lastFramePath = File(framesTempDirectory, "frame_${lastIndex.toString().padStart(5, '0')}.jpg").absolutePath

    // Build a robust concat: [timelapse (5s)] + [last frame looped for 5s]
    val filter = "[0:v]$setptsExpr,fps=$outputFps,format=yuv420p[v0];[1:v]fps=$outputFps,format=yuv420p,setsar=1[v1];[v0][v1]concat=n=2:v=1:a=0[out]"

    // Build segment 1: timelapse adjusted to exactly 5 seconds
    val seg1 = File(framesTempDirectory, "seg1.mp4")
    val seg1Cmd = listOf(
      "ffmpeg",
      "-y",
      "-framerate", outputFps.toString(),
      "-start_number", "0",
      "-i", inputPattern,
      "-vf", setptsExpr,
      "-vsync", "cfr",
      "-r", outputFps.toString(),
      "-c:v", "libx264",
      "-pix_fmt", "yuv420p",
      seg1.absolutePath
    )
    logger.info("Step 1/3: Building 5s timelapse segment → ${seg1.name}")
    var proc = ProcessBuilder(seg1Cmd).inheritIO().start()
    var exit = proc.waitFor()
    if (exit != 0) {
      logger.error("ffmpeg failed while building segment 1 (exit $exit)")
      return
    }

    // Build segment 2: 5s still from last frame
    val seg2 = File(framesTempDirectory, "seg2.mp4")
    val seg2Cmd = listOf(
      "ffmpeg",
      "-y",
      "-loop", "1",
      "-t", "5",
      "-i", lastFramePath,
      "-vsync", "cfr",
      "-r", outputFps.toString(),
      "-c:v", "libx264",
      "-pix_fmt", "yuv420p",
      seg2.absolutePath
    )
    logger.info("Step 2/3: Building 5s still segment from last frame → ${seg2.name}")
    proc = ProcessBuilder(seg2Cmd).inheritIO().start()
    exit = proc.waitFor()
    if (exit != 0) {
      logger.error("ffmpeg failed while building segment 2 (exit $exit)")
      return
    }

    // Concat both segments without re-encoding
    val concatList = File(framesTempDirectory, "concat.txt")
    concatList.writeText("""
file '${seg1.absolutePath}'
file '${seg2.absolutePath}'
""".trimIndent())

    val concatCmd = listOf(
      "ffmpeg",
      "-y",
      "-f", "concat",
      "-safe", "0",
      "-i", concatList.absolutePath,
      "-c", "copy",
      output.absolutePath
    )
    logger.info("Step 3/3: Concatenating segments → ${output.name}")
    proc = ProcessBuilder(concatCmd).inheritIO().start()
    exit = proc.waitFor()
    if (exit != 0) {
      logger.error("ffmpeg failed with exit code $exit while building ${output.name}")
    } else {
      logger.info("ffmpeg finished successfully → ${output.absolutePath}")
    }
  }

  private fun removeTemporaryFrames() {
    framesTempDirectory.listFiles()?.forEach { it.delete() }
    frameCount = 0
  }

  private fun composeOutputFileName(): String {
    val date = LocalDate.now().toString()
    val printName = runBlocking {
      withTimeoutOrNull(1000) { bambuMqttClient.name.firstOrNull() }
    } ?: "timelapse"

    logger.info("Composing output file name for $date/$printName")
    val filename = "${date}_$printName.mp4"
    
    // Ensure we don't overwrite an existing file by appending a numeric suffix if needed
    var candidate = File(outputDirectory, filename)
    var idx = 1
    while (candidate.exists() && idx < 1000) {
      val alt ="${date}_${filename}_$idx.mp4"
      candidate = File(outputDirectory, alt)
      idx++
    }
    return candidate.name
  }
}
