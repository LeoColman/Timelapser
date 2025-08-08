package br.com.colman.timelapser

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import javax.net.ssl.SSLSocket

class InsecureSocketFactorySpec : FunSpec({
  test("createSocket() returns an SSLSocket with hostname verification disabled") {
    val factory = InsecureSocketFactory()

    val socket = factory.createSocket()
    socket.shouldBeInstanceOf<SSLSocket>()

    val sslParams = socket.sslParameters
    // endpointIdentificationAlgorithm must be null to disable hostname verification
    sslParams.endpointIdentificationAlgorithm.shouldBeNull()

    // cleanup
    runCatching { socket.close() }
  }

  test("delegates cipher suites (non-empty default and supported lists)") {
    val factory = InsecureSocketFactory()

    factory.defaultCipherSuites.shouldNotBeEmpty()
    factory.supportedCipherSuites.shouldNotBeEmpty()
  }
})
