package br.com.colman.timelapser

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File

/**
 * Encapsulates all FFMPEG-related logic: RTSP frame grabbing (preview and fallback)
 * and final video building using external ffmpeg process.
 */
class FfmpegService(
  private val rtspUrl: String
) {
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
  private val logger = LoggerFactory.getLogger(FfmpegService::class.java)
  private val converter = Java2DFrameConverter()

  // Preview grabber to keep an always-fresh snapshot
  private var previewJob: Job? = null
  @Volatile private var latestFrame: BufferedImage? = null

  fun startPreview() {
    if (previewJob != null) return
    previewJob = scope.launch(Dispatchers.IO) {
      while (true) {
        val grabber = FFmpegFrameGrabber(rtspUrl)
        grabber.setOption("rtsp_transport", "tcp")
        grabber.setOption("stimeout", "5000000")
        grabber.setOption("rw_timeout", "5000000")
        try {
          grabber.start()
          while (true) {
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
    }
  }

  fun stopPreview() {
    previewJob?.cancel()
    previewJob = null
    latestFrame = null
    logger.info("Preview grabber stopped")
  }

  /**
   * Returns a deep copy of the latest preview frame if available; otherwise attempts
   * a one-off grab from the RTSP stream with timeouts. Returns null on failure.
   */
  suspend fun captureStill(): BufferedImage? {
    latestFrame?.let { src ->
      return try {
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

  fun buildVideo(framesTempDirectory: File, frameCount: Int, outputDirectory: File, outputFileName: String) {
    val outputFps = 30
    val targetDurationSec = 5

    if (frameCount == 0) {
      logger.warn("No frames captured. Skipping video build.")
      return
    }

    val output = File(outputDirectory, outputFileName)

    val actualDurationSec = frameCount.toDouble() / outputFps
    val speedFactor = targetDurationSec / actualDurationSec

    val inputPattern = "${framesTempDirectory.absolutePath}/frame_%05d.jpg"

    val lastIndex = frameCount - 1
    val lastFramePath = File(framesTempDirectory, "frame_${lastIndex.toString().padStart(5, '0')}.jpg").absolutePath

    val seg1 = File(framesTempDirectory, "seg1.mp4")
    val setptsExpr = "setpts=${String.format(java.util.Locale.US, "%.6f", speedFactor)}*PTS"
    val seg1Cmd = listOf(
      "ffmpeg", "-y",
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

    val seg2 = File(framesTempDirectory, "seg2.mp4")
    val seg2Cmd = listOf(
      "ffmpeg", "-y",
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

    val concatList = File(framesTempDirectory, "concat.txt")
    concatList.writeText("""
file '${seg1.absolutePath}'
file '${seg2.absolutePath}'
""".trimIndent())

    val concatCmd = listOf(
      "ffmpeg", "-y",
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
}
