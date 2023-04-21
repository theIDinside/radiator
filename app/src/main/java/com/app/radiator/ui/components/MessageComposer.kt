package com.app.radiator.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun MessageComposer(
    modifier: Modifier = Modifier,
    sendMessageOp: (String) -> Unit = { Log.w("MessageComposer", "Send message not implemented") },
) {
    val (messageInput, setMessage) = remember { mutableStateOf("") }

    fun sendMessage() {
        sendMessageOp(messageInput)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 2.dp, color = Color.Gray)
            .background(color = Color.White)
    ) {
        TextField(
            modifier = Modifier
                .weight(1f),
            value = messageInput,
            onValueChange = setMessage,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions { sendMessage() },
            placeholder = { Text("Write message...") }
        )
    }
}
