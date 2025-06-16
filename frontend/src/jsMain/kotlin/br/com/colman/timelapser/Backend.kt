package br.com.colman.timelapser

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.js.json

private const val BackendUrl = "http://localhost:9090"
private val httpClient = HttpClient { install(ContentNegotiation) { json() } }

fun start() {
  CoroutineScope(Dispatchers.Main).launch {
    httpClient.post("$BackendUrl/start")
  }
}

fun stop() {
  CoroutineScope(Dispatchers.Main).launch {
    httpClient.post("$BackendUrl/stop")
  }
}

suspend fun status(): String {
  return CoroutineScope(Dispatchers.Main).async<String> {
    httpClient.post("$BackendUrl/status").body()
  }.await()
}