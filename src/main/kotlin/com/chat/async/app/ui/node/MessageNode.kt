package com.chat.async.app.ui.node

import com.chat.async.app.helper.asStream
import com.chat.async.app.helper.enums.MessageType
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.*
import javafx.scene.shape.Rectangle
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MessageNode(
    val sender: String,
    val content: String,
    val type: MessageType,
    val isOwnMessage: Boolean,
    val messageId: String? = null,
    timestamp: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
    val onEdit: ((String) -> Unit)? = null,
    val onDownload: (() -> Unit)? = null,
    val onPreview: (() -> Unit)? = null,
    val onCopy: (() -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    private val fileBytes: ByteArray? = null
) : HBox(10.0) {

    init {
        padding = Insets(5.0, 10.0, 5.0, 10.0)
        alignment = if (isOwnMessage) Pos.CENTER_RIGHT else Pos.CENTER_LEFT

        val messageBox = createMessageBox()
        val senderLabel = createSenderLabel()
        val timestampLabel = createTimestampLabel(timestamp)
        val contentNode = createContentNode()

        messageBox.children.addAll(senderLabel, contentNode, timestampLabel)

        val contextMenu = createContextMenu()
        messageBox.setOnContextMenuRequested { event ->
            contextMenu.show(messageBox, event.screenX, event.screenY)
        }

        children.add(messageBox)

        if (type == MessageType.SYSTEM && onClick != null) {
            messageBox.style += "-fx-cursor: hand;"
            messageBox.setOnMouseClicked { onClick.invoke() }
        }
    }

    private fun createMessageBox() = VBox(5.0).apply {
        style = """
            -fx-background-color: ${if (isOwnMessage) "#DCF8C6" else "#FFFFFF"};
            -fx-background-radius: 10;
            -fx-padding: 8px;
            -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 1);
        """.trimIndent()
    }

    private fun createSenderLabel() = Label(sender).apply {
        style = """
            -fx-font-weight: bold;
            -fx-text-fill: ${if (isOwnMessage) "#075E54" else "#000000"};
        """.trimIndent()
    }

    private fun createTimestampLabel(timestamp: String) = Label(timestamp).apply {
        style = """
            -fx-font-size: 10px;
            -fx-text-fill: #666666;
        """.trimIndent()
    }

    private fun createContentNode() = when (type) {
        MessageType.TEXT -> createTextContent()
        MessageType.FILE -> createFileContent()
        MessageType.IMAGE -> createImageContent()
        MessageType.SYSTEM -> createSystemContent()
    }

    private fun createTextContent() = Label(content).apply {
        style = """
            -fx-text-fill: #000000;
            -fx-wrap-text: true;
            -fx-max-width: 300px;
        """.trimIndent()
        isWrapText = true
    }

    private fun createFileContent() = HBox(10.0).apply {
        style = """
            -fx-background-color: #F5F5F5;
            -fx-background-radius: 5;
            -fx-padding: 5px;
            -fx-cursor: hand;
        """.trimIndent()

        children.addAll(
            ImageView(Image(asStream("icon_64.png"))).apply {
                fitWidth = 24.0
                fitHeight = 24.0
            },
            VBox(5.0).apply {
                children.addAll(
                    Label("File").apply {
                        style = "-fx-font-weight: bold;"
                    },
                    Label(content).apply {
                        style = "-fx-font-size: 12px;"
                    }
                )
            }
        )

        setOnMouseClicked { onPreview?.invoke() }
    }

    private fun createImageContent() = StackPane().apply {
        val imageView = ImageView().apply {
            try {
                image = if (fileBytes != null) {
                    Image(ByteArrayInputStream(fileBytes))
                } else {
                    Image(ByteArrayInputStream(content.toByteArray()))
                }
                fitWidth = 200.0
                fitHeight = 200.0
                isPreserveRatio = true
            } catch (e: Exception) {
                // Fallback if image loading fails
                println("Error loading image: ${e.message}")
            }
        }

        val clip = Rectangle().apply {
            width = 200.0
            height = 200.0
            arcWidth = 10.0
            arcHeight = 10.0
        }
        imageView.clip = clip

        children.add(imageView)
        setOnMouseClicked { onPreview?.invoke() }
    }

    private fun createSystemContent() = Label(content).apply {
        style = """
            -fx-font-style: italic;
            -fx-text-fill: gray;
            -fx-alignment: center;
        """.trimIndent()
    }

    private fun createContextMenu() = ContextMenu().apply {
        items.addAll(
            MenuItem("Copy").apply {
                setOnAction {
                    when (type) {
                        MessageType.TEXT -> copyToClipboard(content)
                        MessageType.FILE -> onDownload?.invoke()
                        MessageType.IMAGE -> {
                            onPreview?.invoke()
                            items.add(MenuItem("Download").apply {
                                setOnAction { onDownload?.invoke() }
                            })
                        }
                        MessageType.SYSTEM -> {
                            val idPattern = "ID: ([A-Za-z0-9-]+)".toRegex()
                            val matchResult = idPattern.find(content)
                            if (matchResult != null) {
                                copyToClipboard(matchResult.groupValues[1])
                            } else {
                                copyToClipboard(content)
                            }
                        }
                    }
                }
            }
        )

        if (type == MessageType.SYSTEM && content.contains("ID:")) {
            items.add(MenuItem("Copy ID Only").apply {
                setOnAction {
                    val idPattern = "ID: ([A-Za-z0-9-]+)".toRegex()
                    val matchResult = idPattern.find(content)
                    matchResult?.let {
                        copyToClipboard(it.groupValues[1])
                    }
                }
            })
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = Clipboard.getSystemClipboard()
        val content = ClipboardContent()
        content.putString(text)
        clipboard.setContent(content)
    }
}