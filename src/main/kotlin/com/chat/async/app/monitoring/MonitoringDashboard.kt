package com.chat.async.app.monitoring

import com.chat.async.app.helper.asStream
import com.chat.async.app.helper.formatBytes
import com.chat.async.app.helper.formatLastActivity
import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.chart.LineChart
import javafx.scene.chart.NumberAxis
import javafx.scene.chart.XYChart
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.layout.*
import javafx.stage.Stage
import javafx.stage.StageStyle
import java.util.*
import kotlin.concurrent.timer

class MonitoringDashboard : Application() {
    private val monitoring = MonitoringService.getInstance()
    private var updateTimer: Timer? = null

    // UI Components
    private lateinit var uptimeLabel: Label
    private lateinit var activeUsersLabel: Label
    private lateinit var activeGroupsLabel: Label
    private lateinit var messagesSentLabel: Label
    private lateinit var messagesReceivedLabel: Label
    private lateinit var filesSentLabel: Label
    private lateinit var filesReceivedLabel: Label
    private lateinit var imagesSentLabel: Label
    private lateinit var imagesReceivedLabel: Label
    private lateinit var errorsLabel: Label
    private lateinit var healthStatusLabel: Label

    // Charts
    private lateinit var messageChart: LineChart<Number, Number>
    private val messageChartData = XYChart.Series<Number, Number>()
    private var chartTimeCounter = 0
    private var lastMessageCount = 0L

    // Lists
    private lateinit var usersList: ListView<String>
    private lateinit var groupsList: ListView<String>
    private lateinit var logArea: TextArea

    override fun start(
        primaryStage: Stage
    ) {
        setupUI(primaryStage)
        startAutoUpdate()
        primaryStage.show()

        // Handle window close request
        primaryStage.setOnCloseRequest {
            stopAutoUpdate()
        }
    }

    private fun setupUI(
        stage: Stage
    ) {
        stage.title = "Chat System Monitoring Dashboard"
        stage.initStyle(StageStyle.DECORATED)

        val mainPane = BorderPane().apply {
            padding = Insets(10.0)
            top = createHeaderPane()
            center = createCenterPane()
            bottom = createControlPane()
        }

        stage.scene = Scene(mainPane, 1200.0, 800.0)
        stage.isResizable = true

        stage.icons.addAll(
            Image(this.asStream("debug_16.png")),
            Image(this.asStream("debug_32.png")),
            Image(this.asStream("debug_64.png"))
        )
    }

    private fun createHeaderPane(): HBox {
        return HBox(20.0).apply {
            padding = Insets(10.0)
            alignment = Pos.CENTER
            style = "-fx-background-color: #2c3e50; -fx-background-radius: 5;"

            val uptimeCard = createStatCard("System Uptime", "⏱️")
            uptimeLabel = findLabelInStatCard(uptimeCard)

            val usersCard = createStatCard("Active Users", "👥")
            activeUsersLabel = findLabelInStatCard(usersCard)

            val groupsCard = createStatCard("Active Groups", "🏠")
            activeGroupsLabel = findLabelInStatCard(groupsCard)

            val healthCard = createStatCard("Health Status", "❤️")
            healthStatusLabel = findLabelInStatCard(healthCard)

            children.addAll(uptimeCard, usersCard, groupsCard, healthCard)
        }
    }

    private fun findLabelInStatCard(
        statCard: VBox
    ): Label {
        for (child in statCard.children) {
            if (child is VBox) {
                for (subChild in child.children) {
                    if (subChild is Label && subChild.style.contains("-fx-text-fill: #3498db")) {
                        return subChild
                    }
                }
            }
        }
        return Label("N/A").apply {
            style = "-fx-font-size: 14; -fx-text-fill: #3498db; -fx-font-weight: bold;"
        }
    }

