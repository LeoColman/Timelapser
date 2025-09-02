package br.com.colman.timelapser

import br.com.colman.timelapser.di.appModule
import org.koin.core.context.startKoin
import org.koin.logger.slf4jLogger

fun main() {
  val koin = startKoin {
    slf4jLogger()
    modules(appModule)
  }.koin

  // Resolve the main orchestrator (which wires and starts everything else)
  koin.get<TimelapseTaker>()

  // Keep the JVM alive while coroutines and MQTT listeners run.
  while (true) {
    try {
      Thread.sleep(60_000)
    } catch (_: InterruptedException) {
      break
    }
  }
}