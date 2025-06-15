package br.com.colman.timelapser

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json
import io.ktor.serialization.kotlinx.json.*

fun main() {
    embeddedServer(Netty, port = 9090) {
        install(ContentNegotiation) {
            json(Json { prettyPrint = true })
        }
        routing {
            post("/start") {
                TimelapseService.start("rtsp://thingino:thingino@192.168.15.100:554/ch0")
                call.respondText("Timelapse started")
            }
            post("/stop") {
                val file = TimelapseService.stop()
                call.respondText("Timelapse saved to $file")
            }
        }
    }.start(wait = true)
}