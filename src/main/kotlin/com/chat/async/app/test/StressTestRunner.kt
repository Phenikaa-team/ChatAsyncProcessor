package com.chat.async.app.test

import kotlinx.coroutines.*

class StressTestRunner {

//    companion object {
//        @JvmStatic
//        fun main(args: Array<String>) {
//            runBlocking {
//                val suite = StressTestSuite()
//
//                // Light load test
//                println("📊 Running LIGHT load test...")
//                var config = StressTestSuite.TestConfig(
//                    clientCount = 10,
//                    messagesPerClient = 50,
//                    testDurationSeconds = 30
//                )
//                var results = suite.runStressTest(config)
//                printResults("LIGHT LOAD", results)
//
//                delay(5000) // Rest between tests
//
//                // Medium load test
//                println("\n📊 Running MEDIUM load test...")
//                config = StressTestSuite.TestConfig(
//                    clientCount = 50,
//                    messagesPerClient = 100,
//                    testDurationSeconds = 60
//                )
//                results = suite.runStressTest(config)
//                printResults("MEDIUM LOAD", results)
//
//                delay(5000)
//
//                // Heavy load test
//                println("\n📊 Running HEAVY load test...")
//                config = StressTestSuite.TestConfig(
//                    clientCount = 100,
//                    messagesPerClient = 200,
//                    testDurationSeconds = 120,
//                    messageDelayMs = 50
//                )
//                results = suite.runStressTest(config)
//                printResults("HEAVY LOAD", results)
//            }
//        }
//
//        private fun printResults(testName: String, results: StressTestSuite.TestResults) {
//            println("\n" + "=".repeat(50))
//            println("📈 $testName TEST RESULTS")
//            println("=".repeat(50))
//            println("Total Messages: ${results.totalMessages}")
//            println("Successful: ${results.successfulMessages}")
//            println("Failed: ${results.failedMessages}")
//            println("Success Rate: ${((results.successfulMessages.toDouble() / results.totalMessages) * 100).format(2)}%")
//            println("Average Latency: ${results.averageLatency.format(2)} ms")
//            println("Min Latency: ${results.minLatency} ms")
//            println("Max Latency: ${results.maxLatency} ms")
//            println("Messages/Second: ${results.messagesPerSecond.format(2)}")
//            println("Test Duration: ${results.testDurationMs / 1000} seconds")
//            println("Memory Usage: ${results.memoryUsageMB.format(2)} MB")
//
//            if (results.errors.isNotEmpty()) {
//                println("\n❌ Errors encountered:")
//                results.errors.take(10).forEach { error ->
//                    println("  • $error")
//                }
//                if (results.errors.size > 10) {
//                    println("  ... and ${results.errors.size - 10} more errors")
//                }
//            }
//            println("=".repeat(50))
//        }
//
//        private fun Double.format(decimals: Int): String {
//            return "%.${decimals}f".format(this)
//        }
//    }
}