    private fun createStatCard(
        title: String,
        icon: String
    ): VBox {
        val valueLabel = Label("Loading...").apply {
            style = "-fx-font-size: 14; -fx-text-fill: #3498db; -fx-font-weight: bold;"
        }

        return VBox(5.0).apply {
            alignment = Pos.CENTER
            style = "-fx-background-color: #34495e; -fx-background-radius: 5; -fx-padding: 10;"
            minWidth = 180.0

            children.addAll(
                HBox(5.0).apply {
                    alignment = Pos.CENTER
                    children.addAll(
                        Label(icon).apply { style = "-fx-font-size: 16; -fx-text-fill: white;" },
                        Label(title).apply { style = "-fx-font-size: 12; -fx-text-fill: white; -fx-font-weight: bold;" }
                    )
                },
                VBox().apply {
                    alignment = Pos.CENTER
                    children.add(valueLabel)
                }
            )
        }
    }

    private fun createCenterPane(): TabPane {
        return TabPane().apply {
            tabs.addAll(
                createOverviewTab(),
                createUsersTab(),
                createGroupsTab(),
                createChartsTab(),
                createLogsTab()
            )
        }
    }

    private fun createOverviewTab(): Tab {
        val tab = Tab("📊 Overview")
        tab.isClosable = false

        val gridPane = GridPane().apply {
            hgap = 15.0
            vgap = 15.0
            padding = Insets(20.0)
        }

        // Messages statistics
        val messagesPane = createStatsGroup("Message Statistics", listOf(
            "Messages Sent" to "📤",
            "Messages Received" to "📥",
            "Files Sent" to "📁",
            "Files Received" to "📂",
            "Images Sent" to "🖼️",
            "Images Received" to "🖼️",
            "Total Errors" to "❌"
        ))

        // System info
        val systemInfoArea = TextArea().apply {
            isEditable = false
            prefRowCount = 15
            style = "-fx-font-family: 'Courier New', monospace; -fx-font-size: 12;"
        }

        val refreshButton = Button("🔄 Refresh System Info").apply {
            setOnAction { updateSystemInfo(systemInfoArea) }
        }

        gridPane.add(messagesPane, 0, 0)
        gridPane.add(VBox(10.0, refreshButton, systemInfoArea), 1, 0)

        gridPane.columnConstraints.addAll(
            ColumnConstraints().apply { percentWidth = 40.0 },
            ColumnConstraints().apply { percentWidth = 60.0 }
        )

        tab.content = ScrollPane(gridPane).apply { isFitToWidth = true }
        return tab
    }

    private fun createUsersTab(): Tab {
        val tab = Tab("👥 Active Users")
        tab.isClosable = false

        usersList = ListView<String>().apply {
            placeholder = Label("No active users")
            style = "-fx-font-family: 'Courier New', monospace;"
        }

        val refreshButton = Button("🔄 Refresh Users").apply {
            setOnAction { updateUsersList() }
        }

        tab.content = VBox(10.0, refreshButton, usersList).apply {
            padding = Insets(10.0)
            VBox.setVgrow(usersList, Priority.ALWAYS)
        }

        return tab
    }

    private fun createGroupsTab(): Tab {
        val tab = Tab("🏠 Active Groups")
        tab.isClosable = false

        groupsList = ListView<String>().apply {
            placeholder = Label("No active groups")
            style = "-fx-font-family: 'Courier New', monospace;"
        }

        val refreshButton = Button("🔄 Refresh Groups").apply {
            setOnAction { updateGroupsList() }
        }

        tab.content = VBox(10.0, refreshButton, groupsList).apply {
            padding = Insets(10.0)
            VBox.setVgrow(groupsList, Priority.ALWAYS)
        }

        return tab
    }

