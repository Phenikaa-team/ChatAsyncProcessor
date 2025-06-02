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
import kotlin.system.exitProcess

class ChatApp : Application() {
    companion object {
        lateinit var vertx: Vertx
            private set
    }

    override fun start(primaryStage: Stage) {
        MonitoringConfiguration.loadFromSystemProperties()
        MonitoringConfiguration.validateAndCorrect()
        //MonitoringConfiguration.enableProductionMode()
        MonitoringIntegration.initializeMonitoring()

        if (MonitoringConfiguration.enableDashboard && MonitoringConfiguration.dashboardAutoStart) {
            Thread.sleep(MonitoringConfiguration.dashboardStartupDelay)
            MonitoringIntegration.startMonitoringDashboard()
        }

        vertx = Vertx.vertx()

        "Chat application starting with Vert.x".logAsSystem()

        vertx.deployVerticle(ServerVerticle()) { ar ->
            if (ar.succeeded()) {
                "Server verticle deployed successfully".logAsSystem()

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
        "Chat application shutting down".logAsSystem()
        try {
            vertx.close { ar ->
                if (ar.succeeded()) {
                    "Vert.x closed successfully".logAsSystem()
                } else {
                    "Failed to close Vert.x".logAsError(ar.cause())
                }
                MonitoringIntegration.shutdown()
                exitProcess(0)
            }
        } catch (e: Exception) {
            "Exception during shutdown".logAsError(e)
            MonitoringIntegration.shutdown()
            exitProcess(1)
        }
    }
}

fun main() {
    Application.launch(ChatApp::class.java)
}