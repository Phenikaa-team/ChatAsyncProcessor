package com.chat.async.app.ui.group

import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.stage.Modality
import javafx.stage.Stage
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class ChatGroup(
    val id: String,
    val name: String,
    val members: MutableSet<String> = mutableSetOf(),
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val type: String,
    val targetId: String, // Can be userId or groupId
    val isGroup: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val data: String? = null // For files/images
)

class GroupManager(
    private val currentUserId: String,
    private val currentUsername: String,
    private val onCreateGroup: (ChatGroup) -> Unit,
    private val onJoinGroup: (String) -> Unit,
    private val onLeaveGroup: (String) -> Unit
) {
    private val groups = ConcurrentHashMap<String, ChatGroup>()
    private val joinedGroups = mutableSetOf<String>()

    fun showGroupManagerDialog(parentStage: Stage) {
        val dialog = Stage().apply {
            title = "Group Manager"
            initModality(Modality.APPLICATION_MODAL)
            initOwner(parentStage)
        }

        val root = VBox(10.0).apply {
            padding = Insets(20.0)
            prefWidth = 400.0
            prefHeight = 500.0
        }

        // Create Group Section
        val createGroupSection = TitledPane("Create New Group", createGroupForm())
        createGroupSection.isExpanded = false

        // Join Group Section
        val joinGroupSection = TitledPane("Join Group", createJoinGroupForm())
        joinGroupSection.isExpanded = false

        // My Groups Section
        val myGroupsSection = TitledPane("My Groups", createMyGroupsList())
        myGroupsSection.isExpanded = true

        root.children.addAll(
            Label("Group Chat Management").apply {
                style = "-fx-font-size: 16px; -fx-font-weight: bold;"
            },
            createGroupSection,
            joinGroupSection,
            myGroupsSection,
            HBox(10.0).apply {
                alignment = Pos.CENTER_RIGHT
                children.add(
                    Button("Close").apply {
                        setOnAction { dialog.close() }
                    }
                )
            }
        )

        dialog.scene = Scene(root)
        dialog.showAndWait()
    }

    private fun createGroupForm() = VBox(10.0).apply {
        val nameField = TextField().apply {
            promptText = "Enter group name"
        }

        val createButton = Button("Create Group").apply {
            setOnAction {
                val groupName = nameField.text.trim()
                if (groupName.isNotEmpty()) {
                    val group = ChatGroup(
                        id = generateGroupId(),
                        name = groupName,
                        createdBy = currentUserId
                    )
                    group.members.add(currentUserId)
                    groups[group.id] = group
                    joinedGroups.add(group.id)
                    onCreateGroup(group)
                    nameField.clear()
                    showAlert("Success", "Group '${group.name}' created with ID: ${group.id}")
                } else {
                    showAlert("Error", "Group name cannot be empty")
                }
            }
        }

        children.addAll(
            Label("Group Name:"),
            nameField,
            createButton
        )
    }

    private fun createJoinGroupForm() = VBox(10.0).apply {
        val groupIdField = TextField().apply {
            promptText = "Enter group ID"
        }

        val joinButton = Button("Join Group").apply {
            setOnAction {
                val groupId = groupIdField.text.trim()
                if (groupId.isNotEmpty()) {
                    if (!joinedGroups.contains(groupId)) {
                        joinedGroups.add(groupId)
                        onJoinGroup(groupId)
                        groupIdField.clear()
                        showAlert("Success", "Joined group: $groupId")
                    } else {
                        showAlert("Info", "You are already in this group")
                    }
                } else {
                    showAlert("Error", "Group ID cannot be empty")
                }
            }
        }

        children.addAll(
            Label("Group ID:"),
            groupIdField,
            joinButton
        )
    }

    private fun createMyGroupsList() = VBox(10.0).apply {
        val listView = ListView<String>().apply {
            prefHeight = 200.0
            cellFactory = javafx.util.Callback {
                object : ListCell<String>() {
                    override fun updateItem(item: String?, empty: Boolean) {
                        super.updateItem(item, empty)
                        if (empty || item == null) {
                            graphic = null
                        } else {
                            val group = groups[item]
                            graphic = HBox(10.0).apply {
                                alignment = Pos.CENTER_LEFT
                                children.addAll(
                                    VBox().apply {
                                        children.addAll(
                                            Label(group?.name ?: item).apply {
                                                style = "-fx-font-weight: bold;"
                                            },
                                            Label("ID: $item").apply {
                                                style = "-fx-font-size: 10px; -fx-text-fill: gray;"
                                            }
                                        )
                                    },
                                    Region().apply {
                                        HBox.setHgrow(this, Priority.ALWAYS)
                                    },
                                    Button("Leave").apply {
                                        style = "-fx-font-size: 10px;"
                                        setOnAction {
                                            joinedGroups.remove(item)
                                            onLeaveGroup(item)
                                            refreshGroupsList(listView)
                                            showAlert("Info", "Left group: ${group?.name ?: item}")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        refreshGroupsList(listView)

        children.addAll(
            Label("Joined Groups:"),
            listView,
            Button("Refresh").apply {
                setOnAction { refreshGroupsList(listView) }
            }
        )
    }

    private fun refreshGroupsList(listView: ListView<String>) {
        Platform.runLater {
            listView.items.clear()
            listView.items.addAll(joinedGroups)
        }
    }

    private fun generateGroupId(): String = "group_${UUID.randomUUID().toString().take(8)}"

    private fun showAlert(title: String, message: String) {
        Platform.runLater {
            Alert(Alert.AlertType.INFORMATION).apply {
                this.title = title
                headerText = message
            }.showAndWait()
        }
    }

    fun addGroup(group: ChatGroup) {
        groups[group.id] = group
    }

    fun getJoinedGroups(): Set<String> = joinedGroups.toSet()

    fun isInGroup(groupId: String): Boolean = joinedGroups.contains(groupId)
}