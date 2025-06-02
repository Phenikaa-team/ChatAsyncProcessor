package com.chat.async

import com.chat.async.app.monitoring.MonitoringConfiguration
import com.chat.async.app.monitoring.MonitoringIntegration
import com.chat.async.app.monitoring.logAsError
import com.chat.async.app.monitoring.logAsSystem
import com.chat.async.app.verticle.ClientVerticle
import com.chat.async.app.verticle.ServerVerticle
import io.vertx.core.Vertx
import javafx.application.Application
import javafx.stage.Stage

class ChatApp : Application() {
    override fun start(primaryStage: Stage) {
        MonitoringConfiguration.loadFromSystemProperties()
        MonitoringConfiguration.validateAndCorrect()
        //MonitoringConfiguration.enableProductionMode()
        MonitoringIntegration.initializeMonitoring()

        if (MonitoringConfiguration.enableDashboard && MonitoringConfiguration.dashboardAutoStart) {
            Thread.sleep(MonitoringConfiguration.dashboardStartupDelay)
            MonitoringIntegration.startMonitoringDashboard()
        }

        val vertx = Vertx.vertx()

        "Chat application starting with Vert.x".logAsSystem()

        // 1. First deploy server verticle
        vertx.deployVerticle(ServerVerticle()) { ar ->
            if (ar.succeeded()) {
                "Server verticle deployed successfully".logAsSystem()

                // 2. Only deploy clients after server is ready
                if (MonitoringConfiguration.enableTestClients) {
                    deployTestClients(vertx)
                }
            } else {
                "Failed to deploy server verticle".logAsError(ar.cause())
            }
        }
    }

    private fun deployTestClients(vertx: Vertx) {
        repeat(MonitoringConfiguration.testClientCount) { index ->
            try {
                val client = ClientVerticle()
                vertx.deployVerticle(client) { ar ->
                    if (ar.succeeded()) {
                        "Client verticle $index deployed successfully".logAsSystem()
                    } else {
                        "Failed to deploy client verticle $index: ${ar.cause()?.message}".logAsError(ar.cause())
                    }
                }
            } catch (e: Exception) {
                "Exception while creating client verticle $index: ${e.message}".logAsError(e)
            }
        }
    }

    override fun stop() {
        MonitoringIntegration.shutdown()
        "Chat application shutting down".logAsSystem()
    }
}

fun main() {
    Application.launch(ChatApp::class.java)
}