package com.futura.privox_app.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futura.privox_app.data.remote.AuthService
import com.futura.privox_app.data.remote.SocketService
import com.futura.privox_app.ui.components.PrivoxTopBar
import com.futura.privox_app.utils.CryptoManager.decrypt
import com.futura.privox_app.utils.CryptoManager.encrypt
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    contactId: String,
    contactName: String,
    onBack: () -> Unit,
    socketService: SocketService
) {
    val context = LocalContext.current
    val authService = remember { AuthService(context) }
    var messageText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()
    var isLoadingHistory by remember { mutableStateOf(true) }

    var offset by remember { mutableIntStateOf(0) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMoreMessages by remember { mutableStateOf(true) }

    val pageSize = 10

    // Helper para obtener timestamp actual en formato ISO
    fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    suspend fun loadMoreMessages() {

        if (isLoadingMore || !hasMoreMessages) return

        isLoadingMore = true

        val result = authService.getChatHistory(
            contactId,
            pageSize,
            offset
        )

        if (result.isSuccess) {

            val newMessages = result.getOrNull() ?: emptyList()

            if (newMessages.isEmpty()) {
                hasMoreMessages = false
            } else {

                val parsedMessages = newMessages.reversed().map { msg ->

                    ChatMessage(
                        content = msg["content"]?.toString() ?: "",
                        isFromMe = msg["from"]?.toString() != contactId,
                        id = msg["messageId"]?.toString()
                            ?: msg["_id"]?.toString(),
                        status = msg["status"]?.toString(),
                        createdAt = msg["createdAt"]?.toString()
                    )
                }

                messages.addAll(0, parsedMessages)

                offset += pageSize
            }
        }

        isLoadingMore = false
    }

    // Cargar historial de mensajes usando getChatHistory
    LaunchedEffect(Unit) {
        isLoadingHistory = true
        val result = authService.getChatHistory(
            contactId,
            pageSize,
            offset
        )
        offset += pageSize
        if (result.isSuccess) {
            val historyMessages = result.getOrNull() ?: emptyList()
            messages.clear()

            historyMessages.reversed().forEach { msg ->
                val content = decrypt(msg["content"]?.toString() ?: "")
                val from = msg["from"]?.toString() ?: ""
                val messageId = msg["messageId"]?.toString() ?: msg["_id"]?.toString()
                val status = msg["status"]?.toString()
                val createdAt = msg["createdAt"]?.toString()

                val isFromMe = from != contactId
                messages.add(ChatMessage(content, isFromMe, messageId, status, createdAt))
            }
        } else {
            Log.e("ChatScreen", "Error al cargar historial: ${result.exceptionOrNull()?.message}")
        }
        isLoadingHistory = false
    }

    // Escuchar mensajes en tiempo real
    LaunchedEffect(Unit) {
        socketService.events.collect { event ->
            val type = event["type"] as? String

            if (type == "chat-message") {
                val from = event["from"] as? String
                val content = event["content"] as? String
                val messageId = event["messageId"] as? String
                val createdAt = event["createdAt"]?.toString() ?: getCurrentTimestamp()

                if (from == contactId && content != null) {
                    messages.add(ChatMessage(content, false, messageId, status = "sent", createdAt = createdAt))
                    if (messageId != null) {
                        socketService.sendChatRead(messageId, contactId)
                    }
                }
            }

            if (type == "chat-read") {
                val messageId = event["messageId"] as? String
                if (messageId != null) {
                    // Buscamos el mensaje en la lista para actualizar su estado a "read"
                    val index = messages.indexOfLast { it.id == messageId }
                    if (index != -1) {
                        messages[index] = messages[index].copy(status = "read")
                    }
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
            if (isLoadingHistory && messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF2575FC))
                }
            } else {
                val listState = rememberLazyListState()

                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.lastIndex)
                    }
                }
                LaunchedEffect(listState) {

                    snapshotFlow {
                        listState.firstVisibleItemIndex
                    }.collect { index ->

                        if (
                            index <= 2 &&
                            !isLoadingMore &&
                            hasMoreMessages
                        ) {
                            loadMoreMessages()
                        }
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    items(messages) { message ->
                        ChatBubble(message)
                    }
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
                                socketService.sendChatMessage(contactId, encrypt(messageText))
                                messages.add(ChatMessage(
                                    content = messageText,
                                    isFromMe = true,
                                    status = "sent",
                                    createdAt = getCurrentTimestamp()
                                ))
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
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                message.content?.let {
                    Text(
                        text = it,
                        color = textColor,
                        fontSize = 15.sp
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatCreatedAt(message.createdAt),
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    if (message.isFromMe && message.status != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        val icon = if (message.status == "read") Icons.Default.DoneAll else Icons.Default.Done
                        val iconColor = if (message.status == "read") Color(0xFF38BDF8) else Color.White.copy(alpha = 0.7f)
                        Icon(
                            imageVector = icon,
                            contentDescription = message.status,
                            tint = iconColor,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatCreatedAt(isoString: String?): String {
    if (isoString.isNullOrEmpty()) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(isoString)
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        formatter.format(date ?: Date())
    } catch (e: Exception) {
        ""
    }
}

data class ChatMessage(
    val content: String? =  null,
    val isFromMe: Boolean,
    val id: String? = null,
    val status: String? = null,
    val createdAt: String? = null
)
