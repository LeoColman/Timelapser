package br.com.colman.timelapser

fun main() {
  val transport = BambuMqttTransport(
    brokerUrl = "ssl://192.168.0.2:8883",
    serial = "XXX",
    accessCode = "YYY",
  )
  val timelapseTaker = TimelapseTaker(
    "rtsp://thingino:thingino@192.168.0.3:554/ch0",
    BambuMqttClient(transport)
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