package com.chat.async.app

import javax.swing.JTextArea
import javax.swing.SwingUtilities

const val EXCHANGE = "chat_exchange"

fun String.appendOwnMessage(
    chatArea : JTextArea
) = this.also {
    SwingUtilities.invokeLater {
        chatArea.append("🟩 Bạn: $this\n")
    }
}

fun String.appendMessage(
    chatArea : JTextArea
) = this.also {
    SwingUtilities.invokeLater {
        chatArea.append("🟦 Nhận: $this\n")
    }
}

fun String.appendSystemMessage(
    chatArea : JTextArea
) = this.also {
    SwingUtilities.invokeLater {
        chatArea.append("🔔 $this\n")
    }
}