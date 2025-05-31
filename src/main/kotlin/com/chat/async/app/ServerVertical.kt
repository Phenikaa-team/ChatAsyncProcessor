package com.chat.async.app

import com.rabbitmq.client.*
import io.vertx.core.AbstractVerticle
import io.vertx.core.json.JsonObject

class ServerVertical : AbstractVerticle() {
    private val users = mutableMapOf<String, String>()

    private lateinit var connection: Connection
    private lateinit var channel: Channel

    override fun start() {
        val factory = ConnectionFactory()
        factory.host = System.getenv("RABBITMQ_HOST") ?: "localhost"

        connection = factory.newConnection()
        channel = connection.createChannel()

        channel.exchangeDeclarePassive(EXCHANGE)

        // register queue
        val registerQueue = channel.queueDeclare("chat.register", false, false, false, null).queue
        channel.queueBind(registerQueue, EXCHANGE, "register")

        // message queue
        val messageQueue = channel.queueDeclare("chat.message", false, false, false, null).queue
        channel.queueBind(messageQueue, EXCHANGE, "message")

        // register process
        val registerConsumer = object : DefaultConsumer(channel) {
            override fun handleDelivery(
                consumerTag: String?,
                envelope: Envelope?,
                properties: AMQP.BasicProperties?,
                body: ByteArray?
            ) {
                val name = String(body!!)
                val id = java.util.UUID.randomUUID().toString().substring(0, 6)
                users[id] = name
                println("User registered: $name ($id)")

                // Reply with ID by `BasicProperties`
                val replyProps = AMQP.BasicProperties.Builder()
                    .correlationId(properties?.correlationId)
                    .build()

                channel.basicPublish("", properties?.replyTo, replyProps, id.toByteArray())
            }
        }
        channel.basicConsume(registerQueue, true, registerConsumer)

        // message processor
        val messageConsumer = object : DefaultConsumer(channel) {
            override fun handleDelivery(
                consumerTag: String?,
                envelope: Envelope?,
                properties: AMQP.BasicProperties?,
                body: ByteArray?
            ) {
                val json = JsonObject(String(body!!))
                val toId = json.getString("toId")
                val content = json.getString("content")
                println("Message to $toId: $content")

                // send message queue
                val toQueue = "chat.to.$toId"
                channel.queueDeclare(toQueue, false, false, false, null)
                channel.basicPublish("", toQueue, null, content.toByteArray())
            }
        }
        channel.basicConsume(messageQueue, true, messageConsumer)

        println("Chat server connected to RabbitMQ")
    }

    override fun stop() {
        channel.close()
        connection.close()
    }
}