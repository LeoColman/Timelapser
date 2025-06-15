package br.com.colman.timelapser

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 9090) {
        install(ContentNegotiation) {
            json(Json { prettyPrint = true })
        }
        routing {
            val timelapseTaker = TimelapseTaker("rtsp://thingino:thingino@192.168.15.100:554/ch0")
            post("/start") {
                timelapseTaker.start()
                call.respondText("Timelapse started")
            }
            post("/stop") {
                timelapseTaker.stop()
                call.respondText("Timelapse saved")
            }
        }
    }.start(wait = true)
}