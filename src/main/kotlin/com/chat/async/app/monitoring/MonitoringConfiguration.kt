package com.chat.async.app.monitoring

/**
 * Configuration class for the monitoring system
 * Allows customization of monitoring behavior without code changes
 */
object MonitoringConfiguration {

    // Dashboard settings
    var enableDashboard: Boolean = true
    var dashboardAutoStart: Boolean = true
    var dashboardStartupDelay: Long = 3000 // milliseconds

    // Console output settings
    var enableConsoleStats: Boolean = true
    var consoleStatsInterval: Long = 10000 // milliseconds
    var useColoredConsoleOutput: Boolean = true

    // Logging settings
    var enableFileLogging: Boolean = true
    var logFileName: String = "chat_monitoring.log"
    var maxLogFileSize: Long = 10 * 1024 * 1024 // 10MB
    var enableLogRotation: Boolean = true

    // Performance monitoring
    var trackMessageProcessingTime: Boolean = true
    var maxProcessingTimeHistory: Int = 1000
    var performanceAlertThreshold: Long = 1000 // milliseconds

    // Client settings
    var testClientCount: Int = 2
    var enableTestClients: Boolean = true
    var testClientPrefix: String = "TestClient"

    // Health monitoring
    var healthCheckInterval: Long = 5000 // milliseconds
    var errorRateThreshold: Double = 0.1 // 10%
    var enableHealthAlerts: Boolean = true

    // System resource monitoring
    var enableMemoryMonitoring: Boolean = true
    var memoryAlertThreshold: Double = 0.8 // 80%
    var enableGCMonitoring: Boolean = false

    /**
     * Load configuration from system properties or environment variables
     * This allows runtime configuration without recompiling
     */
    fun loadFromSystemProperties() {
        enableDashboard = System.getProperty("monitoring.dashboard.enabled", "true").toBoolean()
        dashboardAutoStart = System.getProperty("monitoring.dashboard.autoStart", "true").toBoolean()
        dashboardStartupDelay = System.getProperty("monitoring.dashboard.startupDelay", "3000").toLong()

        enableConsoleStats = System.getProperty("monitoring.console.enabled", "true").toBoolean()
        consoleStatsInterval = System.getProperty("monitoring.console.interval", "10000").toLong()
        useColoredConsoleOutput = System.getProperty("monitoring.console.colored", "true").toBoolean()

        enableFileLogging = System.getProperty("monitoring.logging.enabled", "true").toBoolean()
        logFileName = System.getProperty("monitoring.logging.filename", "chat_monitoring.log")

        testClientCount = System.getProperty("monitoring.testClients.count", "2").toInt()
        enableTestClients = System.getProperty("monitoring.testClients.enabled", "true").toBoolean()

        healthCheckInterval = System.getProperty("monitoring.health.interval", "5000").toLong()
        errorRateThreshold = System.getProperty("monitoring.health.errorThreshold", "0.1").toDouble()

        enableMemoryMonitoring = System.getProperty("monitoring.memory.enabled", "true").toBoolean()
        memoryAlertThreshold = System.getProperty("monitoring.memory.threshold", "0.8").toDouble()
    }

    /**
     * Print current configuration to console
     */
    fun printConfiguration() {
        val monitoring = MonitoringIntegration.getMonitoringService()
        val stats = monitoring.getSystemStats()

        println("\n📋 MONITORING CONFIGURATION:")
        println("   Dashboard Enabled: $enableDashboard")
        println("   Dashboard Auto-Start: $dashboardAutoStart")
        println("   Console Stats: $enableConsoleStats")
        println("   File Logging: $enableFileLogging")
        println("   Test Clients: $testClientCount")
        println("   Health Monitoring: $enableHealthAlerts")
        println("   Memory Monitoring: $enableMemoryMonitoring")
        println("   Active Users: ${stats.getInteger("activeUsers")}")
        println("   Total Registered Users: ${stats.getLong("totalUsersRegistered")}")
        println()
    }

    /**
     * Validate configuration and apply defaults for invalid values
     */
    fun validateAndCorrect() {
        if (dashboardStartupDelay < 1000) {
            println("⚠️ Dashboard startup delay too low, setting to 1000ms")
            dashboardStartupDelay = 1000
        }

        if (consoleStatsInterval < 5000) {
            println("⚠️ Console stats interval too low, setting to 5000ms")
            consoleStatsInterval = 5000
        }

        if (testClientCount < 0) {
            println("⚠️ Test client count cannot be negative, setting to 0")
            testClientCount = 0
        }

        if (testClientCount > 10) {
            println("⚠️ Test client count too high, limiting to 10")
            testClientCount = 10
        }

        if (errorRateThreshold < 0.0 || errorRateThreshold > 1.0) {
            println("⚠️ Error rate threshold must be between 0.0 and 1.0, setting to 0.1")
            errorRateThreshold = 0.1
        }

        if (memoryAlertThreshold < 0.5 || memoryAlertThreshold > 0.95) {
            println("⚠️ Memory alert threshold should be between 0.5 and 0.95, setting to 0.8")
            memoryAlertThreshold = 0.8
        }
    }

    /**
     * Enable development mode with more verbose logging and additional features
     */
    fun enableDevelopmentMode() {
        enableDashboard = true
        dashboardAutoStart = true
        enableConsoleStats = true
        consoleStatsInterval = 5000 // More frequent updates
        enableFileLogging = true
        enableTestClients = true
        testClientCount = 3 // More test clients
        enableHealthAlerts = true
        enableMemoryMonitoring = true
        useColoredConsoleOutput = true

        println("🔧 Development mode enabled - Enhanced monitoring active")
    }

    /**
     * Enable production mode with optimized settings
     */
    fun enableProductionMode() {
        enableDashboard = false // Usually disabled in production
        dashboardAutoStart = false
        enableConsoleStats = true
        consoleStatsInterval = 30000 // Less frequent updates
        enableFileLogging = true
        enableTestClients = false // No test clients in production
        testClientCount = 0
        enableHealthAlerts = true
        enableMemoryMonitoring = true
        useColoredConsoleOutput = false // Better for log files
        enableLogRotation = true

        println("🏭 Production mode enabled - Optimized monitoring active")
    }
}