package com.app.radiator.ui.components

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.radiator.R
import com.app.radiator.matrix.timeline.TimelineItemVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

sealed interface MessageCompose {
  object NewMessage : MessageCompose

  data class Action(val item: TimelineItemVariant.Event, val action: MessageAction) : MessageCompose
}

@OptIn(ExperimentalComposeUiApi::class)
@Preview
@Composable
fun MessageComposer(
  modifier: Modifier = Modifier,
  composeFlow: MutableStateFlow<MessageCompose> = MutableStateFlow(MessageCompose.NewMessage),
  sendMessageOp: (String) -> Unit = { Log.w("MessageComposer", "Send message not implemented") },
) {
  val (messageInput, setMessage) = remember { mutableStateOf("") }
  val keyboardController = LocalSoftwareKeyboardController.current
  val mContext = LocalContext.current
  val cs = rememberCoroutineScope()
  fun sendMessage() {
    sendMessageOp(messageInput)
  }

  fun showAddOnToMessage() {
    Toast.makeText(mContext, "Add images, links, etc...", Toast.LENGTH_LONG).show()
  }

  suspend fun emitComposeState(msgComposeState: MessageCompose = MessageCompose.NewMessage) {
    composeFlow.emit(msgComposeState)
  }

  val composeAction = composeFlow.collectAsState()

  BackHandler(enabled = composeAction.value !is MessageCompose.NewMessage) {
    cs.launch {
      emitComposeState()
    }
  }

  Column {
    when (val act = composeAction.value) {
      is MessageCompose.Action -> {
        when (act.action) {
          MessageAction.Reply, MessageAction.ThreadReply, MessageAction.Quote -> {
            Box(
              modifier =
              Modifier
                .border(
                  width = 4.dp,
                  color = Color(235, 235, 235),
                  shape = RoundedCornerShape(5.dp)
                )
                .clip(RoundedCornerShape(5.dp))
                .padding(7.dp)
                .heightIn(0.dp, 300.dp)
            )
            {
              RoomMessageItem(item = act.item, onClick = {}, onClickHold = {})
              Icon(
                modifier = Modifier
                  .align(TopEnd)
                  .scale(0.5f)
                  .clickable {
                    cs.launch { emitComposeState() }
                  },
                imageVector = ImageVector.vectorResource(id = R.drawable.close_button_32),
                contentDescription = null,
                tint = Color.Red
              )
            }
          }

          MessageAction.React -> TODO()
          MessageAction.Edit -> TODO()
          MessageAction.Delete -> TODO()
          MessageAction.Share -> TODO()
        }
      }

      MessageCompose.NewMessage -> {}
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
          maxLines = 10,
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
          label = { Text("Message...") }
        )
      }
    }
  }
}
