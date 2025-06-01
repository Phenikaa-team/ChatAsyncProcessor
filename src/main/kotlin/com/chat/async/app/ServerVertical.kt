package com.chat.async.app

import com.rabbitmq.client.*
import io.vertx.core.AbstractVerticle
import io.vertx.core.json.JsonObject

class ServerVertical : AbstractVerticle() {
    private val users = mutableMapOf<String, String>()
    private lateinit var connection: Connection
    private lateinit var channel: Channel

    override fun start() {
        try {
            initializeRabbitMQConnection()
            setupExchange()
            setupQueuesAndConsumers()
            println("✅ Chat server connected to RabbitMQ")
        } catch (e: Exception) {
            println("❌ Failed to start server: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun initializeRabbitMQConnection() {
        val factory = ConnectionFactory().apply {
            host = System.getenv("RABBITMQ_HOST") ?: "localhost"
        }
        connection = factory.newConnection()
        channel = connection.createChannel()
    }

    private fun setupExchange() {
        channel.exchangeDeclare(EXCHANGE, "direct", true)
    }

    private fun setupQueuesAndConsumers() {
        // Setup all queues
        val registerQueue = setupQueue("chat.register", "register")
        val messageQueue = setupQueue("chat.message", "message")
        val fileQueue = setupQueue("chat.file", "file")
        val imageQueue = setupQueue("chat.image", "image")

        // Setup consumers
        channel.basicConsume(registerQueue, true, createRegistrationConsumer())
        val messageConsumer = createMessageConsumer()

        listOf(messageQueue, fileQueue, imageQueue).forEach { queue ->
            channel.basicConsume(queue, true, messageConsumer)
        }
    }

    private fun setupQueue(
        queueName : String,
        routingKey : String
    ) : String {
        val queue = channel.queueDeclare(queueName, false, false, false, null).queue
        channel.queueBind(queue, EXCHANGE, routingKey)
        return queue
    }

    private fun createRegistrationConsumer() = object : DefaultConsumer(channel) {
        override fun handleDelivery(
            consumerTag: String?,
            envelope: Envelope?,
            properties: AMQP.BasicProperties?,
            body: ByteArray?
        ) {
            handleRegistrationRequest(properties, body)
        }
    }

    private fun handleRegistrationRequest(
        properties: AMQP.BasicProperties?,
        body: ByteArray?
    ) {
        try {
            val name = String(body!!)
            val id = generateUserId()
            users[id] = name
            println("User registered: $name ($id)")

            sendRegistrationResponse(properties, id)
        } catch (e: Exception) {
            println("Error processing registration: ${e.message}")
        }
    }

    private fun sendRegistrationResponse(
        properties: AMQP.BasicProperties?,
        userId: String
    ) {
        val replyProps = AMQP.BasicProperties.Builder()
            .correlationId(properties?.correlationId)
            .build()

        channel.basicPublish(
            "",
            properties?.replyTo,
            replyProps,
            userId.toByteArray()
        )
    }

    private fun createMessageConsumer() = object : DefaultConsumer(channel) {
        override fun handleDelivery(
            consumerTag: String?,
            envelope: Envelope?,
            properties: AMQP.BasicProperties?,
            body: ByteArray?
        ) {
            processIncomingMessage(envelope, body)
        }
    }

    private fun processIncomingMessage(
        envelope: Envelope?,
        body: ByteArray?
    ) {
        try {
            val json = JsonObject(String(body!!))
            val toId = json.getString("toId")
            val routingKey = envelope?.routingKey ?: "unknown"

            println("Received $routingKey message for $toId")
            forwardMessageToRecipient(toId, json, routingKey)
        } catch (e: Exception) {
            println("Error processing message: ${e.message}")
        }
    }

    private fun forwardMessageToRecipient(
        toId: String,
        json: JsonObject,
        routingKey: String
    ) {
        val toQueue = "chat.to.$toId"
        channel.queueDeclare(toQueue, false, false, false, null)

        val props = AMQP.BasicProperties.Builder()
            .contentType("application/json")
            .build()

        channel.basicPublish("", toQueue, props, json.encode().toByteArray())
        println("Forwarded message to $toId via queue $toQueue")
    }

    override fun stop() {
        try {
            channel.close()
            connection.close()
            println("Server resources cleaned up")
        } catch (e: Exception) {
            println("Error while closing connections: ${e.message}")
        }
    }
}