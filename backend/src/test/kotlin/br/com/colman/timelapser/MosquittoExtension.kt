package br.com.colman.timelapser

import io.kotest.core.extensions.install
import io.kotest.core.spec.Spec
import io.kotest.extensions.testcontainers.ContainerExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable

private val config = """
    listener 1883 0.0.0.0
    allow_anonymous true
    persistence false
""".trimIndent()

fun Spec.installMosquitto(): GenericContainer<out GenericContainer<*>?> {
  return install(ContainerExtension(GenericContainer("eclipse-mosquitto:2.0").apply {
    withExposedPorts(1883)
    waitingFor(Wait.forListeningPort())
    
    withCopyToContainer(Transferable.of(config.toByteArray()), "/mosquitto/config/mosquitto.conf")
  }))
}