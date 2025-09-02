package br.com.colman.timelapser

fun main() {
  val transport = BambuMqttTransport(
    brokerUrl = "ssl://192.168.0.2:8883",
    serial = "XXX",
    accessCode = "YYY",
  )
  val transport = BambuMqttTransport(config)
  
  val ffmpeg = FfmpegService("rtsp://thingino:thingino@192.168.15.100:554/ch0")
  TimelapseTaker(
    BambuMqttClient(transport),
    ffmpeg
  )

  // Keep the JVM alive while coroutines and MQTT listeners run.
  while (true) {
    try {
      Thread.sleep(60_000)
    } catch (_: InterruptedException) {
      break
    }
  }
}