package com.chat.async

import com.chat.async.app.verticle.ClientVerticle
import com.chat.async.app.verticle.ServerVerticle
import io.vertx.core.Vertx
import javafx.application.Application
import javafx.stage.Stage

class ChatApp : Application() {
    override fun start(primaryStage: Stage) {
        val vertx = Vertx.vertx()
        vertx.deployVerticle(ServerVerticle())

        // Multiple clients for testing
        repeat(2) {
            val client = ClientVerticle()
            vertx.deployVerticle(client)
            client.start()
        }
    }
}

fun main() {
    Application.launch(ChatApp::class.java)
}
