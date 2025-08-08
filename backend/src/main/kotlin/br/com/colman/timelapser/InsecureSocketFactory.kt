package br.com.colman.timelapser

import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class InsecureSocketFactory : SSLSocketFactory() {
  private val delegate = SSLContext.getInstance("TLS").apply {
    init(null, arrayOf<TrustManager>(object : X509TrustManager {
      override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
      override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
      override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }), SecureRandom())
  }.socketFactory
  override fun getDefaultCipherSuites() = delegate.defaultCipherSuites
  override fun getSupportedCipherSuites() = delegate.supportedCipherSuites
  private fun SSLSocket.tweak() = apply { sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = null } }
  override fun createSocket(): Socket = (delegate.createSocket() as SSLSocket).tweak()
  override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket = (delegate.createSocket(s, host, port, autoClose) as SSLSocket).tweak()
  override fun createSocket(host: String?, port: Int): Socket = (delegate.createSocket(host, port) as SSLSocket).tweak()
  override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = (delegate.createSocket(host, port, localHost, localPort) as SSLSocket).tweak()
  override fun createSocket(host: InetAddress?, port: Int): Socket = (delegate.createSocket(host, port) as SSLSocket).tweak()
  override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = (delegate.createSocket(address, port, localAddress, localPort) as SSLSocket).tweak()
}
