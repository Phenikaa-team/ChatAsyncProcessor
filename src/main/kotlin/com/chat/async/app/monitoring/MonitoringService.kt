package com.chat.async.app.monitoring

import io.vertx.core.json.JsonObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class MonitoringService private constructor() {
    companion object {
        @Volatile
        private var INSTANCE: MonitoringService? = null

        fun getInstance(): MonitoringService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MonitoringService().also { INSTANCE = it }
            }
        }
    }

    // Statistics counters
    private val messagesSent = AtomicLong(0)
    private val messagesReceived = AtomicLong(0)
    private val filesSent = AtomicLong(0)
    private val filesReceived = AtomicLong(0)
    private val imagesSent = AtomicLong(0)
    private val imagesReceived = AtomicLong(0)
    private val usersRegistered = AtomicLong(0)
    private val groupsCreated = AtomicLong(0)
    private val groupJoins = AtomicLong(0)
    private val groupLeaves = AtomicLong(0)
    private val errors = AtomicLong(0)

    // Active connections tracking
    private val activeUsers = ConcurrentHashMap<String, UserSession>()
    private val activeGroups = ConcurrentHashMap<String, GroupInfo>()

    // Performance metrics
    private val messageProcessingTimes = mutableListOf<Long>()
    private val maxMessageProcessingTimes = 1000 // Keep last 1000 processing times

    // System start time
    private val startTime = System.currentTimeMillis()

    // Log file
    private val logFile = File("chat_monitoring.log")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    data class UserSession(
        val userId: String,
        val username: String,
        val loginTime: Long,
        var lastActivity: Long = System.currentTimeMillis()
    )

    data class GroupInfo(
        val groupId: String,
        val groupName: String,
        val createdTime: Long,
        var memberCount: Int = 0
    )

    enum class LogLevel { INFO, WARN, ERROR, DEBUG }
    enum class EventType {
        USER_REGISTER, USER_ACTIVITY,
        MESSAGE_SENT, MESSAGE_RECEIVED,
        FILE_SENT, FILE_RECEIVED,
        IMAGE_SENT, IMAGE_RECEIVED,
        GROUP_CREATED, GROUP_JOINED, GROUP_LEFT,
        ERROR, SYSTEM
    }

    // Core logging function
    fun log(level: LogLevel, eventType: EventType, message: String, details: Map<String, Any> = emptyMap()) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] [${level.name}] [${eventType.name}] $message"

        // Console output with colors
        val coloredLog = when (level) {
            LogLevel.INFO -> "\u001B[32m$logEntry\u001B[0m"    // Green
            LogLevel.WARN -> "\u001B[33m$logEntry\u001B[0m"    // Yellow
            LogLevel.ERROR -> "\u001B[31m$logEntry\u001B[0m"   // Red
            LogLevel.DEBUG -> "\u001B[36m$logEntry\u001B[0m"   // Cyan
        }

        println(coloredLog)

        // File logging
        try {
            val detailsStr = if (details.isNotEmpty()) " | Details: ${JsonObject(details).encode()}" else ""
            logFile.appendText("$logEntry$detailsStr\n")
        } catch (e: Exception) {
            println("❌ Failed to write to log file: ${e.message}")
        }

        // Update statistics
        updateStatistics(eventType)
    }

    private fun updateStatistics(eventType: EventType) {
        when (eventType) {
            EventType.MESSAGE_SENT -> messagesSent.incrementAndGet()
            EventType.MESSAGE_RECEIVED -> messagesReceived.incrementAndGet()
            EventType.FILE_SENT -> filesSent.incrementAndGet()
            EventType.FILE_RECEIVED -> filesReceived.incrementAndGet()
            EventType.IMAGE_SENT -> imagesSent.incrementAndGet()
            EventType.IMAGE_RECEIVED -> imagesReceived.incrementAndGet()
            EventType.USER_REGISTER -> usersRegistered.incrementAndGet()
            EventType.GROUP_CREATED -> groupsCreated.incrementAndGet()
            EventType.GROUP_JOINED -> groupJoins.incrementAndGet()
            EventType.GROUP_LEFT -> groupLeaves.incrementAndGet()
            EventType.ERROR -> errors.incrementAndGet()
            else -> {} // No specific counter for other events
        }
    }

    // User session management
    fun registerUser(userId: String, username: String) {
        activeUsers[userId] = UserSession(userId, username, System.currentTimeMillis())
        log(LogLevel.INFO, EventType.USER_REGISTER, "User registered: $username",
            mapOf("userId" to userId, "username" to username))
    }

    fun updateUserActivity(userId: String) {
        activeUsers[userId]?.let { session ->
            session.lastActivity = System.currentTimeMillis()
        }
    }

    fun removeUser(userId: String) {
        activeUsers.remove(userId)?.let { session ->
            log(LogLevel.INFO, EventType.SYSTEM, "User disconnected: ${session.username}",
                mapOf("userId" to userId, "sessionDuration" to (System.currentTimeMillis() - session.loginTime)))
        }
    }

    // Group management
    fun registerGroup(groupId: String, groupName: String) {
        activeGroups[groupId] = GroupInfo(groupId, groupName, System.currentTimeMillis())
        log(LogLevel.INFO, EventType.GROUP_CREATED, "Group created: $groupName",
            mapOf("groupId" to groupId, "groupName" to groupName))
    }

    fun updateGroupMemberCount(groupId: String, memberCount: Int) {
        activeGroups[groupId]?.let { group ->
            group.memberCount = memberCount
        }
    }

    fun removeGroup(groupId: String) {
        activeGroups.remove(groupId)?.let { group ->
            log(LogLevel.INFO, EventType.SYSTEM, "Group deleted: ${group.groupName}",
                mapOf("groupId" to groupId, "groupName" to group.groupName))
        }
    }

    // Message tracking
    fun trackMessageSent(senderId: String, targetId: String, messageType: String, size: Int = 0) {
        updateUserActivity(senderId)
        log(LogLevel.INFO, EventType.MESSAGE_SENT, "Message sent: $messageType",
            mapOf("senderId" to senderId, "targetId" to targetId, "type" to messageType, "size" to size))
    }

    fun trackMessageReceived(receiverId: String, senderId: String, messageType: String, size: Int = 0) {
        updateUserActivity(receiverId)
        log(LogLevel.INFO, EventType.MESSAGE_RECEIVED, "Message received: $messageType",
            mapOf("receiverId" to receiverId, "senderId" to senderId, "type" to messageType, "size" to size))
    }

    fun trackFileSent(senderId: String, targetId: String, messageType: String, size: Int = 0) {
        updateUserActivity(senderId)
        log(LogLevel.INFO, EventType.FILE_SENT, "File sent: $messageType",
            mapOf("senderId" to senderId, "targetId" to targetId, "type" to messageType, "size" to size))
    }

    fun trackFileReceived(receiverId: String, senderId: String, messageType: String, size: Int = 0) {
        updateUserActivity(receiverId)
        log(LogLevel.INFO, EventType.FILE_RECEIVED, "File received: $messageType",
            mapOf("receiverId" to receiverId, "senderId" to senderId, "type" to messageType, "size" to size))
    }

    fun trackImageSent(senderId: String, targetId: String, messageType: String, size: Int = 0) {
        updateUserActivity(senderId)
        log(LogLevel.INFO, EventType.IMAGE_SENT, "Image sent: $messageType",
            mapOf("senderId" to senderId, "targetId" to targetId, "type" to messageType, "size" to size))
    }

    fun trackImageReceived(receiverId: String, senderId: String, messageType: String, size: Int = 0) {
        updateUserActivity(receiverId)
        log(LogLevel.INFO, EventType.IMAGE_RECEIVED, "Image received: $messageType",
            mapOf("receiverId" to receiverId, "senderId" to senderId, "type" to messageType, "size" to size))
    }

    // Performance tracking
    fun trackMessageProcessingTime(processingTime: Long) {
        synchronized(messageProcessingTimes) {
            messageProcessingTimes.add(processingTime)
            if (messageProcessingTimes.size > maxMessageProcessingTimes) {
                messageProcessingTimes.removeAt(0)
            }
        }
    }

    // Error tracking
    fun logError(message: String, exception: Throwable? = null, context: Map<String, Any> = emptyMap()) {
        val details = context.toMutableMap()
        exception?.let {
            details["exception"] = it.javaClass.simpleName
            details["errorMessage"] = it.message ?: "Unknown error"
            details["stackTrace"] = it.stackTrace.take(5).joinToString("\n") { "  at $it" }
        }
        log(LogLevel.ERROR, EventType.ERROR, message, details)
    }

    // Statistics generation
    fun getSystemStats(): JsonObject {
        val uptime = System.currentTimeMillis() - startTime
        val avgProcessingTime = synchronized(messageProcessingTimes) {
            if (messageProcessingTimes.isEmpty()) 0.0
            else messageProcessingTimes.average()
        }

        val maxProcessingTime = synchronized(messageProcessingTimes) {
            messageProcessingTimes.maxOrNull() ?: 0L
        }

        return JsonObject().apply {
            put("systemUptime", uptime)
            put("uptimeFormatted", formatDuration(uptime))
            put("activeUsers", activeUsers.size)
            put("activeGroups", activeGroups.size)
            put("totalUsersRegistered", usersRegistered.get())
            put("totalGroupsCreated", groupsCreated.get())
            put("statistics", JsonObject().apply {
                put("messagesSent", messagesSent.get())
                put("messagesReceived", messagesReceived.get())
                put("filesSent", filesSent.get())
                put("filesReceived", filesReceived.get())
                put("imagesSent", imagesSent.get())
                put("imagesReceived", imagesReceived.get())
                put("groupJoins", groupJoins.get())
                put("groupLeaves", groupLeaves.get())
                put("totalErrors", errors.get())
            })
            put("performance", JsonObject().apply {
                put("avgMessageProcessingTime", avgProcessingTime)
                put("maxMessageProcessingTime", maxProcessingTime)
                put("totalProcessedMessages", messageProcessingTimes.size)
            })
            put("activeUsersList", activeUsers.values.map { session ->
                JsonObject().apply {
                    put("userId", session.userId)
                    put("username", session.username)
                    put("loginTime", session.loginTime)
                    put("lastActivity", session.lastActivity)
                    put("sessionDuration", formatDuration(System.currentTimeMillis() - session.loginTime))
                }
            })
            put("activeGroupsList", activeGroups.values.map { group ->
                JsonObject().apply {
                    put("groupId", group.groupId)
                    put("groupName", group.groupName)
                    put("createdTime", group.createdTime)
                    put("memberCount", group.memberCount)
                    put("age", formatDuration(System.currentTimeMillis() - group.createdTime))
                }
            })
        }
    }

    // Health check
    fun getHealthStatus(): JsonObject {
        val stats = getSystemStats()
        val errorRate = if (messagesSent.get() > 0) {
            errors.get().toDouble() / messagesSent.get().toDouble()
        } else 0.0

        val isHealthy = errorRate < 0.1 && activeUsers.size >= 0 // Basic health criteria

        return JsonObject().apply {
            put("status", if (isHealthy) "HEALTHY" else "UNHEALTHY")
            put("timestamp", System.currentTimeMillis())
            put("uptime", stats.getLong("systemUptime"))
            put("errorRate", errorRate)
            put("activeConnections", activeUsers.size)
            put("details", JsonObject().apply {
                put("totalMessages", messagesSent.get() + messagesReceived.get())
                put("totalErrors", errors.get())
                put("memoryUsage", getMemoryUsage())
            })
        }
    }

    private fun getMemoryUsage(): JsonObject {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val maxMemory = runtime.maxMemory()

        return JsonObject().apply {
            put("used", usedMemory)
            put("free", freeMemory)
            put("total", totalMemory)
            put("max", maxMemory)
            put("usedPercentage", (usedMemory.toDouble() / maxMemory.toDouble()) * 100)
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ${hours % 24}h ${minutes % 60}m"
            hours > 0 -> "${hours}h ${minutes % 60}m ${seconds % 60}s"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }

    // CLI output methods
    fun printSystemStats() {
        val stats = getSystemStats()
        println("\n" + "=".repeat(60))
        println("📊 CHAT SYSTEM MONITORING DASHBOARD")
        println("=".repeat(60))
        println("⏱️  System Uptime: ${stats.getString("uptimeFormatted")}")
        println("👥 Active Users: ${stats.getInteger("activeUsers")}")
        println("🏠 Active Groups: ${stats.getInteger("activeGroups")}")

        val statistics = stats.getJsonObject("statistics")
        println("\n📈 MESSAGE STATISTICS:")
        println("   📤 Messages Sent: ${statistics.getLong("messagesSent")}")
        println("   📥 Messages Received: ${statistics.getLong("messagesReceived")}")
        println("   📁 Files Sent: ${statistics.getLong("filesSent")}")
        println("   📁 Files Received: ${statistics.getLong("filesReceived")}")
        println("   🖼️  Images Sent: ${statistics.getLong("imagesSent")}")
        println("   🖼️  Images Received: ${statistics.getLong("imagesReceived")}")
        println("   ❌ Total Errors: ${statistics.getLong("totalErrors")}")

        val performance = stats.getJsonObject("performance")
        println("\n⚡ PERFORMANCE METRICS:")
        println("   📊 Avg Processing Time: ${String.format("%.2f", performance.getDouble("avgMessageProcessingTime"))}ms")
        println("   📊 Max Processing Time: ${performance.getLong("maxMessageProcessingTime")}ms")

        val health = getHealthStatus()
        val healthEmoji = if (health.getString("status") == "HEALTHY") "✅" else "⚠️"
        println("\n$healthEmoji HEALTH STATUS: ${health.getString("status")}")
        println("=".repeat(60))
    }

    fun printActiveUsers() {
        println("\n👥 ACTIVE USERS:")
        if (activeUsers.isEmpty()) {
            println("   No active users")
        } else {
            activeUsers.values.forEach { session ->
                val sessionDuration = formatDuration(System.currentTimeMillis() - session.loginTime)
                val lastActivity = formatDuration(System.currentTimeMillis() - session.lastActivity)
                println("   • ${session.username} (${session.userId.take(8)}...) - Session: $sessionDuration, Last active: ${lastActivity} ago")
            }
        }
    }

    fun printActiveGroups() {
        println("\n🏠 ACTIVE GROUPS:")
        if (activeGroups.isEmpty()) {
            println("   No active groups")
        } else {
            activeGroups.values.forEach { group ->
                val age = formatDuration(System.currentTimeMillis() - group.createdTime)
                println("   • ${group.groupName} (${group.groupId.take(8)}...) - ${group.memberCount} members, Created: $age ago")
            }
        }
    }
}