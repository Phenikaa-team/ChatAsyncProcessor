package com.chat.async.app.ui

import com.chat.async.app.appendOwnMessage
import com.chat.async.app.appendSystemMessage
import com.chat.async.app.toByteArray
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.*
import javafx.stage.FileChooser
import javafx.stage.Stage
import javafx.stage.StageStyle
import java.io.File
import java.util.*

class ChatUI(
    private val onSend: (toId: String, message: String) -> Unit,
    private val onSendFile: (toId: String, fileName: String, fileBytes: ByteArray) -> Unit,
    private val onSendImage: (toId: String, imageBytes: ByteArray) -> Unit
) {
    private val stage = Stage()
    private val registerPane = StackPane()
    private val chatPane = BorderPane()
    private val rootPane = StackPane(registerPane, chatPane)

    private lateinit var nameField: TextField
    private lateinit var registerButton: Button
    private lateinit var toIdField: TextField
    private lateinit var inputField: TextField
    private lateinit var sendButton: Button
    private lateinit var fileButton: Button
    private lateinit var imageButton: Button
    lateinit var chatArea: TextArea

    private val previewStage = Stage(StageStyle.UTILITY)
    private val previewImage = ImageView()
    private val previewLabel = Label("File ready to send:")
    private val previewFileName = Label()
    private val previewSendButton = Button("Send")
    private val previewCancelButton = Button("Cancel")
    private var currentPreviewFile: File? = null
    private var currentPreviewType: PreviewType = PreviewType.NONE

    enum class PreviewType {
        NONE, IMAGE, FILE
    }

    var onRegister: ((String) -> Unit)? = null

    init {
        stage.title = "Chat App"
        stage.scene = Scene(rootPane, 600.0, 500.0)

        setupRegisterPane()
        setupChatPane()
        setupPreviewStage()

        showRegisterPane()

        stage.show()
    }

    private fun setupPreviewStage() {
        previewStage.title = "Preview Before Sending"
        val previewPane = VBox(10.0).apply {
            padding = Insets(20.0)
            alignment = Pos.CENTER
        }

        val buttonPane = HBox(10.0).apply {
            alignment = Pos.CENTER
            children.addAll(previewCancelButton, previewSendButton)
        }

        previewImage.fitWidth = 300.0
        previewImage.fitHeight = 300.0
        previewImage.isPreserveRatio = true
        previewImage.isVisible = false

        previewPane.children.addAll(
            previewLabel,
            previewImage,
            previewFileName,
            buttonPane
        )

        previewStage.scene = Scene(previewPane, 400.0, 500.0)

        previewCancelButton.setOnAction {
            previewStage.hide()
            currentPreviewFile = null
            currentPreviewType = PreviewType.NONE
        }
    }

    private fun setupRegisterPane() {
        val layout = VBox(10.0)
        layout.padding = Insets(20.0)
        layout.alignment = Pos.CENTER

        nameField = TextField().apply {
            promptText = "Enter your name"
        }
        registerButton = Button("Register").apply {
            setOnAction {
                val name = nameField.text.trim()
                if (name.isNotEmpty()) {
                    onRegister?.invoke(name)
                }
            }
        }

        layout.children.addAll(
            Label("Name:"),
            nameField,
            registerButton
        )

        registerPane.children.add(layout)
    }

    private fun setupChatPane() {
        // Top panel with recipient ID
        val topPanel = HBox(10.0).apply {
            padding = Insets(10.0)
            children.addAll(
                Label("Send to ID:"),
                TextField().apply {
                    toIdField = this
                    prefWidth = 100.0
                }
            )
        }

        // Chat area
        chatArea = TextArea().apply {
            isEditable = false
            isWrapText = true
        }
        val scrollPane = ScrollPane(chatArea).apply {
            isFitToWidth = true
            isFitToHeight = true
        }

        // File and image buttons
        fileButton = Button("Attach File").apply {
            setOnAction { showFileChooser() }
        }

        imageButton = Button("Attach Image").apply {
            setOnAction { showImageChooser() }
        }

        val attachmentButtons = HBox(10.0, fileButton, imageButton)

        // Bottom panel with input and send button
        val bottomPanel = VBox(10.0).apply {
            padding = Insets(10.0)
            children.addAll(
                HBox(10.0).apply {
                    children.addAll(
                        TextField().apply {
                            inputField = this
                            HBox.setHgrow(this, Priority.ALWAYS)
                        },
                        Button("Send").apply {
                            sendButton = this
                            setOnAction {
                                sendTextMessage()
                            }
                        }
                    )
                },
                attachmentButtons
            )
        }

        chatPane.top = topPanel
        chatPane.center = scrollPane
        chatPane.bottom = bottomPanel
    }

    private fun showFileChooser() {
        val fileChooser = FileChooser().apply {
            title = "Select File to Send"
        }
        val file = fileChooser.showOpenDialog(stage)
        if (file != null) {
            currentPreviewFile = file
            currentPreviewType = PreviewType.FILE

            previewLabel.text = "File ready to send:"
            previewImage.isVisible = false
            previewFileName.text = file.name
            previewFileName.graphic = FileIconView(file)

            previewSendButton.setOnAction {
                val toId = toIdField.text.trim()
                if (toId.isNotEmpty()) {
                    onSendFile(toId, file.name, file.readBytes())
                    "[File sent: ${file.name}]".appendOwnMessage(chatArea)
                    previewStage.hide()
                }
            }

            previewStage.show()
        }
    }

    private fun showImageChooser() {
        val fileChooser = FileChooser().apply {
            title = "Select Image to Send"
            extensionFilters.addAll(
                FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
            )
        }
        val file = fileChooser.showOpenDialog(stage)
        if (file != null) {
            currentPreviewFile = file
            currentPreviewType = PreviewType.IMAGE

            try {
                val image = Image(file.toURI().toString())
                previewImage.image = image
                previewImage.isVisible = true
                previewLabel.text = "Image preview:"
                previewFileName.text = file.name
                previewFileName.graphic = null

                previewSendButton.setOnAction {
                    val toId = toIdField.text.trim()
                    if (toId.isNotEmpty()) {
                        onSendImage(toId, image.toByteArray(getFileExtension(file)))
                        "[Image sent: ${file.name}]".appendOwnMessage(chatArea)
                        previewStage.hide()
                    }
                }

                previewStage.show()
            } catch (e: Exception) {
                e.printStackTrace()
                "Failed to load image".appendSystemMessage(chatArea)
            }
        }
    }

    private fun getFileExtension(file: File): String {
        val name = file.name
        val lastDot = name.lastIndexOf('.')
        return if (lastDot > 0) name.substring(lastDot + 1).lowercase(Locale.getDefault()) else ""
    }

    private fun sendTextMessage() {
        val toId = toIdField.text.trim()
        val message = inputField.text.trim()
        if (toId.isNotEmpty() && message.isNotEmpty()) {
            onSend.invoke(toId, message)
            message.appendOwnMessage(chatArea)
            inputField.text = ""
        }
    }

    private fun showRegisterPane() {
        registerPane.isVisible = true
        chatPane.isVisible = false
    }

    private fun showChatPane() {
        registerPane.isVisible = false
        chatPane.isVisible = true
    }

    fun setUserId(id: String) {
        Platform.runLater {
            showChatPane()
            ("📌 Registration successful! Your ID is: $id").appendSystemMessage(chatArea)
        }
    }

    fun showReceivedFile(senderId: String, fileName: String, bytes: ByteArray) {
        Platform.runLater {
            val saveButton = Button("Save $fileName")
            saveButton.setOnAction {
                val fileChooser = FileChooser().apply {
                    initialFileName = fileName
                    title = "Save File"
                }
                val file = fileChooser.showSaveDialog(stage)
                if (file != null) {
                    file.writeBytes(bytes)
                    "[File saved to: ${file.absolutePath}]".appendSystemMessage(chatArea)
                }
            }

            chatArea.appendText("\n\uD83D\uDCCE [$senderId] sent a file: ")
            chatArea.appendText(fileName)
            chatArea.appendText("\n")
        }
    }
}

class FileIconView(
    file : File
) : StackPane() {
    init {
        val icon = when {
            file.name.endsWith(".pdf") -> "📄"
            file.name.endsWith(".doc", ignoreCase = true) -> "📝"
            file.name.endsWith(".xls", ignoreCase = true) -> "📊"
            file.name.endsWith(".zip", ignoreCase = true) -> "🗜"
            file.name.matches(Regex(".*\\.(png|jpg|jpeg|gif)", RegexOption.IGNORE_CASE)) -> "🖼"
            else -> "📁"
        }
        children.add(Label(icon))
    }
}