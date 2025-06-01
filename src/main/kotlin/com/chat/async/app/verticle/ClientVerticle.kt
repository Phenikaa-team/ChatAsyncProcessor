package com.chat.async.app.verticle

import com.chat.async.app.EXCHANGE
import com.chat.async.app.encodeBase64
import com.chat.async.app.ui.ChatUI
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import io.vertx.core.AbstractVerticle
import io.vertx.core.json.JsonObject
import javafx.application.Platform

class ClientVerticle(
    private val clientName: String
) : AbstractVerticle() {

    var ui: ChatUI? = null

    override fun start() {
        ui = ChatUI(
            onSend = { toId, msg ->
                val json = JsonObject(mapOf("toId" to toId, "message" to msg))
                sendToRabbitMQ("message", json.encode())
            },
            onSendFile = { toId, name, bytes ->
                val json = JsonObject(mapOf("toId" to toId, "file" to name, "data" to bytes.encodeBase64()))
                sendToRabbitMQ("file", json.encode())
            },
            onSendImage = { toId, bytes ->
                val json = JsonObject(mapOf("toId" to toId, "image" to bytes.encodeBase64()))
                json.put("toId", toId)
                sendToRabbitMQ("image", json.encode())
            }
        )

        ui?.onRegister = { (name, uuid) ->
            registerUser(name, uuid)
        }
    }

    private fun registerUser(
        username : String,
        uuid : String
    ) {
        val factory = ConnectionFactory().apply {
            host = System.getenv("RABBITMQ_HOST") ?: "localhost"
        }
        val conn = factory.newConnection()
        val ch = conn.createChannel()

        val replyQueue = ch.queueDeclare().queue

        val props = AMQP.BasicProperties.Builder()
            .correlationId(uuid)
            .replyTo(replyQueue)
            .build()

        // Send both username and UUID
        val registrationData = JsonObject()
            .put("username", username)
            .put("uuid", uuid)
            .encode()

        ch.basicPublish(EXCHANGE, "register", props, registrationData.toByteArray())

        val consumer = object : DefaultConsumer(ch) {
            override fun handleDelivery(tag: String?, env: Envelope?, props: AMQP.BasicProperties?, body: ByteArray?) {
                if (props?.correlationId == uuid) {
                    val id = String(body!!)
                    println("[$clientName] Registered with ID: $id")

                    Platform.runLater {
                        ui?.chatArea?.appendText("Your ID: $id\n")
                        ui?.setUserId(id)
                    }

                    setupReceiver(id, ui!!)
                }
            }
        }

        ch.basicConsume(replyQueue, true, consumer)
    }

    private fun setupReceiver(
        id : String,
        ui : ChatUI
    ) {
        val factory = ConnectionFactory().apply {
            host = System.getenv("RABBITMQ_HOST") ?: "localhost"
        }
        val conn = factory.newConnection()
        val ch = conn.createChannel()
        val queue = "chat.to.$id"
        ch.queueDeclare(queue, false, false, false, null)

        ch.basicConsume(queue, true, object : DefaultConsumer(ch) {
            override fun handleDelivery(tag: String?, env: Envelope?, props: AMQP.BasicProperties?, body: ByteArray?) {
                val json = JsonObject(String(body!!))
                val msg = json.getString("message")
                    ?: json.getString("file")?.let { "📁 File received: $it" }
                    ?: json.getString("image")?.let { "🖼️ Image received." }
                Platform.runLater { ui.chatArea.appendText("[Received] $msg\n") }
            }
        })
    }

    private fun sendToRabbitMQ(
        routingKey : String,
        data : String
    ) {
        val factory = ConnectionFactory().apply {
            host = System.getenv("RABBITMQ_HOST") ?: "localhost"
        }
        val conn = factory.newConnection()
        val ch = conn.createChannel()
        ch.basicPublish(EXCHANGE, routingKey, AMQP.BasicProperties.Builder().contentType("application/json").build(), data.toByteArray())
        conn.close()
    }
}