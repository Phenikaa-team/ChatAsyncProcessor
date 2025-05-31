package com.chat.async.app

import java.awt.*
import java.util.function.Consumer
import javax.swing.*

class ChatUI(
    var onSend: (toId: String, message: String) -> Unit
) {
    private val frame = JFrame("Chat App")
    private val registerPanel = JPanel()
    private val chatPanel = JPanel()

    private lateinit var nameField: JTextField
    private lateinit var registerButton: JButton
    private lateinit var toIdField: JTextField
    private lateinit var inputField: JTextField
    private lateinit var sendButton: JButton
    lateinit var chatArea: JTextArea

    var onRegister: Consumer<String>? = null

    init {
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(500, 400)
        frame.layout = CardLayout()

        setupRegisterPanel()
        setupChatPanel()

        frame.add(registerPanel, "REGISTER")
        frame.add(chatPanel, "CHAT")
        showRegisterPanel()

        frame.isVisible = true
    }

    private fun setupRegisterPanel() {
        registerPanel.layout = FlowLayout()

        val nameLabel = JLabel("Tên:")
        nameField = JTextField(15)
        registerButton = JButton("Đăng ký")

        registerButton.addActionListener {
            val name = nameField.text.trim()
            if (name.isNotEmpty()) {
                onRegister?.accept(name)
            }
        }

        registerPanel.add(nameLabel)
        registerPanel.add(nameField)
        registerPanel.add(registerButton)
    }

    private fun setupChatPanel() {
        chatPanel.layout = BorderLayout()

        val topPanel = JPanel(FlowLayout())
        topPanel.add(JLabel("Gửi đến ID:"))
        toIdField = JTextField(10)
        topPanel.add(toIdField)

        chatArea = JTextArea()
        chatArea.isEditable = false
        val scrollPane = JScrollPane(chatArea)

        val bottomPanel = JPanel(BorderLayout())
        inputField = JTextField()
        sendButton = JButton("Gửi")

        sendButton.addActionListener {
            val toId = toIdField.text.trim()
            val message = inputField.text.trim()
            if (toId.isNotEmpty() && message.isNotEmpty()) {
                onSend.invoke(toId, message)
                message.appendOwnMessage(chatArea)
                inputField.text = ""
            }
        }

        bottomPanel.add(inputField, BorderLayout.CENTER)
        bottomPanel.add(sendButton, BorderLayout.EAST)

        chatPanel.add(topPanel, BorderLayout.NORTH)
        chatPanel.add(scrollPane, BorderLayout.CENTER)
        chatPanel.add(bottomPanel, BorderLayout.SOUTH)
    }

    private fun showRegisterPanel() {
        (frame.contentPane.layout as CardLayout).show(frame.contentPane, "REGISTER")
    }

    private fun showChatPanel() {
        (frame.contentPane.layout as CardLayout).show(frame.contentPane, "CHAT")
    }

    fun setUserId(id: String) {
        SwingUtilities.invokeLater {
            showChatPanel()
            ("📌 Đăng ký thành công! ID của bạn là: $id").appendSystemMessage(chatArea)
        }
    }
}

