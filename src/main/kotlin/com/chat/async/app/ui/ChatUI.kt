package com.chat.async.app.ui

import com.chat.async.app.helper.*
import com.chat.async.app.helper.enums.MessageType
import com.chat.async.app.helper.enums.PreviewType
import com.chat.async.app.ui.group.GroupManager
import com.chat.async.app.ui.group.ChatGroup
import com.chat.async.app.ui.node.MessageNode
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.*
import javafx.stage.FileChooser
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.util.Callback
import java.io.ByteArrayInputStream
import java.io.File

class ChatUI(
    private val onSend: (toId: String, message: String) -> Unit,
    private val onSendFile: (toId: String, fileName: String, fileBytes: ByteArray) -> Unit,
    private val onSendImage: (toId: String, imageBytes: ByteArray) -> Unit,
    private val onEditMessage: (messageId: String, newContent: String) -> Unit,
    private val onCreateGroup: (ChatGroup) -> Unit,
    private val onJoinGroup: (String) -> Unit,
    private val onLeaveGroup: (String) -> Unit
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
    private lateinit var uuidField: TextField

    // Buttons
    private lateinit var registerButton: Button
    private lateinit var sendButton: Button
    private lateinit var fileButton: Button
    private lateinit var imageButton: Button
    private lateinit var groupManagerButton: Button

    // Chat area
    lateinit var chatArea: ListView<MessageNode>

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
    var onRegister: ((Pair<String, String>) -> Unit)? = null
    private var hostServices: javafx.application.HostServices? = null

    private var currentUserId: String = ""
    private var currentUsername: String = ""

    // Group Manager
    private val groupManager: GroupManager by lazy {
        GroupManager(
            currentUserId = currentUserId,
            currentUsername = currentUsername,
            onCreateGroup = onCreateGroup,
            onJoinGroup = { groupId ->
                onJoinGroup(groupId)
                Platform.runLater {
                    updateGroupUI()
                }
            },
            onLeaveGroup = { groupId ->
                onLeaveGroup(groupId)
                Platform.runLater {
                    updateGroupUI()
                }
            }
        ).apply {
            Platform.runLater {
                updateGroupUI()
            }
        }
    }

    init {
        stage.title = "Chat App"
        stage.scene = Scene(rootPane, 600.0, 500.0)

        stage.icons.addAll(
            Image(this.asStream("icon_16.png")),
            Image(this.asStream("icon_32.png")),
            Image(this.asStream("icon_64.png"))
        )

        previewStage.title = "Preview Before Sending"
        previewStage.scene = Scene(createPreviewPane(), 400.0, 500.0)
        previewCancelButton.setOnAction { resetPreview() }

        registerPane.children.add(createRegisterForm())

        chatPane.top = createRecipientPanel()
        chatPane.center = createChatArea()
        chatPane.bottom = createInputPanel()

        registerPane.isVisible = true
        chatPane.isVisible = false
        createUserInfoBar()
        stage.show()
    }

    private fun createPreviewPane(
    ) = VBox(10.0).apply {
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

    private fun createUserInfoBar() = HBox(10.0).apply {
        alignment = Pos.TOP_RIGHT
        padding = Insets(5.0)
        style = "-fx-background-color: #f0f0f0;"

        val userLabel = Label().apply {
            style = "-fx-font-weight: bold;"
        }

        val uuidLabel = Label().apply {
            style = "-fx-font-size: 11px; -fx-text-fill: #666666;"
            tooltip = Tooltip("Click to copy UUID")
            setOnMouseClicked {
                copyToClipboard(currentUserId)
                showAlert("Copied", "UUID copied to clipboard")
            }
        }

        children.addAll(userLabel, uuidLabel)

        fun updateInfo() {
            userLabel.text = currentUsername
            uuidLabel.text = "(${currentUserId.take(8)}...)"
        }

        updateInfo()
    }

    fun setUserId(id: String, username: String) {
        currentUserId = id
        currentUsername = username

        Platform.runLater {
            showChatPane()
            groupManagerButton.isDisable = false
            groupManagerButton.text = "Groups"
            ("📌 Registration successful!").appendSystemMessage(chatArea)
        }
    }

    private fun updateGroupUI() {
        groupManagerButton.text = "Groups (${groupManager.getAllGroups().size})"

        (chatPane.top as? HBox)?.children?.filterIsInstance<MenuButton>()?.firstOrNull()?.let { menuButton ->
            menuButton.items.clear()
            groupManager.getAllGroups().forEach { groupId ->
                groupManager.getGroupById(groupId.id)?.let { group ->
                    menuButton.items.add(MenuItem("${group.name} (${group.id.take(8)}...)").apply {
                        setOnAction {
                            toIdField.text = group.id
                        }
                    })
                }
            }
            if (menuButton.items.isEmpty()) {
                menuButton.items.add(MenuItem("No groups joined").apply { isDisable = true })
            }
            menuButton.text = "Joined Groups (${groupManager.getAllGroups().size})"
        }
    }

    private fun resetPreview() {
        previewStage.hide()
        currentPreviewFile = null
        currentPreviewType = PreviewType.NONE
        previewImage.isVisible = false
        previewImage.image = null
    }

    private fun createRegisterForm() = VBox(10.0).apply {
        padding = Insets(20.0)
        alignment = Pos.CENTER

        // Username field
        children.addAll(
            Label("Username:"),
            TextField().apply {
                alignment = Pos.CENTER
                nameField = this
                promptText = "Enter your username"
                prefWidth = 300.0
            }
        )

        // UUID field with random generation button
        children.addAll(
            Label("UUID (leave empty to generate):"),
            HBox(5.0).apply {
                alignment = Pos.CENTER
                children.addAll(
                    TextField().apply {
                        uuidField = this
                        promptText = "Leave empty to generate random ID"
                        prefWidth = 250.0
                    },
                    Button("⟳").apply {
                        tooltip = Tooltip("Generate Random ID")
                        style = "-fx-font-size: 14;"
                        prefWidth = 40.0
                        setOnAction {
                            uuidField.text = generateUserId()
                        }
                    }
                )
            }
        )

        // Register button
        children.add(
            Button("Register").apply {
                registerButton = this
                prefWidth = 300.0
                setOnAction { handleRegistration() }
                // Allow to Enter key to register
                setOnKeyPressed { event ->
                    if (event.code.toString() == "ENTER") {
                        handleRegistration()
                    }
                }
            }
        )

        // Set Enter key action for text fields
        nameField.setOnKeyPressed { event ->
            if (event.code.toString() == "ENTER") {
                handleRegistration()
            }
        }

        uuidField.setOnKeyPressed { event ->
            if (event.code.toString() == "ENTER") {
                handleRegistration()
            }
        }
    }

    private fun handleRegistration() {
        val username = nameField.text.trim()
        var uuid = uuidField.text.trim()

        if (username.isEmpty()) {
            showAlert("Error", "Username cannot be empty")
            return
        }

        if (uuid.isEmpty()) {
            uuid = generateUserId()
            uuidField.text = uuid
        }

        onRegister?.invoke(username to uuid)
        println(username to uuid)
    }

    fun addMessageToChat(
        sender: String,
        content: String,
        type: MessageType,
        isOwnMessage: Boolean,
        messageId: String? = null,
        fileBytes: ByteArray? = null
    ) {
        val messageNode = MessageNode(
            sender = sender,
            content = content,
            type = type,
            isOwnMessage = isOwnMessage,
            messageId = messageId,
            onEdit = { newContent ->
                messageId?.let { id ->
                    onEditMessage(id, newContent)
                    Platform.runLater {
                        val items = chatArea.items
                        for (i in 0 until items.size) {
                            if (items[i].messageId == id) {
                                items[i] = MessageNode(
                                    sender = sender,
                                    content = newContent,
                                    type = type,
                                    isOwnMessage = isOwnMessage,
                                    messageId = id,
                                    onEdit = { newerContent ->
                                        onEditMessage(id, newerContent)
                                    }
                                )
                                break
                            }
                        }
                    }
                }
            },
            onDownload = {
                fileBytes?.let { bytes ->
                    saveFile(content, bytes)
                }
            },
            onPreview = {
                when (type) {
                    MessageType.FILE -> showFilePreview(content, fileBytes!!)
                    MessageType.IMAGE -> showImagePreview(fileBytes!!)
                    else -> {}
                }
            }
        )

        Platform.runLater {
            chatArea.items.add(messageNode)
            chatArea.scrollTo(chatArea.items.size - 1)
        }
    }

    private fun showFilePreview(fileName: String, bytes: ByteArray) {
        val previewStage = Stage(StageStyle.UTILITY).apply {
            title = "File Preview: $fileName"
            scene = Scene(VBox(10.0).apply {
                padding = Insets(20.0)
                alignment = Pos.CENTER
                children.addAll(
                    Label("File: $fileName").apply {
                        style = "-fx-font-weight: bold;"
                    },
                    HBox(10.0).apply {
                        alignment = Pos.CENTER
                        children.addAll(
                            Button("View").apply {
                                setOnAction {
                                    try {
                                        val tempFile = File.createTempFile("chat_file", ".tmp")
                                        tempFile.writeBytes(bytes)
                                        hostServices?.showDocument(tempFile.absolutePath)
                                    } catch (e: Exception) {
                                        showAlert("Error", "Could not open file: ${e.message}")
                                    }
                                }
                            },
                            Button("Download").apply {
                                setOnAction { saveFile(fileName, bytes) }
                            }
                        )
                    }
                )
            })
            width = 300.0
            height = 150.0
        }
        previewStage.show()
    }

    private fun showImagePreview(bytes: ByteArray) {
        val previewStage = Stage(StageStyle.UTILITY).apply {
            title = "Image Preview"
            scene = Scene(VBox(10.0).apply {
                padding = Insets(20.0)
                alignment = Pos.CENTER
                children.addAll(
                    ImageView(Image(ByteArrayInputStream(bytes))).apply {
                        fitWidth = 300.0
                        fitHeight = 300.0
                        isPreserveRatio = true
                    },
                    HBox(10.0).apply {
                        alignment = Pos.CENTER
                        children.addAll(
                            Button("Copy Image").apply {
                                setOnAction {
                                    val image = Image(ByteArrayInputStream(bytes))
                                    Clipboard.getSystemClipboard().setContent(
                                        ClipboardContent().apply { putImage(image) }
                                    )
                                }
                            },
                            Button("Download").apply {
                                setOnAction {
                                    saveFile("image_${System.currentTimeMillis()}.png", bytes)
                                }
                            },
                            Button("View Full").apply {
                                setOnAction {
                                    val fullStage = Stage().apply {
                                        scene = Scene(StackPane(ImageView(Image(ByteArrayInputStream(bytes)))))
                                        title = "Full Image View"
                                    }
                                    fullStage.show()
                                }
                            }
                        )
                    }
                )
            })
        }
        previewStage.show()
    }

    private fun sendTextMessage() {
        val toId = toIdField.text.trim()
        val message = inputField.text.trim()
        val messageId = generateMessageId()

        if (toId.isNotEmpty() && message.isNotEmpty()) {
            onSend(toId, message)
            addMessageToChat("You", message, MessageType.TEXT, true, messageId)
            inputField.text = ""
        }
    }

    private fun createRecipientPanel() = HBox(10.0).apply {
        padding = Insets(10.0)
        children.addAll(
            Label("Send to ID:"),
            TextField().apply {
                toIdField = this
                prefWidth = 200.0
                promptText = "User ID or Group ID"
            },
            Button("📋").apply {
                tooltip = Tooltip("Paste from clipboard")
                prefWidth = 30.0
                setOnAction {
                    val clipboard = Clipboard.getSystemClipboard()
                    if (clipboard.hasString()) {
                        toIdField.text = clipboard.string
                    }
                }
            },
            Button("Groups").apply {
                groupManagerButton = this
                setOnAction {
                    try {
                        text = "Groups (${groupManager.getAllGroups().size})"
                        groupManager.showGroupManagerDialog(stage)
                    } catch (e: Exception) {
                        showAlert("Error", "Groups not available yet")
                    }
                }
            }
        )
    }

    private fun createChatArea(): BorderPane {
        val borderPane = BorderPane()
        borderPane.top = createUserInfoBar()
        borderPane.center = ScrollPane(ListView<MessageNode>().apply {
            chatArea = this
            cellFactory = Callback {
                object : ListCell<MessageNode>() {
                    override fun updateItem(item: MessageNode?, empty: Boolean) {
                        super.updateItem(item, empty)
                        graphic = if (empty || item == null) null else item
                    }
                }
            }
        }).apply {
            isFitToWidth = true
            isFitToHeight = true
        }
        return borderPane
    }

    private fun createInputPanel() = VBox(10.0).apply {
        padding = Insets(10.0)
        children.addAll(
            createMessageInputRow(),
            createAttachmentButtons()
        )
    }

    private fun createMessageInputRow() = HBox(10.0).apply {
        children.addAll(
            TextField().apply {
                inputField = this
                HBox.setHgrow(this, Priority.ALWAYS)
                // Allow to Enter key to send a message
                setOnKeyPressed { event ->
                    if (event.code.toString() == "ENTER") {
                        sendTextMessage()
                    }
                }
            },
            Button("Send").apply {
                sendButton = this
                setOnAction { sendTextMessage() }
            }
        )
    }

    private fun createAttachmentButtons() = HBox(10.0).apply {
        children.addAll(
            Button("Attach File").apply {
                fileButton = this
                setOnAction {
                    // show file chooser
                    FileChooser().apply {
                        title = "Select File to Send"
                    }.showOpenDialog(stage)?.let { file ->
                        prepareFilePreview(file, PreviewType.FILE) {
                            val toId = toIdField.text.trim()
                            if (toId.isNotEmpty()) {
                                onSendFile(toId, file.name, file.readBytes())
                                addMessageToChat("You", file.name, MessageType.FILE, true, generateMessageId(), file.readBytes())
                            }
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

    private fun prepareImagePreview(
        file: File
    ) {
        val image = Image(file.toURI().toString())
        previewImage.image = image
        previewImage.isVisible = true

        prepareFilePreview(file, PreviewType.IMAGE) {
            val toId = toIdField.text.trim()
            if (toId.isNotEmpty()) {
                onSendImage(toId, image.toByteArray(file.extension))
                addMessageToChat("You", "image", MessageType.IMAGE, true, generateMessageId(), file.readBytes())
            }
        }

        previewLabel.text = "Image preview:"
        previewFileName.graphic = null
    }

    private fun showChatPane() {
        registerPane.isVisible = false
        chatPane.isVisible = true
    }

    private fun saveFile(fileName: String, bytes: ByteArray) {
        val fileChooser = FileChooser().apply {
            initialFileName = fileName
        }
        val file = fileChooser.showSaveDialog(stage) ?: return

        try {
            file.writeBytes(bytes)
            Platform.runLater {
                chatArea.items.add(MessageNode("System", "File saved to ${file.absolutePath}",
                    MessageType.SYSTEM, false))
            }
        } catch (e: Exception) {
            Platform.runLater {
                chatArea.items.add(MessageNode("System", "Failed to save file: ${e.message}",
                    MessageType.SYSTEM, false))
            }
        }
    }

    private fun showAlert(title: String, message: String) {
        Platform.runLater {
            Alert(Alert.AlertType.INFORMATION).apply {
                this.title = title
                headerText = message
            }.showAndWait()
        }
    }

    fun addGroup(group: ChatGroup) {
        groupManager.addGroup(group)
        Platform.runLater {
            updateGroupUI()
        }
    }
}

class FileIconView(
    file : File
) : StackPane() {
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