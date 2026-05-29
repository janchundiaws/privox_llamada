package com.futura.privox_app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futura.privox_app.data.remote.SocketService
import com.futura.privox_app.ui.components.PrivoxTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    contactId: String,
    contactName: String,
    onBack: () -> Unit,
    socketService: SocketService
) {
    var messageText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        socketService.events.collect { event ->
            val type = event["type"] as? String
            if (type == "chat-message") {
                val from = event["from"] as? String
                val content = event["content"] as? String
                if (from == contactId && content != null) {
                    messages.add(ChatMessage(content, false))
                }
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFFF9FAFB),
        topBar = {
            PrivoxTopBar(
                title = contactName,
                onBack = onBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) { message ->
                    ChatBubble(message)
                }
            }

            // Input area
            Surface(
                color = Color.White,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Escribe un mensaje...", color = Color(0xFF9CA3AF)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF3F4F6),
                            unfocusedContainerColor = Color(0xFFF3F4F6),
                            disabledContainerColor = Color(0xFFF3F4F6),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                socketService.sendChatMessage(contactId, messageText)
                                messages.add(ChatMessage(messageText, true))
                                messageText = ""
                            }
                        },
                        containerColor = Color(0xFF2575FC),
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp),
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (message.isFromMe) Color(0xFF2575FC) else Color(0xFFE5E7EB)
    val textColor = if (message.isFromMe) Color.White else Color(0xFF111827)
    val shape = if (message.isFromMe) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.content,
                color = textColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                fontSize = 15.sp
            )
        }
    }
}

data class ChatMessage(
    val content: String,
    val isFromMe: Boolean
)
