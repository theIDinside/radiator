package com.app.radiator.ui.components

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class)
@Preview
@Composable
fun MessageComposer(
    modifier: Modifier = Modifier,
    sendMessageOp: (String) -> Unit = { Log.w("MessageComposer", "Send message not implemented") },
) {
    val (messageInput, setMessage) = remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val mContext = LocalContext.current
    fun sendMessage() {
        sendMessageOp(messageInput)
    }

    fun showAddOnToMessage() {
        Toast.makeText(mContext, "Add images, links, etc...", Toast.LENGTH_LONG).show()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // .border(width = 2.dp, color = Color.Gray)
            .background(color = Color.White),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier,
            contentAlignment = Alignment.Center,
            propagateMinConstraints = true
        ) {
            Row(modifier = Modifier, horizontalArrangement = Arrangement.Center) {
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    modifier = Modifier
                        .background(color = Color.White)
                        .size(35.dp)
                        .offset(x = 2.5.dp),
                    onClick = { showAddOnToMessage() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.LightGray,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.Gray,
                        disabledContentColor = Color.Gray
                    ),
                    contentPadding = PaddingValues(0.dp),
                    shape = CircleShape
                )
                {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "content description",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(15.dp))
        Box(modifier = Modifier.padding(top = 5.dp, bottom = 5.dp, end = 5.dp)) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = messageInput,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                onValueChange = setMessage,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        sendMessage()
                        setMessage("")
                        keyboardController?.hide()
                    }),
                // placeholder = {  },
                label = { Text("Message...") }
            )
        }
    }
}
