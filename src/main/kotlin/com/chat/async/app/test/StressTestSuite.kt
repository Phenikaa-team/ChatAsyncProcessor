package com.chat.async.app.test

import com.chat.async.app.helper.getMemoryUsage
import io.vertx.core.Vertx
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class StressTestSuite {

    data class TestConfig(
        val clientCount: Int = 50,
        val messagesPerClient: Int = 100,
        val testDurationSeconds: Int = 60,
        val messageDelayMs: Long = 100,
        val enableFileTransfer: Boolean = true,
        val enableImageTransfer: Boolean = true,
        val enableGroupTesting: Boolean = true,
        val groupCount: Int = 5,
        val membersPerGroup: Int = 10,
        val rabbitmqHost: String = System.getenv("RABBITMQ_HOST") ?: "localhost"
    )

    data class TestResults(
        val totalMessages: Int,
        val successfulMessages: Int,
        val failedMessages: Int,
        val averageLatency: Double,
        val maxLatency: Long,
        val minLatency: Long,
        val messagesPerSecond: Double,
        val testDurationMs: Long,
        val memoryUsageMB: Double,
        val errors: List<String>
    )

    private val messagesSent = AtomicInteger(0)
    private val messagesReceived = AtomicInteger(0)
    private val messagesFailed = AtomicInteger(0)
    private val latencies = mutableListOf<Long>()
    private val errors = mutableListOf<String>()

    private val vertx = Vertx.vertx()
    private val clients = mutableListOf<StressTestClient>()

    suspend fun runStressTest(config: TestConfig): TestResults {
        println("🚀 Starting stress test with ${config.clientCount} clients...")
        println("📊 Configuration: $config")

        val startTime = System.currentTimeMillis()
        val startMemory = getMemoryUsage()

        try {
            createClients(config)
            delay(2000) // Wait for registration

            if (config.enableGroupTesting) {
                createGroups(config)
                delay(1000)
            }

            runConcurrentTests(config)

            cleanup()

        } catch (e: Exception) {
            errors.add("Test execution error: ${e.message}")
            e.printStackTrace()
        }

        val endTime = System.currentTimeMillis()
        val endMemory = getMemoryUsage()
        val testDuration = endTime - startTime

        return calculateResults(testDuration, endMemory - startMemory)
    }

    private suspend fun createClients(config: TestConfig) {
        println("👥 Creating ${config.clientCount} test clients...")

        withContext(Dispatchers.IO) {
            val jobs = (1..config.clientCount).map { clientId ->
                async {
                    try {
                        val client = StressTestClient(
                            clientId = "stress_client_$clientId",
                            username = "StressUser$clientId",
                            rabbitmqHost = config.rabbitmqHost
                        )
                        client.connect()
                        clients.add(client)
                        delay(50) // Stagger connections
                    } catch (e: Exception) {
                        errors.add("Failed to create client $clientId: ${e.message}")
                    }
                }
            }
            jobs.awaitAll()
        }

        println("✅ Created ${clients.size} clients successfully")
    }

    private suspend fun createGroups(config: TestConfig) {
        println("🏘️ Creating ${config.groupCount} test groups...")

        repeat(config.groupCount) { groupIndex ->
            try {
                val groupId = "stress_group_$groupIndex"
                val groupName = "StressGroup$groupIndex"

                // Select random creator
                val creator = clients.random()
                creator.createGroup(groupId, groupName)

                // Add random members
                val members = clients.shuffled().take(config.membersPerGroup)
                members.forEach { member ->
                    if (member != creator) {
                        delay(100)
                        member.joinGroup(groupId)
                    }
                }

                delay(200)
            } catch (e: Exception) {
                errors.add("Failed to create group $groupIndex: ${e.message}")
            }
        }
    }

    private suspend fun runConcurrentTests(config: TestConfig) {
        println("⚡ Running concurrent stress tests...")

        val jobs = mutableListOf<Job>()

        // Message flood test
        jobs.add(runBlocking { launch { runMessageFloodTest(config) }})

        // File transfer test
        if (config.enableFileTransfer) {
            jobs.add(runBlocking { launch { runFileTransferTest(config) }})
        }

        // Image transfer test
        if (config.enableImageTransfer) {
            jobs.add(runBlocking {launch { runImageTransferTest(config) }})
        }

        // Group messaging test
        if (config.enableGroupTesting) {
            jobs.add(runBlocking {launch { runGroupMessagingTest(config) }})
        }

        // Connection stability test
        jobs.add(runBlocking {launch { runConnectionStabilityTest(config) }})

        // Wait for all tests or timeout
        withTimeoutOrNull(config.testDurationSeconds * 1000L) {
            jobs.joinAll()
        } ?: run {
            jobs.forEach { it.cancel() }
            println("⏰ Tests stopped due to timeout")
        }
    }

    private suspend fun runMessageFloodTest(config: TestConfig) {
        println("💬 Starting message flood test...")

        withContext(Dispatchers.IO) {
            val jobs = clients.map { client ->
                async {
                    repeat(config.messagesPerClient) { msgIndex ->
                        try {
                            val startTime = System.nanoTime()
                            val target = clients.random()
                            val message = "Stress test message $msgIndex from ${client.clientId}"

                            client.sendMessage(target.clientId, message) {
                                val latency = (System.nanoTime() - startTime) / 1_000_000
                                synchronized(latencies) {
                                    latencies.add(latency)
                                }
                                messagesReceived.incrementAndGet()
                            }

                            messagesSent.incrementAndGet()
                            delay(config.messageDelayMs)

                        } catch (e: Exception) {
                            messagesFailed.incrementAndGet()
                            errors.add("Message flood error: ${e.message}")
                        }
                    }
                }
            }
            jobs.awaitAll()
        }
    }

    private suspend fun runFileTransferTest(config: TestConfig) {
        println("📁 Starting file transfer stress test...")

        val testFileData = ByteArray(1024) { Random.nextInt(256).toByte() } // 1KB test file

        withContext(Dispatchers.IO) {
            repeat(config.clientCount / 5) { // Less frequent than messages
                try {
                    val sender = clients.random()
                    val receiver = clients.random()

                    sender.sendFile(receiver.clientId, "stress_test_file_$it.dat", testFileData)
                    delay(1000) // Files take longer

                } catch (e: Exception) {
                    errors.add("File transfer error: ${e.message}")
                }
            }
        }
    }

    private suspend fun runImageTransferTest(config: TestConfig) {
        println("🖼️ Starting image transfer stress test...")

        val testImageData = ByteArray(2048) { Random.nextInt(256).toByte() } // 2KB test image

        withContext(Dispatchers.IO) {
            repeat(config.clientCount / 10) { // Even less frequent
                try {
                    val sender = clients.random()
                    val receiver = clients.random()

                    sender.sendImage(receiver.clientId, testImageData)
                    delay(1500)

                } catch (e: Exception) {
                    errors.add("Image transfer error: ${e.message}")
                }
            }
        }
    }

    private suspend fun runGroupMessagingTest(config: TestConfig) {
        println("👨‍👩‍👧‍👦 Starting group messaging stress test...")

        withContext(Dispatchers.IO) {
            repeat(config.messagesPerClient / 5) { msgIndex ->
                try {
                    val sender = clients.random()
                    val groupId = "stress_group_${Random.nextInt(config.groupCount)}"
                    val message = "Group stress message $msgIndex"

                    sender.sendMessage(groupId, message)
                    delay(config.messageDelayMs * 2) // Group messages are more expensive

                } catch (e: Exception) {
                    errors.add("Group messaging error: ${e.message}")
                }
            }
        }
    }

    private suspend fun runConnectionStabilityTest(config: TestConfig) {
        println("🔗 Starting connection stability test...")

        val testDuration = config.testDurationSeconds * 1000L
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < testDuration) {
            try {
                // Randomly disconnect and reconnect some clients
                val clientsToTest = clients.shuffled().take(3)

                clientsToTest.forEach { client ->
                    if (Random.nextBoolean()) {
                        runBlocking {
                            launch {
                                client.disconnect()
                                delay(Random.nextLong(100, 500))
                                client.connect()
                            }
                        }
                    }
                }

                delay(5000) // Check every 5 seconds

            } catch (e: Exception) {
                errors.add("Connection stability error: ${e.message}")
            }
        }
    }

    private fun calculateResults(testDurationMs: Long, memoryDelta: Double): TestResults {
        val avgLatency = if (latencies.isNotEmpty()) {
            latencies.average()
        } else 0.0

        val maxLatency = latencies.maxOrNull() ?: 0L
        val minLatency = latencies.minOrNull() ?: 0L

        val messagesPerSecond = if (testDurationMs > 0) {
            (messagesSent.get() * 1000.0) / testDurationMs
        } else 0.0

        return TestResults(
            totalMessages = messagesSent.get(),
            successfulMessages = messagesReceived.get(),
            failedMessages = messagesFailed.get(),
            averageLatency = avgLatency,
            maxLatency = maxLatency,
            minLatency = minLatency,
            messagesPerSecond = messagesPerSecond,
            testDurationMs = testDurationMs,
            memoryUsageMB = memoryDelta,
            errors = errors.toList()
        )
    }

    private suspend fun cleanup() {
        println("🧹 Cleaning up test resources...")

        withContext(Dispatchers.IO) {
            clients.forEach { client ->
                try {
                    client.disconnect()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        vertx.close()
    }
}
