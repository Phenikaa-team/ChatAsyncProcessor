package com.chat.async.app.monitoring

import com.chat.async.app.helper.enums.EventType
import com.chat.async.app.helper.enums.LogLevel
import javafx.application.Platform
import javafx.stage.Stage

class MonitoringIntegration {
    companion object {
        private var dashboardStage: Stage? = null
        private var dashboardInstance: MonitoringDashboard? = null
        private val monitoring = MonitoringService.getInstance()

        fun startMonitoringDashboard() {
            if (dashboardStage == null) {
                try {
                    Platform.runLater {
                        try {
                            dashboardInstance = MonitoringDashboard()
                            dashboardStage = Stage()
                            dashboardInstance!!.start(dashboardStage!!)
                            println("✅ Monitoring Dashboard launched")
                        } catch (e: Exception) {
                            println("❌ Failed to start monitoring dashboard: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    println("❌ Failed to start monitoring dashboard: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                println("⚠️ Monitoring Dashboard is already running")
                // Bring an existing window to front
                Platform.runLater {
                    dashboardStage?.toFront()
                    dashboardStage?.requestFocus()
                }
            }
        }

        fun closeDashboard() {
            Platform.runLater {
                dashboardStage?.close()
                dashboardStage = null
                dashboardInstance = null
            }
        }

        fun printSystemStats() {
            monitoring.printSystemStats()
            monitoring.printActiveUsers()
            monitoring.printActiveGroups()
        }

        fun getMonitoringService(): MonitoringService {
            return monitoring
        }

        fun logEvent(
            level: LogLevel,
            eventType: EventType,
            message: String, 
            details: Map<String, Any> = emptyMap()
        ) {
            monitoring.log(level, eventType, message, details)
        }

        fun registerUser(
            userId: String,
            username: String
        ) {
            monitoring.registerUser(userId, username)
        }

        fun updateUserActivity(
            userId: String
        ) {
            monitoring.updateUserActivity(userId)
        }

        fun removeUser(
            userId: String
        ) {
            monitoring.removeUser(userId)
        }

        fun registerGroup(
            groupId: String, 
            groupName: String
        ) {
            monitoring.registerGroup(groupId, groupName)
        }

        fun updateGroupMemberCount(
            groupId: String,
            memberCount: Int
        ) {
            monitoring.updateGroupMemberCount(groupId, memberCount)
        }

        fun removeGroup(
            groupId: String
        ) {
            monitoring.removeGroup(groupId)
        }

        fun trackMessageSent(
            senderId: String,
            targetId: String, 
            messageType: String,
            size: Int = 0
        ) {
            monitoring.trackMessageSent(senderId, targetId, messageType, size)
        }

        fun trackMessageReceived(
            receiverId: String,
            senderId: String, 
            messageType: String,
            size: Int = 0
        ) {
            monitoring.trackMessageReceived(receiverId, senderId, messageType, size)
        }

        fun trackFileSent(
            senderId: String,
            targetId: String,
            messageType: String,
            size: Int = 0
        ) {
            monitoring.trackFileSent(senderId, targetId, messageType, size)
        }

        fun trackFileReceived(
            receiverId: String, 
            senderId: String, 
            messageType: String,
            size: Int = 0
        ) {
            monitoring.trackFileReceived(receiverId, senderId, messageType, size)
        }

        fun trackImageSent(
            senderId: String,
            targetId: String, 
            messageType: String,
            size: Int = 0
        ) {
            monitoring.trackImageSent(senderId, targetId, messageType, size)
        }

        fun trackImageReceived(
            receiverId: String,
            senderId: String,
            messageType: String, 
            size: Int = 0
        ) {
            monitoring.trackImageReceived(receiverId, senderId, messageType, size)
        }

        fun trackMessageProcessingTime(
            processingTime: Long
        ) {
            monitoring.trackMessageProcessingTime(processingTime)
        }

        fun logError(
            message: String,
            exception: Throwable? = null,
            context: Map<String, Any> = emptyMap()
        ) {
            monitoring.logError(message, exception, context)
        }

        fun getSystemStats() = monitoring.getSystemStats()

        fun getHealthStatus() = monitoring.getHealthStatus()

        fun initializeMonitoring() {
            logEvent(
                LogLevel.INFO,
                EventType.SYSTEM,
                "Chat system monitoring initialized",
                mapOf("timestamp" to System.currentTimeMillis())
            )

            println("✅ Monitoring system initialized")
            println("📊 Use MonitoringIntegration.startMonitoringDashboard() to launch GUI")
            println("📋 Use MonitoringIntegration.printSystemStats() for console output")
        }

        fun shutdown() {
            logEvent(
                LogLevel.INFO,
                EventType.SYSTEM,
                "Chat system shutting down",
                mapOf("timestamp" to System.currentTimeMillis())
            )

            closeDashboard()
        }
    }
}

fun String.logAsSystem(
    level: LogLevel = LogLevel.INFO
) {
    MonitoringIntegration.logEvent(level, EventType.SYSTEM, this)
}

fun String.logAsError(
    exception: Throwable? = null,
    context: Map<String, Any> = emptyMap()
) {
    MonitoringIntegration.logError(this, exception, context)
}

fun Throwable.logError(
    message: String = this.message ?: "Unknown error",
    context: Map<String, Any> = emptyMap()
) {
    MonitoringIntegration.logError(message, this, context)
}