package com.chat.async.app

import com.chat.async.app.ui.ChatUI
import com.rabbitmq.client.*
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import java.nio.charset.StandardCharsets
import java.util.*

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
            onSend = { toId, message -> sendMessage(toId, message) },
            onSendFile = { toId, fileName, fileBytes -> sendFile(toId, fileName, fileBytes) },
            onSendImage = { toId, imageBytes -> sendImage(toId, imageBytes) }
        )

        ui.onRegister = { name ->
            this.name = name
            registerUser(name)
        }
        try {
            // setupRabbitMQConnection
            connection = factory.newConnection()
            channel = connection.createChannel()
            channel.exchangeDeclarePassive(EXCHANGE)

            // setupQueues
            listOf(
                "register" to "chat.register",
                "message" to "chat.message",
                "file" to "chat.file",
                "image" to "chat.image"
            ).forEach { (routingKey, queueName) ->
                val queue = channel.queueDeclare(queueName, false, false, false, null).queue
                channel.queueBind(queue, EXCHANGE, routingKey)
            }

            println("✅ Client connected to RabbitMQ")
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Client failed to connect to RabbitMQ")
        }
    }

    private fun sendMessage(
        toId: String,
        message: String
    ) {
        val json = createBaseMessageJson(toId)
            .put("content", "$id ($name): $message")

        sendJsonMessage(json, "message")
    }

    private fun sendFile(
        toId: String,
        fileName: String,
        fileBytes: ByteArray
    ) {
        val json = createBaseMessageJson(toId)
            .put("fileName", fileName)
            .put("content", fileBytes.encodeBase64())

        sendJsonMessage(json, "file")
    }

    private fun sendImage(
        toId: String,
        imageBytes: ByteArray
    ) {
        val json = createBaseMessageJson(toId)
            .put("content", imageBytes.encodeBase64())

        sendJsonMessage(json, "image")
    }

    private fun createBaseMessageJson(
        toId: String
    ) = JsonObject()
        .put("toId", toId)
        .put("senderId", id)
        .put("senderName", name)

    private fun sendJsonMessage(
        json: JsonObject,
        routingKey: String
    ) {
        val props = AMQP.BasicProperties.Builder()
            .contentType("application/json")
            .build()

        channel.basicPublish(
            EXCHANGE,
            routingKey,
            props,
            json.encode().toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun registerUser(
        name: String
    ) {
        val replyQueue = channel.queueDeclare().queue
        val corrId = UUID.randomUUID().toString()

        val props = AMQP.BasicProperties.Builder()
            .replyTo(replyQueue)
            .correlationId(corrId)
            .build()

        val message = JsonObject().put("name", name).encode()
        channel.basicPublish(EXCHANGE, "register", props, message.toByteArray(StandardCharsets.UTF_8))

        setupRegistrationConsumer(replyQueue, corrId)
    }

    private fun setupRegistrationConsumer(
        replyQueue: String,
        corrId: String
    ) {
        val consumer = object : DefaultConsumer(channel) {
            override fun handleDelivery(
                consumerTag: String?,
                envelope: Envelope?,
                properties: AMQP.BasicProperties?,
                body: ByteArray?
            ) {
                if (properties?.correlationId == corrId) {
                    handleRegistrationResponse(body)
                    channel.basicCancel(consumerTag)
                }
            }
        }
        channel.basicConsume(replyQueue, true, consumer)
    }

    private fun handleRegistrationResponse(
        body: ByteArray?
    ) {
        id = body?.toString(StandardCharsets.UTF_8) ?: ""
        ui.setUserId(id)
        subscribeToMessages()
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
                handleIncomingMessage(body)
            }
        }
        channel.basicConsume(myQueue, true, consumer)
    }

    private fun handleIncomingMessage(
        body: ByteArray?
    ) {
        try {
            val content = body?.toString(StandardCharsets.UTF_8) ?: return
            val json = JsonObject(content)

            vertx.runOnContext {
                when {
                    json.containsKey("fileName") -> handleFileMessage(json)
                    else -> handleTextMessage(json)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleFileMessage(
        json: JsonObject
    ) {
        val fileName = json.getString("fileName")
        val fileContent = json.getString("content").decodeBase64()
        val senderId = json.getString("senderId")
        ui.showReceivedFile(senderId, fileName, fileContent)
    }

    private fun handleTextMessage(
        json: JsonObject
    ) {
        val message = json.getString("content")
        message.appendMessage(ui.chatArea)
    }

    override fun close() {
        try {
            channel.close()
            connection.close()
        } catch (e: Exception) {
            println("Error while closing connections: ${e.message}")
        }
    }
}