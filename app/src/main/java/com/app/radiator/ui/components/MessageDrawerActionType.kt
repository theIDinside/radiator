package com.app.radiator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.radiator.ui.components.MessageDrawerActionType.*
import com.app.radiator.ui.components.general.CenteredRow

@Preview
@Composable
fun PreviewMessageDrawerContents() {
  MessageDrawerContent() {
    when (it) {
      Reply -> TODO()
      ThreadReply -> TODO()
      React -> TODO()
      Edit -> TODO()
      Delete -> TODO()
      Share -> TODO()
      Quote -> TODO()
      NewMessage -> TODO()
    }
  }
}

@Immutable
enum class MessageDrawerActionType {
  Reply, ThreadReply, React, Edit, Delete, Share, Quote, NewMessage
}

@Immutable
data class MessageDrawerAction(
  val text: String,
  val icon: ImageVector,
  val desc: String,
  val action: MessageDrawerActionType,
)

val actions = listOf(
  MessageDrawerAction("Reply", Icons.Default.Send, desc = "Reply to message", action = Reply),
  MessageDrawerAction(
    "Reply in thread", Icons.Default.Refresh, desc = "Reply in thread", action = ThreadReply
  ),
  MessageDrawerAction(
    "Reaction", Icons.Default.ThumbUp, desc = "React to message", action = React
  ),
  MessageDrawerAction("Edit", Icons.Default.Edit, desc = "Edit message", action = Edit),
  MessageDrawerAction("Delete", Icons.Default.Delete, desc = "Delete message", action = Delete),
  MessageDrawerAction("Share", Icons.Default.Share, desc = "Share", action = Share),
  MessageDrawerAction("Quote", Icons.Default.Favorite, desc = "Quote", action = Quote),
)

val messageDrawerActionFontSize = 20.sp

@Composable
fun MessageDrawerContent(eventSink: (MessageDrawerActionType) -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(color = Color(45, 45, 55))
      .border(color = Color(45, 45, 45), width = 4.dp, shape = RoundedCornerShape(5.dp))
      .padding(start = 5.dp, top = 5.dp, bottom = 5.dp)
  ) {
    for (action in actions) {
      CenteredRow(
        modifier = Modifier
          .padding(bottom = 5.dp)
          .clickable {
            eventSink(action.action)
          }
      ) {
        Icon(imageVector = action.icon, contentDescription = action.desc)
        Text(
          text = action.text,
          modifier = Modifier.padding(start = 10.dp),
          color = Color.LightGray,
          fontSize = messageDrawerActionFontSize
        )
      }
    }
  }
}