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
import kotlinx.coroutines.delay
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import java.util.Locale
import javax.imageio.ImageIO

private val logger = LoggerFactory.getLogger(TimelapseTaker::class.java)

class TimelapseTaker(
  val bambuMqttClient: BambuMqttClient,
  private val ffmpeg: FfmpegService,
  val framesTempDirectory: File = File("frames"),
  val outputDirectory: File = File("output"),
) {
  private lateinit var job: Job
  var isCapturing = false
    private set

  private val scope = CoroutineScope(Dispatchers.IO)
  private var frameCount = 0

// Encapsulated FFMPEG operations

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
            // Let the capture job handle the 20s delay and final capture, then it will request stop()
            logger.info("Final layer reached ($layer/$total) – deferring stop until after final 20s capture")
          }
        }
    }
  }

  fun start() {
    if(isCapturing) return
    isCapturing = true
    ffmpeg.startPreview()
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
            logger.info("Reached final layer $layer/$total – waiting 20s before capturing final frame…")
            if (isCapturing) {
              delay(20_000)
              if (isCapturing) {
                captureFrame(layer)
                logger.info("Final frame captured; stopping timelapse…")
                scope.launch { stop() }
              }
            }
          }
        }
    }
  }

  private fun startPreviewGrabber() {
    ffmpeg.startPreview()
  }

  private fun stopPreviewGrabber() {
    ffmpeg.stopPreview()
  }

  suspend fun captureFrame(layer: Int) {
    val frame = captureFrame()
    frame?.let { persist(it, layer) }
    logger.info("Captured frame for layer $layer")
  }

  private suspend fun captureFrame(): BufferedImage? {
    return ffmpeg.captureStill()
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
    ffmpeg.buildVideo(framesTempDirectory, frameCount, outputDirectory, composeOutputFileName())
    removeTemporaryFrames()
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
