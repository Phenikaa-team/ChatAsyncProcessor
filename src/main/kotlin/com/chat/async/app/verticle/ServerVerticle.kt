package com.chat.async.app.verticle

import com.chat.async.app.EXCHANGE
import com.chat.async.app.generateUserId
import com.rabbitmq.client.*
import io.vertx.core.AbstractVerticle
import io.vertx.core.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

class ServerVerticle : AbstractVerticle() {
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

    private fun setupQueue(name: String, key: String, consumer: Consumer) {
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

            val replyProps = AMQP.BasicProperties.Builder()
                .correlationId(props?.correlationId)
                .build()
            channel.basicPublish("", props?.replyTo, replyProps, id.toByteArray())
        }
    }

    private fun createMessageConsumer() = object : DefaultConsumer(channel) {
        override fun handleDelivery(tag: String?, env: Envelope?, props: AMQP.BasicProperties?, body: ByteArray?) {
            val json = JsonObject(String(body!!))
            val toId = json.getString("toId")
            val route = env?.routingKey ?: "message"
            val queue = "chat.to.$toId"
            channel.queueDeclare(queue, false, false, false, null)
            channel.basicPublish("", queue, AMQP.BasicProperties.Builder().contentType("application/json").build(), body)
            println("Forwarded $route to $toId via $queue")
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