    private fun createChartsTab(): Tab {
        val tab = Tab("📈 Charts")
        tab.isClosable = false

        val xAxis = NumberAxis().apply {
            label = "Time (seconds)"
            isAutoRanging = false
            lowerBound = 0.0
            upperBound = 60.0
            tickUnit = 10.0
        }

        val yAxis = NumberAxis().apply {
            label = "Messages per Second"
            isAutoRanging = true
        }

        messageChart = LineChart(xAxis, yAxis).apply {
            title = "Message Traffic Over Time"
            data.add(messageChartData.apply { name = "Messages/sec" })
            createSymbols = false
            animated = false
        }

        val clearChartButton = Button("🗑️ Clear Chart").apply {
            setOnAction {
                messageChartData.data.clear()
                chartTimeCounter = 0
                lastMessageCount = 0L
            }
        }

        tab.content = VBox(10.0, clearChartButton, messageChart).apply {
            padding = Insets(10.0)
            VBox.setVgrow(messageChart, Priority.ALWAYS)
        }

        return tab
    }

    private fun createLogsTab(): Tab {
        val tab = Tab("📝 Logs")
        tab.isClosable = false

        logArea = TextArea().apply {
            isEditable = false
            style = "-fx-font-family: 'Courier New', monospace; -fx-font-size: 11;"
            prefRowCount = 25
        }

        val clearLogsButton = Button("🗑️ Clear Logs").apply {
            setOnAction { logArea.text = "" }
        }

        val saveLogsButton = Button("💾 Save Logs").apply {
            setOnAction {
                val alert = Alert(Alert.AlertType.INFORMATION)
                alert.title = "Save Logs"
                alert.headerText = "Logs Information"
                alert.contentText = "Logs are automatically saved to chat_monitoring.log\nCurrent log entries: ${logArea.text.split('\n').size}"
                alert.showAndWait()
            }
        }

        val autoScrollCheckBox = CheckBox("Auto Scroll").apply {
            isSelected = true
        }

        tab.content = VBox(10.0).apply {
            padding = Insets(10.0)
            children.addAll(
                HBox(10.0, clearLogsButton, saveLogsButton, autoScrollCheckBox),
                logArea
            )
            VBox.setVgrow(logArea, Priority.ALWAYS)
        }

        return tab
    }

    private fun createStatsGroup(
        title: String,
        stats: List<Pair<String, String>>
    ): TitledPane {
        val vbox = VBox(10.0).apply {
            padding = Insets(10.0)
        }

        stats.forEach { (statName, icon) ->
            val label = Label("0").apply {
                style = "-fx-font-weight: bold; -fx-font-size: 14;"
            }

            when (statName) {
                "Messages Sent" -> messagesSentLabel = label
                "Messages Received" -> messagesReceivedLabel = label
                "Files Sent" -> filesSentLabel = label
                "Files Received" -> filesReceivedLabel = label
                "Images Sent" -> imagesSentLabel = label
                "Images Received" -> imagesReceivedLabel = label
                "Total Errors" -> errorsLabel = label
            }

            vbox.children.add(
                HBox(10.0).apply {
                    alignment = Pos.CENTER_LEFT
                    children.addAll(
                        Label(icon).apply { style = "-fx-font-size: 16;" },
                        Label(statName).apply { style = "-fx-font-weight: bold;" },
                        Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
                        label
                    )
                }
            )
        }

        return TitledPane(title, vbox).apply {
            isCollapsible = false
        }
    }

    private fun createControlPane(): HBox {
        return HBox(10.0).apply {
            padding = Insets(10.0)
            alignment = Pos.CENTER

            children.addAll(
                Button("🔄 Refresh All").apply {
                    setOnAction { updateAll() }
                },
                Button("📊 Print Stats to Console").apply {
                    setOnAction { monitoring.printSystemStats() }
                },
                Button("👥 Print Active Users").apply {
                    setOnAction { monitoring.printActiveUsers() }
                },
                Button("🏠 Print Active Groups").apply {
                    setOnAction { monitoring.printActiveGroups() }
                },
                Button("❌ Close Dashboard").apply {
                    setOnAction {
                        stopAutoUpdate()
                        (scene.window as Stage).close()
                    }
                }
            )
        }
    }

    private fun startAutoUpdate() {
        updateTimer = timer(period = 3000) { // Update every 3 seconds
            Platform.runLater {
                updateAll()
                updateChart()
                updateLogs()
            }
        }
    }

