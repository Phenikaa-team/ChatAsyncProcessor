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

        channel.exchangeDeclare(EXCHANGE, "direct", true)

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
                try {
                    val json = JsonObject(String(body!!))
                    val toId = json.getString("toId")
                    println("Received ${envelope?.routingKey} for $toId")

                    // Tạo queue cho người nhận nếu chưa có
                    val toQueue = "chat.to.$toId"
                    channel.queueDeclare(toQueue, false, false, false, null)

                    // Giữ nguyên routingKey khi forward
                    channel.basicPublish("", toQueue,
                        AMQP.BasicProperties.Builder()
                            .contentType("application/json")
                            .build(),
                        body)
                    println("Forwarding message to $toId via queue $toQueue")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        channel.basicConsume(messageQueue, true, messageConsumer)
        channel.basicConsume(fileQueue, true, messageConsumer)
        channel.basicConsume(imageQueue, true, messageConsumer)

        println("Chat server connected to RabbitMQ")
    }

    override fun stop() {
        channel.close()
        connection.close()
    }
}