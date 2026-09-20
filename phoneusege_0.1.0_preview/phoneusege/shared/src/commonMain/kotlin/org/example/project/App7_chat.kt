package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

data class ChatBubble(
    val id: Long,
    val text: String,
    val isUser: Boolean,
    val time: String = ""
)

@Composable
fun AIAssistChatScreen(
    pixelFont: FontFamily,
    onClose: () -> Unit
) {
    val messages = remember { mutableStateListOf<ChatBubble>() }
    var messageIdCounter by remember { mutableStateOf(0L) }
    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F11))
    ) {
        // 顶部标题栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color.Black)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "AI Assist System",
                color = Color.White,
                fontSize = 18.sp,
                fontFamily = pixelFont,
                fontWeight = FontWeight.Bold
            )
        }

        // 聊天消息列表
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { msg ->
                ChatBubbleItem(msg, pixelFont)
            }
        }

        // 底部输入框
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF4A4A4F), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                textStyle = TextStyle(color = Color.White, fontFamily = pixelFont, fontSize = 15.sp),
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        Text(
                            text = "Ask anything...",
                            color = Color.Gray,
                            fontFamily = pixelFont,
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isSending) Color.Gray else Color(0xFF3DDC84),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = !isSending) {
                        if (inputText.isNotBlank()) {
                            val userMsg = ChatBubble(
                                id = messageIdCounter++,
                                text = inputText.trim(),
                                isUser = true,
                                time = "now"
                            )
                            messages.add(userMsg)
                            scope.launch { listState.scrollToItem(messages.lastIndex) }

                            val currentInput = inputText
                            inputText = ""
                            isSending = true

                            scope.launch {
                                // TODO: 后续在这里调用 requestChat
                                delay(1200) // 模拟网络延迟
                                val reply = ChatBubble(
                                    id = messageIdCounter++,
                                    text = "This is a simulated reply to: $currentInput",
                                    isUser = false,
                                    time = "now"
                                )
                                messages.add(reply)
                                isSending = false
                                listState.scrollToItem(messages.lastIndex)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↑",
                    color = Color.Black,
                    fontSize = 22.sp,
                    fontFamily = pixelFont,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    msg: ChatBubble,
    pixelFont: FontFamily
) {
    val alignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val background = if (msg.isUser) Color(0xFF3DDC84) else Color(0xFF1E1E1E)
    val textColor = if (msg.isUser) Color.Black else Color.White

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(background, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                text = msg.text,
                color = textColor,
                fontSize = 14.sp,
                fontFamily = pixelFont,
                lineHeight = 18.sp
            )
        }
    }
}