package com.chat.async.app

import javafx.application.Platform
import javafx.embed.swing.SwingFXUtils
import javafx.scene.control.TextArea
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

fun generateUserId() = UUID.randomUUID().toString().take(8)

fun String.appendSystemMessage(
    chatArea: TextArea
) {
    Platform.runLater {
        chatArea.appendText("\n[SYSTEM] $this\n")
    }
}

fun String.appendOwnMessage(
    chatArea: TextArea
) {
    Platform.runLater {
        chatArea.appendText("\n[YOU] $this\n")
    }
}