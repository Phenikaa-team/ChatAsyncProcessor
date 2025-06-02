package com.chat.async.app.verticle

import com.chat.async.app.helper.*
import com.chat.async.app.helper.enums.MessageType
import com.chat.async.app.monitoring.MonitoringIntegration
import com.chat.async.app.ui.ChatUI
import com.chat.async.app.ui.group.ChatGroup
import com.chat.async.app.ui.node.MessageNode
import com.rabbitmq.client.*
import io.vertx.core.AbstractVerticle
import io.vertx.core.json.JsonObject
import javafx.application.Platform
import java.util.concurrent.ConcurrentHashMap

class ClientVerticle : AbstractVerticle() {

    var ui: ChatUI? = null
    private val sentMessages = ConcurrentHashMap<String, String>()
    private var currentUserId: String = ""

    override fun start() {
        Platform.runLater {
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
                    val json = JsonObject(
                        mapOf(
                            "messageId" to messageId,
                            "newContent" to newContent
                        )
                    )
                    sendToRabbitMQ("edit", json.encode())
                },
                onCreateGroup = { group ->
                    val json = JsonObject(
                        mapOf(
                            "groupId" to group.id,
                            "groupName" to group.name,
                            "createdBy" to group.createdBy
                        )
                    )
                    sendToRabbitMQ("create_group", json.encode())
                },
                onJoinGroup = { groupId ->
                    val json = JsonObject(
                        mapOf(
                            "groupId" to groupId
                        )
                    )
                    sendToRabbitMQ("join_group", json.encode())
                },
                onLeaveGroup = { groupId ->
                    val json = JsonObject(
                        mapOf(
                            "groupId" to groupId
                        )
                    )
                    sendToRabbitMQ("leave_group", json.encode())
                }
            )

            ui?.onRegister = { (name, uuid) ->
                currentUserId = uuid
                registerUser(name, uuid)
            }
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
        MonitoringIntegration.registerUser(uuid, username)

        val consumer = object : DefaultConsumer(ch) {
            override fun handleDelivery(tag: String?, env: Envelope?, props: AMQP.BasicProperties?, body: ByteArray?) {
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
                            json.containsKey("newContent") -> handleEditMessage(json)
                            json.containsKey("groupCreated") -> handleGroupCreated(json)
                            json.containsKey("groupJoined") -> handleGroupJoined(json)
                            json.containsKey("groupLeft") -> handleGroupLeft(json)
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
                val isGroup = json.getBoolean("isGroup", false)
                val targetId = json.getString("targetId", "")

                val displaySender = if (isGroup) "$sender@$targetId" else sender

                ui.addMessageToChat(
                    sender = displaySender,
                    content = message,
                    type = MessageType.TEXT,
                    isOwnMessage = isOwn,
                    messageId = json.getString("messageId")
                )

                MonitoringIntegration.trackMessageSent(sender, targetId, "text")
            }

            private fun handleFileMessage(json: JsonObject) {
                val sender = json.getString("sender", "Unknown")
                val fileName = json.getString("file")
                val bytes = json.getString("data").decodeBase64()
                val isOwn = json.getString("senderId") == currentUserId
                val isGroup = json.getBoolean("isGroup", false)
                val targetId = json.getString("targetId", "")

                val displaySender = if (isGroup) "$sender@$targetId" else sender

                ui.addMessageToChat(displaySender, fileName, MessageType.FILE, isOwn, null, bytes)
                MonitoringIntegration.trackFileSent(sender, targetId, "file")
            }

            private fun handleImageMessage(json: JsonObject) {
                val sender = json.getString("sender", "Unknown")
                val bytes = json.getString("image").decodeBase64()
                val isOwn = json.getString("senderId") == currentUserId
                val isGroup = json.getBoolean("isGroup", false)
                val targetId = json.getString("targetId", "")

                val displaySender = if (isGroup) "$sender@$targetId" else sender

                ui.addMessageToChat(displaySender, "image", MessageType.IMAGE, isOwn, null, bytes)
                MonitoringIntegration.trackImageSent(sender, targetId, "image")
            }

            private fun handleEditMessage(json: JsonObject) {
                val messageId = json.getString("messageId")
                val newContent = json.getString("newContent")
                val sender = json.getString("sender", "Unknown")
                val isOwn = json.getString("senderId") == currentUserId
                val isGroup = json.getBoolean("isGroup", false)
                val targetId = json.getString("targetId", "")

                val displaySender = if (isGroup) "$sender@$targetId" else sender

                // Find the old message in the chatArea to replace it
                Platform.runLater {
                    val items = ui.chatArea.items
                    for (i in 0 until items.size) {
                        val node = items[i]
                        if (node.messageId == messageId) {
                            items[i] = MessageNode(
                                sender = displaySender,
                                content = newContent,
                                type = MessageType.TEXT,
                                isOwnMessage = isOwn,
                                messageId = messageId,
                                onEdit = { newText ->
                                    val editJson = JsonObject().apply {
                                        put("messageId", messageId)
                                        put("newContent", newText)
                                    }
                                    sendToRabbitMQ("edit", editJson.encode())
                                }
                            )
                            break
                        }
                    }
                }
            }

            private fun handleGroupCreated(json: JsonObject) {
                val groupId = json.getString("groupId")
                val groupName = json.getString("groupName")
                val createdBy = json.getString("createdBy")

                val group = ChatGroup(
                    id = groupId,
                    name = groupName,
                    createdBy = createdBy
                )

                Platform.runLater {
                    ui.addGroup(group)
                    "✅ Group '${groupName}' created successfully (ID: ${groupId})".appendSystemMessage(ui.chatArea)
                }
            }

            private fun handleGroupJoined(json: JsonObject) {
                val groupId = json.getString("groupId")
                val groupName = json.getString("groupName", "Unknown Group")
                val joinedBy = json.getString("joinedBy", currentUserId)
                val joinerName = json.getString("joinerName", "Unknown User")

                if (joinedBy == currentUserId) {
                    Platform.runLater {
                        "✅ Successfully joined group '${groupName}' (ID: ${groupId})".appendSystemMessage(ui.chatArea)
                    }
                } else {
                    Platform.runLater {
                        "👋 ${joinerName} joined the group '${groupName}'".appendSystemMessage(ui.chatArea)
                    }
                }
            }

            private fun handleGroupLeft(json: JsonObject) {
                val groupId = json.getString("groupId")
                val groupName = json.getString("groupName", "Unknown Group")
                val leftBy = json.getString("leftBy", currentUserId)
                val leaverName = json.getString("leaverName", "Unknown User")

                if (leftBy == currentUserId) {
                    Platform.runLater {
                        "👋 You left the group '${groupName}' (ID: ${groupId})".appendSystemMessage(ui.chatArea)
                    }
                } else {
                    Platform.runLater {
                        "👋 $leaverName left the group '${groupName}'".appendSystemMessage(ui.chatArea)
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
            MonitoringIntegration.updateUserActivity(currentUserId)
            println("✅ Sent successfully")
        } catch (e: Exception) {
            println("❌ Failed to send message: ${e.message}")
            e.printStackTrace()
        }
    }
}