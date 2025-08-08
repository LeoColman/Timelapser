package br.com.colman.timelapser

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.eclipse.paho.client.mqttv3.MqttClient
import java.util.UUID

class BambuMqttTransportSpec : FunSpec({
  val mosquitto = installMosquitto()
  val broker by lazy { "tcp://${mosquitto.host}:${mosquitto.getMappedPort(1883)}" }

  val serial = "SERIAL"
  val target by lazy { BambuMqttTransport(broker, serial, "ACCESS_CODE") }
  
  test("BambuMqttTransport emits a payload published to its report topic") {
    val publisher = MqttClient(broker, UUID.randomUUID().toString())
    publisher.connect()
    val topic = "device/$serial/report"
    val payload = """{"print":{"subtask_name":"file.gcode"}}"""
    
    publisher.publish(topic, payload.toByteArray(), 0, true)

    val received = withTimeout(10_000) { target.messages.first() }
    received shouldBe payload

    publisher.disconnect()
  }
})
