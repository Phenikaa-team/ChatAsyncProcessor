package com.chat.async.app.verticle

import com.chat.async.app.EXCHANGE
import com.chat.async.app.generateMessageId
import com.chat.async.app.generateUserId
import com.rabbitmq.client.*
import io.vertx.core.AbstractVerticle
import io.vertx.core.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

class ServerVerticle : AbstractVerticle() {
    private val messages = ConcurrentHashMap<String, Pair<String, String>>()

    private val users = ConcurrentHashMap<String, String>()
    private lateinit var connection: Connection
    private lateinit var channel: Channel

    override fun start() {
        vertx.executeBlocking<Unit>({ promise ->
            try {
                val factory = ConnectionFactory().apply {
                    host = System.getenv("RABBITMQ_HOST") ?: "localhost"
                }
                connection = factory.newConnection()
                channel = connection.createChannel()
                channel.exchangeDeclare(EXCHANGE, "direct", true)

                setupQueue("chat.register", "register", createRegistrationConsumer())
                setupQueue("chat.message", "message", createMessageConsumer())
                setupQueue("chat.file", "file", createMessageConsumer())
                setupQueue("chat.image", "image", createMessageConsumer())

                println("✅ Server is running with distributed queues")
                promise.complete()
            } catch (e: Exception) {
                e.printStackTrace()
                promise.fail(e)
            }
        }, false) {}
    }

    private fun setupQueue(
        name: String,
        key: String,
        consumer: Consumer
    ) {
        val queue = channel.queueDeclare(name, false, false, false, null).queue
        channel.queueBind(queue, EXCHANGE, key)
        channel.basicConsume(queue, true, consumer)
    }

    private fun createRegistrationConsumer() = object : DefaultConsumer(channel) {
        override fun handleDelivery(
            tag: String?,
            env: Envelope?,
            props: AMQP.BasicProperties?,
            body: ByteArray?
        ) {
            val json = JsonObject(String(body!!))
            val name = json.getString("username")
            val id = json.getString("uuid") ?: generateUserId()

            if (users.containsKey(id)) {
                // ID already exists - send error back
                val replyProps = AMQP.BasicProperties.Builder()
                    .correlationId(props?.correlationId)
                    .build()
                channel.basicPublish("", props?.replyTo, replyProps,
                    "ERROR: ID already exists".toByteArray())
                return
            }

            users[id] = name
            println("Registered user $name with ID $id")

            val replyData = JsonObject()
                .put("userId", id)
                .put("username", name)
                .encode()

            val replyProps = AMQP.BasicProperties.Builder()
                .correlationId(props?.correlationId)
                .build()
            channel.basicPublish("", props?.replyTo, replyProps, replyData.toByteArray())
        }
    }

    private fun createMessageConsumer() = object : DefaultConsumer(channel) {
        override fun handleDelivery(
            tag: String?,
            env: Envelope?,
            props: AMQP.BasicProperties?,
            body: ByteArray?
        ) {
            try {
                val json = JsonObject(String(body!!))
                val toId = json.getString("toId")
                val route = env?.routingKey ?: "message"

                // Get the sender id from headers or from the message
                val senderId = props?.headers?.get("senderId")?.toString()
                    ?: json.getString("senderId")
                    ?: run {
                        println("⚠️ Missing senderId in message")
                        return
                    }

                // Make sure you have the sender's information
                if (!users.containsKey(senderId)) {
                    println("⚠️ Unknown sender: $senderId")
                    return
                }

                val senderName = users[senderId] ?: "Unknown"

                when (route) {
                    "message" -> {
                        val message = json.getString("message")
                        val messageId = json.getString("messageId") ?: generateMessageId()

                        // save messages to history
                        messages[messageId] = senderId to message

                        // Prepare a forwarding message
                        val forwardMsg = JsonObject().apply {
                            put("message", message)
                            put("messageId", messageId)
                            put("sender", senderName)
                            put("senderId", senderId)
                            put("timestamp", System.currentTimeMillis())
                        }

                        sendToQueue(toId, forwardMsg)
                    }

                    "file" -> {
                        val fileName = json.getString("file")
                        val data = json.getString("data")

                        val forwardMsg = JsonObject().apply {
                            put("file", fileName)
                            put("data", data)
                            put("sender", senderName)
                            put("senderId", senderId)
                            put("timestamp", System.currentTimeMillis())
                        }

                        sendToQueue(toId, forwardMsg)
                    }

                    "image" -> {
                        val imageData = json.getString("image")

                        val forwardMsg = JsonObject().apply {
                            put("image", imageData)
                            put("sender", senderName)
                            put("senderId", senderId)
                            put("timestamp", System.currentTimeMillis())
                        }

                        sendToQueue(toId, forwardMsg)
                    }

                    "edit" -> {
                        val originalMessageId = json.getString("originalMessageId")
                        val newContent = json.getString("newMessage")
                        val senderId = json.getString("senderId") ?: return

                        // Check if the old message exists and belongs to the sender
                        messages[originalMessageId]?.let { (originalSender, _) ->
                            if (originalSender == senderId) {
                                // New content updates
                                messages[originalMessageId] = originalSender to newContent

                                // Prepare a forwarding message
                                val forwardMsg = JsonObject().apply {
                                    put("originalMessageId", originalMessageId)
                                    put("newMessage", newContent)
                                    put("sender", users[senderId] ?: "Unknown")
                                    put("senderId", senderId)
                                    put("timestamp", System.currentTimeMillis())
                                }

                                sendToQueue(toId, forwardMsg)
                            } else {
                                println("⚠️ Unauthorized edit attempt by $senderId for message $originalMessageId")
                            }
                        } ?: run {
                            println("⚠️ Original message $originalMessageId not found for edit")
                        }
                    }
                }
            } catch (e: Exception) {
                println("❌ Error processing message: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun sendToQueue(
        toId: String,
        message: JsonObject
    ) {
        try {
            val queueName = "chat.to.$toId"
            channel.queueDeclare(queueName, false, false, false, null)
            channel.basicPublish("", queueName, null, message.encode().toByteArray())
            println("📤 Forwarded message to $queueName")
        } catch (e: Exception) {
            println("❌ Failed to forward message to $toId: ${e.message}")
        }
    }

    override fun stop() {
        try {
            channel.close()
            connection.close()
            println("Server stopped.")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}