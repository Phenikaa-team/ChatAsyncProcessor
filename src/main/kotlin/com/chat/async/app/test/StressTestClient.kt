package com.chat.async.app.test

import com.chat.async.app.helper.EXCHANGE
import com.chat.async.app.helper.encodeBase64
import com.chat.async.app.helper.generateMessageId
import com.rabbitmq.client.*
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap


class StressTestClient(
    val clientId: String,
    private val username: String,
    private val rabbitmqHost: String
) {
    private var connection: Connection? = null
    private var channel: Channel? = null
    private val messageCallbacks = ConcurrentHashMap<String, () -> Unit>()

    suspend fun connect() {
        withContext(Dispatchers.IO) {
            try {
                val factory = ConnectionFactory().apply {
                    host = rabbitmqHost
                    connectionTimeout = 5000
                    requestedHeartbeat = 30
                }

                connection = factory.newConnection()
                channel = connection?.createChannel()

                registerWithServer()
                setupReceiver()

            } catch (e: Exception) {
                throw Exception("Failed to connect client $clientId: ${e.message}")
            }
        }
    }

    private fun registerWithServer() {
        val ch = channel ?: return
        val replyQueue = ch.queueDeclare().queue

        val props = AMQP.BasicProperties.Builder()
            .correlationId(clientId)
            .replyTo(replyQueue)
            .build()

        val registrationData = JsonObject()
            .put("username", username)
            .put("uuid", clientId)
            .encode()

        ch.basicPublish(EXCHANGE, "register", props, registrationData.toByteArray())

        ch.basicConsume(replyQueue, true, object : DefaultConsumer(ch) {
            override fun handleDelivery(tag: String?, env: Envelope?, props: AMQP.BasicProperties?, body: ByteArray?) {
                if (props?.correlationId == clientId) {
                    println("✅ Client $clientId registered successfully")
                }
            }
        })
    }

    private fun setupReceiver() {
        val ch = channel ?: return
        val queue = "chat.to.$clientId"

        ch.queueDeclare(queue, false, false, false, null)

        ch.basicConsume(queue, true, object : DefaultConsumer(ch) {
            override fun handleDelivery(tag: String?, env: Envelope?, props: AMQP.BasicProperties?, body: ByteArray?) {
                try {
                    val json = JsonObject(String(body!!))
                    val messageId = json.getString("messageId")

                    // Trigger callback if exists
                    messageId?.let { id ->
                        messageCallbacks.remove(id)?.invoke()
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })
    }

    fun sendMessage(toId: String, message: String, onReceived: (() -> Unit)? = null) {
        val ch = channel ?: return
        val messageId = generateMessageId()

        onReceived?.let { callback ->
            messageCallbacks[messageId] = callback
        }

        val json = JsonObject(mapOf(
            "toId" to toId,
            "message" to message,
            "messageId" to messageId
        ))

        val props = AMQP.BasicProperties.Builder()
            .contentType("application/json")
            .headers(mapOf("senderId" to clientId))
            .build()

        ch.basicPublish(EXCHANGE, "message", props, json.encode().toByteArray())
    }

    fun sendFile(toId: String, fileName: String, fileData: ByteArray) {
        val ch = channel ?: return

        val json = JsonObject(mapOf(
            "toId" to toId,
            "file" to fileName,
            "data" to fileData.encodeBase64()
        ))

        val props = AMQP.BasicProperties.Builder()
            .contentType("application/json")
            .headers(mapOf("senderId" to clientId))
            .build()

        ch.basicPublish(EXCHANGE, "file", props, json.encode().toByteArray())
    }

    fun sendImage(toId: String, imageData: ByteArray) {
        val ch = channel ?: return

        val json = JsonObject(mapOf(
            "toId" to toId,
            "image" to imageData.encodeBase64()
        ))

        val props = AMQP.BasicProperties.Builder()
            .contentType("application/json")
            .headers(mapOf("senderId" to clientId))
            .build()

        ch.basicPublish(EXCHANGE, "image", props, json.encode().toByteArray())
    }

    fun createGroup(groupId: String, groupName: String) {
        val ch = channel ?: return

        val json = JsonObject(mapOf(
            "groupId" to groupId,
            "groupName" to groupName,
            "createdBy" to clientId
        ))

        val props = AMQP.BasicProperties.Builder()
            .contentType("application/json")
            .headers(mapOf("senderId" to clientId))
            .build()

        ch.basicPublish(EXCHANGE, "create_group", props, json.encode().toByteArray())
    }

    fun joinGroup(groupId: String) {
        val ch = channel ?: return

        val json = JsonObject(mapOf("groupId" to groupId))

        val props = AMQP.BasicProperties.Builder()
            .contentType("application/json")
            .headers(mapOf("senderId" to clientId))
            .build()

        ch.basicPublish(EXCHANGE, "join_group", props, json.encode().toByteArray())
    }

    fun disconnect() {
        try {
            channel?.close()
            connection?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}