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

class ChatUI(
    private val onSend: (toId: String, message: String) -> Unit,
    private val onSendFile: (toId: String, fileName: String, fileBytes: ByteArray) -> Unit,
    private val onSendImage: (toId: String, imageBytes: ByteArray) -> Unit
) {
    // UI Components
    private val stage = Stage()
    private val registerPane = StackPane()
    private val chatPane = BorderPane()
    private val rootPane = StackPane(registerPane, chatPane)

    // Input fields
    private lateinit var nameField: TextField
    private lateinit var toIdField: TextField
    private lateinit var inputField: TextField

    // Buttons
    private lateinit var registerButton: Button
    private lateinit var sendButton: Button
    private lateinit var fileButton: Button
    private lateinit var imageButton: Button

    // Chat area
    lateinit var chatArea: TextArea

    // Preview components
    private val previewStage = Stage(StageStyle.UTILITY)
    private val previewImage = ImageView().apply {
        fitWidth = 300.0
        fitHeight = 300.0
        isPreserveRatio = true
        isVisible = false
    }
    private val previewLabel = Label("File ready to send:")
    private val previewFileName = Label()
    private val previewSendButton = Button("Send")
    private val previewCancelButton = Button("Cancel")

    // State
    private var currentPreviewFile: File? = null
    private var currentPreviewType: PreviewType = PreviewType.NONE
    var onRegister: ((String) -> Unit)? = null

    enum class PreviewType { NONE, IMAGE, FILE }

    init {
        configureMainStage()
        setupPreviewStage()
        setupRegisterPane()
        setupChatPane()
        showRegisterPane()
        stage.show()
    }

    private fun configureMainStage() {
        stage.title = "Chat App"
        stage.scene = Scene(rootPane, 600.0, 500.0)
    }

    private fun setupPreviewStage() {
        previewStage.title = "Preview Before Sending"
        previewStage.scene = Scene(createPreviewPane(), 400.0, 500.0)
        previewCancelButton.setOnAction { resetPreview() }
    }

    private fun createPreviewPane(): VBox {
        return VBox(10.0).apply {
            padding = Insets(20.0)
            alignment = Pos.CENTER
            children.addAll(
                previewLabel,
                previewImage,
                previewFileName,
                HBox(10.0, previewCancelButton, previewSendButton).apply {
                    alignment = Pos.CENTER
                }
            )
        }
    }

    private fun resetPreview() {
        previewStage.hide()
        currentPreviewFile = null
        currentPreviewType = PreviewType.NONE
        previewImage.isVisible = false
        previewImage.image = null
    }

    private fun setupRegisterPane() {
        registerPane.children.add(createRegisterForm())
    }

    private fun createRegisterForm(): VBox {
        return VBox(10.0).apply {
            padding = Insets(20.0)
            alignment = Pos.CENTER
            children.addAll(
                Label("Name:"),
                TextField().apply {
                    nameField = this
                    promptText = "Enter your name"
                },
                Button("Register").apply {
                    registerButton = this
                    setOnAction { handleRegistration() }
                }
            )
        }
    }

    private fun handleRegistration() {
        nameField.text.trim().takeIf { it.isNotEmpty() }?.let {
            onRegister?.invoke(it)
        }
    }

    private fun setupChatPane() {
        chatPane.top = createRecipientPanel()
        chatPane.center = createChatArea()
        chatPane.bottom = createInputPanel()
    }

    private fun createRecipientPanel(): HBox {
        return HBox(10.0).apply {
            padding = Insets(10.0)
            children.addAll(
                Label("Send to ID:"),
                TextField().apply {
                    toIdField = this
                    prefWidth = 100.0
                }
            )
        }
    }

    private fun createChatArea(): ScrollPane {
        chatArea = TextArea().apply {
            isEditable = false
            isWrapText = true
        }
        return ScrollPane(chatArea).apply {
            isFitToWidth = true
            isFitToHeight = true
        }
    }

    private fun createInputPanel(): VBox {
        return VBox(10.0).apply {
            padding = Insets(10.0)
            children.addAll(
                createMessageInputRow(),
                createAttachmentButtons()
            )
        }
    }

    private fun createMessageInputRow(
    ) = HBox(10.0).apply {
        children.addAll(
            TextField().apply {
                inputField = this
                HBox.setHgrow(this, Priority.ALWAYS)
            },
            Button("Send").apply {
                sendButton = this
                setOnAction { sendTextMessage() }
            }
        )
    }

    private fun createAttachmentButtons(
    ) = HBox(10.0).apply {
        children.addAll(
            Button("Attach File").apply {
                fileButton = this
                setOnAction {
                    // show file chooser
                    FileChooser().apply {
                        title = "Select File to Send"
                    }.showOpenDialog(stage)?.let { file ->
                        prepareFilePreview(file, PreviewType.FILE) {
                            onSendFile(toIdField.text.trim(), file.name, file.readBytes())
                            "[File sent: ${file.name}]".appendOwnMessage(chatArea)
                        }
                    }
                }
            },
            Button("Attach Image").apply {
                imageButton = this
                setOnAction {
                    // show image chooser
                    FileChooser().apply {
                        title = "Select Image to Send"
                        extensionFilters.addAll(
                            FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
                        )
                    }.showOpenDialog(stage)?.let { file ->
                        try {
                            prepareImagePreview(file)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            "Failed to load image".appendSystemMessage(chatArea)
                        }
                    }
                }
            }
        )
    }

    private fun prepareFilePreview(
        file: File,
        type: PreviewType,
        onSendAction: () -> Unit
    ) {
        currentPreviewFile = file
        currentPreviewType = type

        previewLabel.text = "File ready to send:"
        previewFileName.text = file.name
        previewFileName.graphic = FileIconView(file)

        previewSendButton.setOnAction {
            if (toIdField.text.trim().isNotEmpty()) {
                onSendAction()
                resetPreview()
            }
        }

        previewStage.show()
    }

    private fun prepareImagePreview(file: File) {
        val image = Image(file.toURI().toString())
        previewImage.image = image
        previewImage.isVisible = true

        prepareFilePreview(file, PreviewType.IMAGE) {
            onSendImage(toIdField.text.trim(), image.toByteArray(file.extension))
            "[Image sent: ${file.name}]".appendOwnMessage(chatArea)
        }

        previewLabel.text = "Image preview:"
        previewFileName.graphic = null
    }

    private fun sendTextMessage() {
        val toId = toIdField.text.trim()
        val message = inputField.text.trim()

        if (toId.isNotEmpty() && message.isNotEmpty()) {
            onSend(toId, message)
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
            val saveButton = Button("Save $fileName").apply {
                setOnAction { saveFile(fileName, bytes) }
            }

            chatArea.appendText("\n\uD83D\uDCCE [$senderId] sent a file: $fileName\n")
        }
    }

    private fun saveFile(fileName: String, bytes: ByteArray) {
        FileChooser().apply {
            initialFileName = fileName
            title = "Save File"
        }.showSaveDialog(stage)?.let { file ->
            file.writeBytes(bytes)
            "[File saved to: ${file.absolutePath}]".appendSystemMessage(chatArea)
        }
    }
}

class FileIconView(private val file: File) : StackPane() {
    init {
        children.add(Label(getFileIcon(file)))
    }

    private fun getFileIcon(file: File): String {
        return when {
            file.name.endsWith(".pdf") -> "📄"
            file.name.endsWith(".doc", ignoreCase = true) -> "📝"
            file.name.endsWith(".xls", ignoreCase = true) -> "📊"
            file.name.endsWith(".zip", ignoreCase = true) -> "🗜"
            file.name.matches(Regex(".*\\.(png|jpg|jpeg|gif)", RegexOption.IGNORE_CASE)) -> "🖼"
            else -> "📁"
        }
    }
}