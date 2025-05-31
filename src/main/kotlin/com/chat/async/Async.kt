package com.chat.async

import com.chat.async.`fun`.chat.async.app.ChatClient
import com.chat.async.app.ServerVertical
import io.vertx.core.Vertx

fun main() {
    val vertx = Vertx.vertx()
    vertx.deployVerticle(ServerVertical())

    val client1 = ChatClient(vertx)
    val client2 = ChatClient(vertx)

    client1.start()
    client2.start()
}
