package br.com.colman.timelapser.di

import br.com.colman.timelapser.BambuConfiguration
import br.com.colman.timelapser.BambuMqttClient
import br.com.colman.timelapser.BambuMqttTransport
import br.com.colman.timelapser.FfmpegService
import br.com.colman.timelapser.TimelapseTaker
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin module wiring the application dependencies.
 * For now, configuration values are bound from constants; they can be easily
 * swapped to read from env vars or a config file later.
 */
val appModule = module {
  // Configuration values (could be externalized later)
  single(named("brokerUrl")) { System.getenv("BAMBU_BROKER_URL") ?: "ssl://192.168.0.1:8883" }
  single(named("serial")) { System.getenv("BAMBU_SERIAL") ?: "SERIAL" }
  single(named("accessCode")) { System.getenv("BAMBU_ACCESS_CODE") ?: "ACCESS_CODE" }
  single(named("rtspUrl")) { System.getenv("RTSP_URL") ?: "rtsp://192.168.0.2:554/ch0" }

  // Core configuration object
  single { BambuConfiguration(
    brokerUrl = get(named("brokerUrl")),
    serial = get(named("serial")),
    accessCode = get(named("accessCode"))
  ) }

  // Transports and services
  single { BambuMqttTransport(get()) }
  single { BambuMqttClient(get()) }
  single { FfmpegService(get(named("rtspUrl"))) }

  // Main orchestrator
  single { TimelapseTaker(
    bambuMqttClient = get(),
    ffmpeg = get()
  ) }
}
