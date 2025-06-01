package com.chat.async.app

import com.chat.async.app.ui.ChatUI
import com.rabbitmq.client.*
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

class ChatClient(
    private val vertx: Vertx
) : AutoCloseable {
    private lateinit var id: String
    private lateinit var name: String
    private val factory = ConnectionFactory().apply { host = "localhost" }
    private lateinit var connection: Connection
    private lateinit var channel: Channel
    private lateinit var ui: ChatUI

    fun start() {
        this.ui = ChatUI(
            { toId, message -> this.sendMessage(toId, message) },
            { toId, fileName, fileBytes -> this.sendFile(toId, fileName, fileBytes) },
            { toId, imageBytes -> this.sendImage(toId, imageBytes) }
        )

        try {
            connection = factory.newConnection()
            channel = connection.createChannel()

            // exchange & queue
            channel.exchangeDeclarePassive(EXCHANGE)

            // register queue
            val registerQueue = channel.queueDeclare("chat.register", false, false, false, null).queue
            channel.queueBind(registerQueue, EXCHANGE, "register")

            // message queue
            val messageQueue = channel.queueDeclare("chat.message", false, false, false, null).queue
            channel.queueBind(messageQueue, EXCHANGE, "message")

            // file queue
            val fileQueue = channel.queueDeclare("chat.file", false, false, false, null).queue
            channel.queueBind(fileQueue, EXCHANGE, "file")

            // image queue
            val imageQueue = channel.queueDeclare("chat.image", false, false, false, null).queue
            channel.queueBind(imageQueue, EXCHANGE, "image")

            ui.onRegister = { enteredName ->
                name = enteredName
                registerUser(name)
            }

            println("✅ Client connected to RabbitMQ")
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Client failed to connect to RabbitMQ")
        }
    }

    private fun sendMessage(toId: String, message: String) {
        val json = JsonObject()
            .put("toId", toId)
            .put("content", "$id ($name): $message")
            .put("senderId", id)
            .put("senderName", name)
            .encode()

        val props = AMQP.BasicProperties.Builder()
            .contentType("application/json")
            .build()
        channel.basicPublish(EXCHANGE, "message", props, json.toByteArray(StandardCharsets.UTF_8))
    }

    private fun sendFile(toId: String, fileName: String, fileBytes: ByteArray) {
        val json = JsonObject()
            .put("toId", toId)
            .put("fileName", fileName)
            .put("content", fileBytes.encodeBase64())
            .put("senderId", id)
            .put("senderName", name)
            .encode()

        val props = AMQP.BasicProperties.Builder().build()
        channel.basicPublish(EXCHANGE, "file", props, json.toByteArray(StandardCharsets.UTF_8))
    }

    private fun sendImage(toId: String, imageBytes: ByteArray) {
        val json = JsonObject()
            .put("toId", toId)
            .put("content", imageBytes.encodeBase64())
            .put("senderId", id)
            .put("senderName", name)
            .encode()

        val props = AMQP.BasicProperties.Builder().build()
        channel.basicPublish(EXCHANGE, "image", props, json.toByteArray(StandardCharsets.UTF_8))
    }

    private fun registerUser(name: String) {
        // Make temp queue reply to claim ID
        val replyQueue = channel.queueDeclare().queue
        val corrId = UUID.randomUUID().toString()

        val props = AMQP.BasicProperties.Builder()
            .replyTo(replyQueue)
            .correlationId(corrId)
            .build()

        // send register request
        val message = JsonObject().put("name", name).encode()
        channel.basicPublish(EXCHANGE, "register", props, message.toByteArray(StandardCharsets.UTF_8))

        // reply listener
        val consumer = object : DefaultConsumer(channel) {
            override fun handleDelivery(
                consumerTag: String?,
                envelope: Envelope?,
                properties: AMQP.BasicProperties?,
                body: ByteArray?
            ) {
                if (properties?.correlationId == corrId) {
                    val idStr = body?.toString(StandardCharsets.UTF_8) ?: ""
                    id = idStr
                    ui.setUserId(id)
                    subscribeToMessages()
                    channel.basicCancel(consumerTag)
                }
            }
        }
        channel.basicConsume(replyQueue, true, consumer)
    }

    private fun subscribeToMessages() {
        val myQueue = "chat.to.$id"
        channel.queueDeclare(myQueue, false, false, false, null)

        val consumer = object : DefaultConsumer(channel) {
            override fun handleDelivery(
                consumerTag: String?,
                envelope: Envelope?,
                properties: AMQP.BasicProperties?,
                body: ByteArray?
            ) {
                try {
                    val content = body?.toString(StandardCharsets.UTF_8) ?: ""
                    val json = JsonObject(content)

                    vertx.runOnContext {
                        when {
                            json.containsKey("fileName") -> {
                                val fileName = json.getString("fileName")
                                val fileContent = json.getString("content").decodeBase64()
                                val senderId = json.getString("senderId")
                                ui.showReceivedFile(senderId, fileName, fileContent)
                            }
                            json.containsKey("content") && !json.containsKey("fileName") -> { // Text message
                                val message = json.getString("content")
                                message.appendMessage(ui.chatArea)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        channel.basicConsume(myQueue, true, consumer)
    }

    override fun close() {
        channel.close()
        connection.close()
    }
}