package com.chat.async.app

import com.chat.async.app.ui.node.MessageNode
import javafx.application.Platform
import javafx.embed.swing.SwingFXUtils
import javafx.scene.control.Alert
import javafx.scene.control.ListView
import javafx.scene.image.Image
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO

const val EXCHANGE = "chat_exchange"

fun ByteArray.encodeBase64(): String = Base64.getEncoder().encodeToString(this)!!

fun String.decodeBase64(): ByteArray = Base64.getDecoder().decode(this)!!

fun Image.toByteArray(extension: String): ByteArray {
    val bufferedImage = SwingFXUtils.fromFXImage(this, null)
    val outputStream = ByteArrayOutputStream()
    ImageIO.write(bufferedImage, extension, outputStream)
    return outputStream.toByteArray()
}

fun generateUserId(): String = UUID.randomUUID().toString().take(8)
fun generateMessageId(): String = UUID.randomUUID().toString()
fun generateGroupId(): String = UUID.randomUUID().toString().take(8)

fun showAlert(
    title: String,
    message: String
) {
    Platform.runLater {
        Alert(Alert.AlertType.INFORMATION).apply {
            this.title = title
            headerText = message
        }.showAndWait()
    }
}

fun String.appendSystemMessage(
    chatArea: ListView<MessageNode>
) {
    Platform.runLater {
        chatArea.items.add(MessageNode(
            "System",
            this,
            MessageNode.MessageType.SYSTEM,
            false
        ))
    }
}

fun String.appendOwnMessage(
    chatArea: ListView<MessageNode>
) {
    Platform.runLater {
        chatArea.items.add(MessageNode(
            "You",
            this,
            MessageNode.MessageType.TEXT,
            true
        ))
    }
}

fun Any.asStream(path: String) = this.javaClass.getResourceAsStream("/assets/$path")
    ?: throw RuntimeException("Resource not found: /assets/$path")