    private fun stopAutoUpdate() {
        updateTimer?.cancel()
        updateTimer = null
    }

    private fun updateAll() {
        try {
            val stats = monitoring.getSystemStats()
            val health = monitoring.getHealthStatus()

            // Update header stats
            uptimeLabel.text = stats.getString("uptimeFormatted")
            activeUsersLabel.text = stats.getInteger("activeUsers").toString()
            activeGroupsLabel.text = stats.getInteger("activeGroups").toString()

            val healthStatus = health.getString("status")
            healthStatusLabel.text = healthStatus
            healthStatusLabel.style = "-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: ${
                if (healthStatus == "HEALTHY") "#27ae60" else "#e74c3c"
            };"

            // Update statistics
            val statistics = stats.getJsonObject("statistics")
            messagesSentLabel.text = statistics.getLong("messagesSent").toString()
            messagesReceivedLabel.text = statistics.getLong("messagesReceived").toString()
            filesSentLabel.text = statistics.getLong("filesSent").toString()
            filesReceivedLabel.text = statistics.getLong("filesReceived").toString()
            imagesSentLabel.text = statistics.getLong("imagesSent").toString()
            imagesReceivedLabel.text = statistics.getLong("imagesReceived").toString()
            errorsLabel.text = statistics.getLong("totalErrors").toString()

            // Update lists
            updateUsersList()
            updateGroupsList()
        } catch (e: Exception) {
            println("❌ Error updating dashboard: ${e.message}")
        }
    }

    private fun updateUsersList() {
        try {
            val stats = monitoring.getSystemStats()
            val users = stats.getJsonArray("activeUsersList")

            usersList.items.clear()
            users?.forEach { userObj ->
                if (userObj is Map<*, *>) {
                    val userInfo = "${userObj["username"]} (${(userObj["userId"] as String).take(8)}...)\n" +
                            "  Session: ${userObj["sessionDuration"]}\n" +
                            "  Last Active: ${formatLastActivity(userObj["lastActivity"] as Long)}"
                    usersList.items.add(userInfo)
                }
            }
        } catch (e: Exception) {
            println("❌ Error updating users list: ${e.message}")
        }
    }

    private fun updateGroupsList() {
        try {
            val stats = monitoring.getSystemStats()
            val groups = stats.getJsonArray("activeGroupsList")

            groupsList.items.clear()
            groups?.forEach { groupObj ->
                if (groupObj is Map<*, *>) {
                    val groupInfo = "${groupObj["groupName"]} (${(groupObj["groupId"] as String).take(8)}...)\n" +
                            "  Members: ${groupObj["memberCount"]}\n" +
                            "  Created: ${groupObj["age"]} ago"
                    groupsList.items.add(groupInfo)
                }
            }
        } catch (e: Exception) {
            println("❌ Error updating groups list: ${e.message}")
        }
    }

    private fun updateSystemInfo(
        textArea: TextArea
    ) {
        try {
            val stats = monitoring.getSystemStats()
            val health = monitoring.getHealthStatus()

            val info = buildString {
                appendLine("=== CHAT SYSTEM MONITORING DASHBOARD ===")
                appendLine("System Uptime: ${stats.getString("uptimeFormatted")}")
                appendLine("Health Status: ${health.getString("status")}")
                appendLine("Error Rate: ${String.format("%.2f%%", health.getDouble("errorRate") * 100)}")
                appendLine()

                appendLine("=== ACTIVE CONNECTIONS ===")
                appendLine("Active Users: ${stats.getInteger("activeUsers")}")
                appendLine("Active Groups: ${stats.getInteger("activeGroups")}")
                appendLine("Total Users Registered: ${stats.getLong("totalUsersRegistered")}")
                appendLine("Total Groups Created: ${stats.getLong("totalGroupsCreated")}")
                appendLine()

                appendLine("=== MESSAGE STATISTICS ===")
                val statistics = stats.getJsonObject("statistics")
                appendLine("Messages Sent: ${statistics.getLong("messagesSent")}")
                appendLine("Messages Received: ${statistics.getLong("messagesReceived")}")
                appendLine("Files Sent: ${statistics.getLong("filesSent")}")
                appendLine("Files Received: ${statistics.getLong("filesReceived")}")
                appendLine("Images Sent: ${statistics.getLong("imagesSent")}")
                appendLine("Images Received: ${statistics.getLong("imagesReceived")}")
                appendLine("Group Joins: ${statistics.getLong("groupJoins")}")
                appendLine("Group Leaves: ${statistics.getLong("groupLeaves")}")
                appendLine("Total Errors: ${statistics.getLong("totalErrors")}")
                appendLine()

                appendLine("=== PERFORMANCE METRICS ===")
                val performance = stats.getJsonObject("performance")
                appendLine("Avg Message Processing Time: ${String.format("%.2f", performance.getDouble("avgMessageProcessingTime"))}ms")
                appendLine("Max Message Processing Time: ${performance.getLong("maxMessageProcessingTime")}ms")
                appendLine("Total Processed Messages: ${performance.getInteger("totalProcessedMessages")}")
                appendLine()

                appendLine("=== MEMORY USAGE ===")
                val memoryUsage = health.getJsonObject("details").getJsonObject("memoryUsage")
                appendLine("Used Memory: ${formatBytes(memoryUsage.getLong("used"))}")
                appendLine("Free Memory: ${formatBytes(memoryUsage.getLong("free"))}")
                appendLine("Total Memory: ${formatBytes(memoryUsage.getLong("total"))}")
                appendLine("Max Memory: ${formatBytes(memoryUsage.getLong("max"))}")
                appendLine("Memory Usage: ${String.format("%.2f%%", memoryUsage.getDouble("usedPercentage"))}")
            }

            textArea.text = info
        } catch (e: Exception) {
            textArea.text = "Error loading system information: ${e.message}"
        }
    }

    private fun updateChart() {
        try {
            val stats = monitoring.getSystemStats()
            val statistics = stats.getJsonObject("statistics")
            val currentMessageCount = statistics.getLong("messagesSent") + statistics.getLong("messagesReceived")

            val messagesPerSecond = if (lastMessageCount > 0) {
                ((currentMessageCount - lastMessageCount) / 3.0) // 3 second intervals
            } else {
                0.0
            }

            lastMessageCount = currentMessageCount

            // Add data point to chart
            messageChartData.data.add(XYChart.Data(chartTimeCounter, messagesPerSecond))

            // Keep only last 60 data points (3 minutes of data)
            if (messageChartData.data.size > 60) {
                messageChartData.data.removeAt(0)
            }

            // Update X axis range
            if (chartTimeCounter > 60) {
                val xAxis = messageChart.xAxis as NumberAxis
                xAxis.lowerBound = (chartTimeCounter - 60).toDouble()
                xAxis.upperBound = chartTimeCounter.toDouble()
            }

            chartTimeCounter += 3
        } catch (e: Exception) {
            println("❌ Error updating chart: ${e.message}")
        }
    }

    private fun updateLogs() {
        try {
            val currentTime = java.text.SimpleDateFormat("HH:mm:ss").format(Date())
            val stats = monitoring.getSystemStats()
            val recentLogEntry = "[$currentTime] Active Users: ${stats.getInteger("activeUsers")}, " +
                    "Active Groups: ${stats.getInteger("activeGroups")}, " +
                    "Messages: ${stats.getJsonObject("statistics").getLong("messagesSent")}"

            if (logArea.text.split('\n').size > 100) {
                // Keep only last 80 lines to prevent memory issues
                val lines = logArea.text.split('\n').takeLast(80)
                logArea.text = lines.joinToString("\n")
            }

            logArea.appendText("\n$recentLogEntry")
            logArea.positionCaret(logArea.text.length)
        } catch (e: Exception) {
            println("❌ Error updating logs: ${e.message}")
        }
    }
}