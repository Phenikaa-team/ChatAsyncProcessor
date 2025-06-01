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


        val client1 = ClientVerticle("Client-1")
        vertx.deployVerticle(client1)
        client1.start()

        val client2 = ClientVerticle("Client-2")
        vertx.deployVerticle(client2)
        client2.start()
    }
}

fun main() {
    Application.launch(ChatApp::class.java)
}
