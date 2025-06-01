package com.chat.async

import com.chat.async.app.ChatClient
import com.chat.async.app.ServerVertical
import io.vertx.core.Vertx
import javafx.application.Application
import javafx.stage.Stage

class ChatApp : Application() {
    override fun start(primaryStage: Stage) {
        val vertx = Vertx.vertx()
        vertx.deployVerticle(ServerVertical())

        val client1 = ChatClient(vertx)
        val client2 = ChatClient(vertx)

        client1.start()
        client2.start()
    }
}

fun main() {
    Application.launch(ChatApp::class.java)
}
