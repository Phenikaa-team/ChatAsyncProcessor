package com.chat.async.app.verticle

import com.chat.async.app.*
import com.chat.async.app.ui.ChatUI
import com.chat.async.app.ui.extension.MessageNode
import com.rabbitmq.client.*
import io.vertx.core.AbstractVerticle
import io.vertx.core.json.JsonObject
import javafx.application.Platform
import java.util.concurrent.ConcurrentHashMap

class ClientVerticle(
    private val clientName: String
) : AbstractVerticle() {

    var ui: ChatUI? = null
    private val sentMessages = ConcurrentHashMap<String, String>()
    private var currentUserId: String = ""

    override fun start() {
        ui = ChatUI(
            onSend = { toId, msg ->
                val messageId = generateMessageId()
                sentMessages[messageId] = msg
                val json = JsonObject(
                    mapOf(
                        "toId" to toId,
                        "message" to msg,
                        "messageId" to messageId
                    )
                )
                sendToRabbitMQ("message", json.encode())
            },
            onSendFile = { toId, name, bytes ->
                val json = JsonObject(
                    mapOf(
                        "toId" to toId,
                        "file" to name,
                        "data" to bytes.encodeBase64()
                    )
                )
                sendToRabbitMQ("file", json.encode())
            },
            onSendImage = { toId, bytes ->
                val json = JsonObject(
                    mapOf(
                        "toId" to toId,
                        "image" to bytes.encodeBase64()
                    )
                )
                sendToRabbitMQ("image", json.encode())
            },
            onEditMessage = { messageId, newContent ->
                sentMessages[messageId]?.let {
                    val json = JsonObject(
                        mapOf(
                            "originalMessageId" to messageId,
                            "newMessage" to newContent
                        )
                    )
                    sendToRabbitMQ("edit", json.encode())
                }
            }
        )

        ui?.onRegister = { (name, uuid) ->
            currentUserId = uuid
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
            override fun handleDelivery(
                tag: String?,
                env: Envelope?,
                props: AMQP.BasicProperties?,
                body: ByteArray?
            ) {
                if (props?.correlationId == uuid) {
                    val json = JsonObject(String(body!!))
                    val id = json.getString("userId")
                    val username2 = json.getString("username")

                    Platform.runLater {
                        ui?.setUserId(id, username2)
                        ("Your ID: $id").appendSystemMessage(ui?.chatArea!!)
                    }

                    setupReceiver(id, ui!!)
                }
            }
        }

        ch.basicConsume(replyQueue, true, consumer)
    }

    private fun setupReceiver(id: String, ui: ChatUI) {
        val factory = ConnectionFactory().apply {
            host = System.getenv("RABBITMQ_HOST") ?: "localhost"
        }
        val conn = factory.newConnection()
        val ch = conn.createChannel()
        val queue = "chat.to.$id"

        // Delete old queues if they exist to avoid errors
        try {
            ch.queueDelete(queue)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        ch.queueDeclare(queue, false, false, false, null)

        println("👂 Listening on queue: $queue")

        ch.basicConsume(queue, true, object : DefaultConsumer(ch) {
            override fun handleDelivery(tag: String?, env: Envelope?, props: AMQP.BasicProperties?, body: ByteArray?) {
                try {
                    val json = JsonObject(String(body!!))
                    println("📩 Received message: ${json.encodePrettily()}")

                    Platform.runLater {
                        when {
                            json.containsKey("message") -> handleTextMessage(json)
                            json.containsKey("file") -> handleFileMessage(json)
                            json.containsKey("image") -> handleImageMessage(json)
                            json.containsKey("newMessage") -> handleEditMessage(json)
                            else -> println("⚠️ Unknown message type")
                        }
                    }
                } catch (e: Exception) {
                    println("❌ Error processing received message: ${e.message}")
                    e.printStackTrace()
                }
            }

            private fun handleTextMessage(json: JsonObject) {
                val sender = json.getString("sender", "Unknown")
                val message = json.getString("message")
                val isOwn = json.getString("senderId") == currentUserId

                ui.addMessageToChat(
                    sender = sender,
                    content = message,
                    type = MessageNode.MessageType.TEXT,
                    isOwnMessage = isOwn,
                    messageId = json.getString("messageId")
                )
            }

            private fun handleFileMessage(json: JsonObject) {
                val sender = json.getString("sender", "Unknown")
                val fileName = json.getString("file")
                val bytes = json.getString("data").decodeBase64()
                val isOwn = json.getString("senderId") == currentUserId

                ui.showReceivedFile(sender, fileName, bytes)
            }

            private fun handleImageMessage(json: JsonObject) {
                val sender = json.getString("sender", "Unknown")
                val bytes = json.getString("image").decodeBase64()
                val isOwn = json.getString("senderId") == currentUserId

                ui.showReceivedImage(sender, bytes)
            }

            private fun handleEditMessage(json: JsonObject) {
                val originalMessageId = json.getString("originalMessageId")
                val newContent = json.getString("newMessage")
                val sender = json.getString("sender", "Unknown")
                val isOwn = json.getString("senderId") == currentUserId

                // Find the old message in the chatArea to replace it
                Platform.runLater {
                    val items = ui.chatArea.items
                    for (i in 0 until items.size) {
                        val node = items[i]
                        if (node.messageId == originalMessageId) {
                            items[i] = MessageNode(
                                sender = sender,
                                content = newContent,
                                type = MessageNode.MessageType.TEXT,
                                isOwnMessage = isOwn,
                                messageId = originalMessageId,
                                onEdit = { newText ->
                                    val editJson = JsonObject().apply {
                                        put("originalMessageId", originalMessageId)
                                        put("newMessage", newText)
                                    }
                                    sendToRabbitMQ("edit", editJson.encode())
                                }
                            )
                            break
                        }
                    }
                }
            }
        })
    }

    private fun sendToRabbitMQ(routingKey: String, data: String) {
        println("📤 Sending to $routingKey: ${data.take(100)}...")
        try {
            val factory = ConnectionFactory().apply {
                host = System.getenv("RABBITMQ_HOST") ?: "localhost"
            }
            val conn = factory.newConnection()
            val ch = conn.createChannel()

            val props = AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .headers(mapOf("senderId" to currentUserId))
                .build()

            ch.basicPublish(EXCHANGE, routingKey, props, data.toByteArray())
            conn.close()
            println("✅ Sent successfully")
        } catch (e: Exception) {
            println("❌ Failed to send message: ${e.message}")
            e.printStackTrace()
        }
    }
}