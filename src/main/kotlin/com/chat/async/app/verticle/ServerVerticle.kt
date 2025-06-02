package com.chat.async.app.verticle

import com.chat.async.app.helper.EXCHANGE
import com.chat.async.app.helper.generateMessageId
import com.chat.async.app.helper.generateUserId
import com.chat.async.app.monitoring.MonitoringIntegration
import com.chat.async.app.ui.group.ChatGroup
import com.rabbitmq.client.*
import io.vertx.core.AbstractVerticle
import io.vertx.core.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

class ServerVerticle : AbstractVerticle() {
    private val messages = ConcurrentHashMap<String, Pair<String, String>>()
    private val users = ConcurrentHashMap<String, String>()
    private val groups = ConcurrentHashMap<String, ChatGroup>()

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
                setupQueue("chat.create_group", "create_group", createGroupOperationConsumer())
                setupQueue("chat.join_group", "join_group", createGroupOperationConsumer())
                setupQueue("chat.leave_group", "leave_group", createGroupOperationConsumer())

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
        override fun handleDelivery(tag: String?, env: Envelope?, props: AMQP.BasicProperties?, body: ByteArray?) {
            val json = JsonObject(String(body!!))
            val name = json.getString("username")
            val id = json.getString("uuid") ?: generateUserId()

            if (users.containsKey(id)) {
                return
            }

            users[id] = name
            println("Registered user $name with ID $id")

            // Gửi phản hồi cho client
            val replyData = JsonObject()
                .put("userId", id)
                .put("username", name)
                .encode()

            val replyProps = AMQP.BasicProperties.Builder()
                .correlationId(props?.correlationId)
                .build()
            channel.basicPublish("", props?.replyTo, replyProps, replyData.toByteArray())

            MonitoringIntegration.registerUser(id, name)
        }
    }

    private fun createGroupOperationConsumer() = object : DefaultConsumer(channel) {
        override fun handleDelivery(
            tag: String?,
            env: Envelope?,
            props: AMQP.BasicProperties?,
            body: ByteArray?
        ) {
            try {
                val json = JsonObject(String(body!!))
                val route = env?.routingKey ?: return
                val senderId = props?.headers?.get("senderId")?.toString() ?: return
                val senderName = users[senderId] ?: "Unknown"

                when (route) {
                    "create_group" -> {
                        val groupId = json.getString("groupId")
                        val groupName = json.getString("groupName")
                        val createdBy = json.getString("createdBy")

                        // create a new group
                        val group = ChatGroup(
                            id = groupId,
                            name = groupName,
                            createdBy = createdBy
                        )
                        group.members.add(createdBy)
                        groups[groupId] = group

                        // Send notifications to creators
                        val responseMsg = JsonObject().apply {
                            put("groupCreated", true)
                            put("groupId", groupId)
                            put("groupName", groupName)
                            put("createdBy", createdBy)
                            put("timestamp", System.currentTimeMillis())
                        }

                        sendToQueue(createdBy, responseMsg)
                        println("✅ Group created: $groupName (ID: $groupId) by $senderName")
                        MonitoringIntegration.registerGroup(groupId, groupName)
                    }

                    "join_group" -> {
                        val groupId = json.getString("groupId")
                        val group = groups[groupId]

                        if (group != null) {
                            group.members.add(senderId)

                            // Send notifications to users who have just joined
                            val joinResponseMsg = JsonObject().apply {
                                put("groupJoined", true)
                                put("groupId", groupId)
                                put("groupName", group.name)
                                put("joinedBy", senderId)
                                put("joinerName", senderName)
                                put("timestamp", System.currentTimeMillis())
                            }

                            sendToQueue(senderId, joinResponseMsg)

                            // Notify all other members in the group
                            group.members.forEach { memberId ->
                                if (memberId != senderId) {
                                    val notifyMsg = JsonObject().apply {
                                        put("groupJoined", true)
                                        put("groupId", groupId)
                                        put("groupName", group.name)
                                        put("joinedBy", senderId)
                                        put("joinerName", senderName)
                                        put("timestamp", System.currentTimeMillis())
                                    }
                                    sendToQueue(memberId, notifyMsg)
                                }
                            }

                            println("✅ User $senderName joined group: ${group.name}")
                        } else {
                            // Group not found
                            val errorMsg = JsonObject().apply {
                                put("error", true)
                                put("message", "Group not found: $groupId")
                                put("timestamp", System.currentTimeMillis())
                            }
                            sendToQueue(senderId, errorMsg)
                        }
                    }

                    "leave_group" -> {
                        val groupId = json.getString("groupId")
                        val group = groups[groupId]

                        if (group != null && group.members.contains(senderId)) {
                            group.members.remove(senderId)

                            // Send notifications to users who have just left
                            val leaveResponseMsg = JsonObject().apply {
                                put("groupLeft", true)
                                put("groupId", groupId)
                                put("groupName", group.name)
                                put("leftBy", senderId)
                                put("leaverName", senderName)
                                put("timestamp", System.currentTimeMillis())
                            }

                            sendToQueue(senderId, leaveResponseMsg)

                            // Notify the rest of the members
                            group.members.forEach { memberId ->
                                val notifyMsg = JsonObject().apply {
                                    put("groupLeft", true)
                                    put("groupId", groupId)
                                    put("groupName", group.name)
                                    put("leftBy", senderId)
                                    put("leaverName", senderName)
                                    put("timestamp", System.currentTimeMillis())
                                }
                                sendToQueue(memberId, notifyMsg)
                            }

                            // Delete a group if there are no more members
                            if (group.members.isEmpty()) {
                                groups.remove(groupId)
                                println("🗑️ Group ${group.name} deleted (no members left)")
                            }

                            println("👋 User $senderName left group: ${group.name}")
                            MonitoringIntegration.removeGroup(groupId)
                        }
                    }
                }
            } catch (e: Exception) {
                println("❌ Error processing group operation: ${e.message}")
                e.printStackTrace()
            }
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

                        // Save messages to history
                        messages[messageId] = senderId to message

                        // Check if toId is a group
                        val isGroup = groups.containsKey(toId)

                        if (isGroup) {
                            // Send a message to all members of the group
                            val group = groups[toId]!!
                            group.members.forEach { memberId ->
                                if (memberId != senderId) { // Don't send it back to the sender
                                    val forwardMsg = JsonObject().apply {
                                        put("message", message)
                                        put("messageId", messageId)
                                        put("sender", senderName)
                                        put("senderId", senderId)
                                        put("isGroup", true)
                                        put("targetId", toId)
                                        put("timestamp", System.currentTimeMillis())
                                    }
                                    sendToQueue(memberId, forwardMsg)
                                }
                            }
                            println("📤 Group message sent to ${group.members.size} members in group $toId")
                        } else {
                            // Send personal messages as usual
                            val forwardMsg = JsonObject().apply {
                                put("message", message)
                                put("messageId", messageId)
                                put("sender", senderName)
                                put("senderId", senderId)
                                put("isGroup", false)
                                put("timestamp", System.currentTimeMillis())
                            }
                            sendToQueue(toId, forwardMsg)
                        }
                        MonitoringIntegration.trackMessageReceived(senderId, toId, "text")
                    }

                    "file" -> {
                        val fileName = json.getString("file")
                        val data = json.getString("data")

                        val isGroup = groups.containsKey(toId)
                        val forwardMsg = JsonObject().apply {
                            put("file", fileName)
                            put("data", data)
                            put("sender", senderName)
                            put("senderId", senderId)
                            put("isGroup", isGroup)
                            if (isGroup) {
                                put("targetId", toId)
                            }
                            put("timestamp", System.currentTimeMillis())
                        }

                        if (isGroup) {
                            groups[toId]?.members?.forEach { memberId ->
                                sendToQueue(memberId, forwardMsg)
                            }
                            println("📤 Group file sent to ${groups[toId]?.members?.size} members in group $toId")
                        } else {
                            sendToQueue(toId, forwardMsg)
                        }
                        MonitoringIntegration.trackFileReceived(senderId, toId, "file")
                    }

                    "image" -> {
                        val imageData = json.getString("image")

                        val isGroup = groups.containsKey(toId)
                        val forwardMsg = JsonObject().apply {
                            put("image", imageData)
                            put("sender", senderName)
                            put("senderId", senderId)
                            put("isGroup", isGroup)
                            if (isGroup) put("targetId", toId)
                            put("timestamp", System.currentTimeMillis())
                        }

                        if (isGroup) {
                            groups[toId]?.members?.forEach { memberId ->
                                sendToQueue(memberId, forwardMsg)
                            }
                            println("📤 Group image sent to ${groups[toId]?.members?.size} members in group $toId")
                        } else {
                            sendToQueue(toId, forwardMsg)
                        }
                        MonitoringIntegration.trackImageReceived(senderId, toId, "image")
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
                                MonitoringIntegration.trackMessageReceived(toId, senderId, "text")
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