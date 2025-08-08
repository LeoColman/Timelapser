package br.com.colman.timelapser

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Properties
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class BambuMqttClient(
  private val transport: BambuMqttTransport
) {
  private val Json = Json { ignoreUnknownKeys = true }
  private val logger = LoggerFactory.getLogger(BambuMqttClient::class.java)
  private val scope = CoroutineScope(Dispatchers.IO)

  init {
    scope.launch {
      transport.messages.collect { payload ->
        emitFilename(payload)
        emitTotalLayers(payload)
        emitLayerChange(payload)
      }
    }
  }

  private val _name: MutableStateFlow<String?> = MutableStateFlow(null)
  val name: Flow<String> = _name.asStateFlow().filterIsInstance<String>()

  private val _layer: MutableStateFlow<Int?> = MutableStateFlow(null)
  val layer: Flow<Int> = _layer.asStateFlow().filterIsInstance<Int>()

  private val _totalLayers: MutableStateFlow<Int?> = MutableStateFlow(null)
  val totalLayers: Flow<Int> = _totalLayers.asStateFlow().filterIsInstance<Int>()

  private fun emitFilename(payload: String) {
    scope.launch {
      val printFileName = Json.decodeFromString<Task>(payload).print?.subtask_name ?: return@launch
      logger.info("MQTT detected print file: $printFileName")
      _name.emit(printFileName)
    }
  }

  private fun emitLayerChange(payload: String) {
    scope.launch {
      val layer = Json.decodeFromString<Task>(payload).print?.layer_num ?: return@launch
      logger.info("MQTT layer change detected: $layer")
      _layer.emit(layer)
    }
  }

  private fun emitTotalLayers(payload: String) {
    scope.launch {
      val totalLayerNun = Json.decodeFromString<Task>(payload).print?.total_layer_num ?: return@launch
      logger.info("MQTT total layer change detected: $totalLayerNun")
      _totalLayers.emit(totalLayerNun)
    }
  }

  @Serializable
  data class Task(val print: Print? = null)
  @Serializable
  data class Print(val subtask_name: String? = null, val layer_num: Int? = null, val total_layer_num: Int? = null)
}
