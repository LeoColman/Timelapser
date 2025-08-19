package br.com.colman.timelapser

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.slf4j.LoggerFactory
import java.util.Properties
import java.util.UUID

private val logger = LoggerFactory.getLogger(BambuMqttTransport::class.java)

class BambuMqttTransport(private val config: BambuConfiguration) : MqttCallbackExtended {
  val topic = "device/${config.serial}/report"
  
  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
  val messages: Flow<String> = _messages.asSharedFlow()

  private val client: MqttClient = MqttClient(config.brokerUrl, "timelapser-${UUID.randomUUID()}", MemoryPersistence())

  internal val connectOptions: MqttConnectOptions = MqttConnectOptions().apply {
    isAutomaticReconnect = true
    isCleanSession = false
    userName = Username
    password = config.accessCode.toCharArray()
    if (config.brokerUrl.startsWith("ssl://", ignoreCase = true)) {
      socketFactory = InsecureSocketFactory()
      isHttpsHostnameVerificationEnabled = false
      sslProperties = Properties().apply { put("com.ibm.ssl.verifyName", "false") }
    }
  }

  init {
    client.setCallback(this)
    client.connect(connectOptions)
    client.subscribe(topic)
    logger.info("Connected to MQTT ${config.brokerUrl}, subscribed to $topic")
  }

  override fun messageArrived(topic: String, message: MqttMessage) {
    val payload = message.payload.toString(Charsets.UTF_8)
    _messages.tryEmit(payload)
  }

  override fun connectionLost(cause: Throwable?) {
    logger.warn("MQTT connection lost: ${cause?.message}")
    // Automatic reconnect is enabled; the client will attempt to reconnect.
  }

  override fun connectComplete(reconnect: Boolean, serverURI: String?) {
    if (reconnect) {
      logger.info("MQTT reconnected to $serverURI, re-subscribing to $topic")
      runCatching { client.subscribe(topic) }
        .onFailure { logger.warn("Failed to re-subscribe after reconnect: ${it.message}", it) }
    } else {
      logger.info("MQTT initial connection to $serverURI complete")
    }
  }

  override fun deliveryComplete(token: IMqttDeliveryToken?) {}

  companion object { const val Username = "bblp" }
}
