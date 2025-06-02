package com.chat.async.app.helper

import com.chat.async.app.helper.enums.MessageType
import com.chat.async.app.ui.node.MessageNode
import javafx.application.Platform
import javafx.embed.swing.SwingFXUtils
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

fun formatDuration(
    millis: Long
): String {
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

fun formatLastActivity(lastActivity: Long): String {
    val diff = System.currentTimeMillis() - lastActivity
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m ago"
        minutes > 0 -> "${minutes}m ${seconds % 60}s ago"
        else -> "${seconds}s ago"
    }
}

fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1 -> String.format("%.2f GB", gb)
        mb >= 1 -> String.format("%.2f MB", mb)
        kb >= 1 -> String.format("%.2f KB", kb)
        else -> "$bytes B"
    }
}

fun String.appendSystemMessage(
    chatArea: ListView<MessageNode>
) {
    Platform.runLater {
        chatArea.items.add(MessageNode(
            "System",
            this,
            MessageType.SYSTEM,
            false
        ))
    }
}

fun Any.asStream(path: String) = this.javaClass.getResourceAsStream("/assets/$path")
    ?: throw RuntimeException("Resource not found: /assets/$path")
