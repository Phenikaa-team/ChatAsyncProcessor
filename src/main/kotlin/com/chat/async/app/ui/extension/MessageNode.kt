package com.chat.async.app.ui.extension

import com.chat.async.app.asStream
import javafx.application.Platform
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
    val onPreview: (() -> Unit)? = null
) : HBox(10.0) {

    enum class MessageType { TEXT, FILE, IMAGE, SYSTEM }

    init {
        padding = Insets(5.0, 10.0, 5.0, 10.0)
        alignment = if (isOwnMessage) Pos.CENTER_RIGHT else Pos.CENTER_LEFT

        val messageBox = VBox(5.0).apply {
            style = """
                -fx-background-color: ${if (isOwnMessage) "#DCF8C6" else "#FFFFFF"};
                -fx-background-radius: 10;
                -fx-padding: 8px;
                -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 1);
            """.trimIndent()
        }

        val senderLabel = Label(sender).apply {
            style = """
                -fx-font-weight: bold;
                -fx-text-fill: ${if (isOwnMessage) "#075E54" else "#000000"};
            """.trimIndent()
        }

        val timestampLabel = Label(timestamp).apply {
            style = """
                -fx-font-size: 10px;
                -fx-text-fill: #666666;
            """.trimIndent()
        }

        val contentNode = when (type) {
            MessageType.TEXT -> createTextContent()
            MessageType.FILE -> createFileContent()
            MessageType.IMAGE -> createImageContent()
            MessageType.SYSTEM -> createSystemContent()
        }

        messageBox.children.addAll(senderLabel, contentNode, timestampLabel)

        val contextMenu = ContextMenu().apply {
            items.addAll(
                MenuItem("Copy").apply {
                    setOnAction {
                        when (type) {
                            MessageType.TEXT -> copyToClipboard(content)
                            MessageType.FILE -> onDownload?.invoke()
                            MessageType.IMAGE -> onPreview?.invoke()
                            else -> {}
                        }
                    }
                }
            )

            if (type == MessageType.IMAGE || type == MessageType.FILE) {
                items.add(MenuItem("Download").apply {
                    setOnAction { onDownload?.invoke() }
                })
            }

            if (onEdit != null) {
                items.add(MenuItem("Edit").apply {
                    setOnAction {
                        val dialog = TextInputDialog(content)
                        dialog.title = "Edit Message"
                        dialog.headerText = "Edit your message:"
                        dialog.showAndWait().ifPresent { newContent ->
                            onEdit.invoke(newContent)
                        }
                    }
                })
            }
        }

        messageBox.setOnContextMenuRequested { event ->
            contextMenu.show(messageBox, event.screenX, event.screenY)
        }

        children.add(messageBox)
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
            image = Image(ByteArrayInputStream(content.toByteArray()))
            fitWidth = 200.0
            fitHeight = 200.0
            isPreserveRatio = true
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

    private fun copyToClipboard(text: String) {
        val clipboard = Clipboard.getSystemClipboard()
        val content = ClipboardContent()
        content.putString(text)
        clipboard.setContent(content)
    }
}
