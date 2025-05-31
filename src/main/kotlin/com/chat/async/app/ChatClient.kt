package com.chat.async.`fun`.chat.async.app

import com.rabbitmq.client.*
import com.chat.async.app.ChatUI
import com.chat.async.app.EXCHANGE
import com.chat.async.app.appendMessage
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeoutException
import java.util.function.Consumer
import kotlin.concurrent.thread

class ChatClient(
    private val vertx : Vertx
) : AutoCloseable {
    private lateinit var id: String
    private lateinit var name: String
    private val factory = ConnectionFactory().apply { host = "localhost" }
    private lateinit var connection: Connection
    private lateinit var channel: Channel
    private lateinit var ui: ChatUI

    fun start() {
        this.ui = ChatUI { toId, message -> this.sendMessage(toId, message) }
        thread {
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

                ui.onRegister = Consumer { enteredName ->
                    name = enteredName
                    registerUser(name)
                }

                ui.onSend = { toId, message ->
                    sendMessage(toId, message)
                }

                println("✅ Client connected to RabbitMQ")
            } catch (e: Exception) {
                e.printStackTrace()
                println("❌ Client failed to connect to RabbitMQ")
            }
        }
    }

    private fun registerUser(
        name : String
    ) {
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

    private fun sendMessage(
        toId : String,
        message : String
    ) {
        val json = JsonObject()
            .put("toId", toId)
            .put("content", "$id ($name): $message")
            .encode()

        val props = AMQP.BasicProperties.Builder().build()
        channel.basicPublish(EXCHANGE, "message", props, json.toByteArray(StandardCharsets.UTF_8))
    }

    private fun subscribeToMessages() {
        // Make queue receive message
        val myQueue = "chat.to.$id"
        channel.queueDeclare(myQueue, false, false, false, null)

        val consumer = object : DefaultConsumer(channel) {
            override fun handleDelivery(
                consumerTag: String?,
                envelope: Envelope?,
                properties: AMQP.BasicProperties?,
                body: ByteArray?
            ) {
                val content = body?.toString(StandardCharsets.UTF_8) ?: ""
                vertx.runOnContext {
                    content.appendMessage(ui.chatArea)
                }
            }
        }
        channel.basicConsume(myQueue, true, consumer)
    }

    override fun close() {
        try {
            channel.close()
            connection.close()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: TimeoutException) {
            e.printStackTrace()
        }
    }
}
