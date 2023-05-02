package com.app.radiator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.radiator.R
import com.app.radiator.matrix.timeline.IEvent
import com.app.radiator.matrix.timeline.Message
import com.app.radiator.ui.routes.avatarData

@Preview
@Composable
fun PreviewLastMessage() {
  LastMessage(event = preview)
}


val lastMessageBgColor = Color(244, 246, 250)

@Composable
fun LastMessageText(text: String) {
  Text(text = text, overflow = TextOverflow.Ellipsis, maxLines = 1, minLines = 1)
}

@Composable
fun LastMessage(event: IEvent.Event) {
  Row(
    modifier = Modifier
      .background(color = lastMessageBgColor, shape = RoundedCornerShape(10.dp))
      .border(width = 2.dp, color = Color(227, 246, 250), shape = RoundedCornerShape(10.dp))
      .padding(3.dp)
      .clip(RoundedCornerShape(3.dp)),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      modifier = Modifier
        .scale(0.65f, 0.8f)
        .padding(start = 5.dp, end = 5.dp, top = 2.5.dp, bottom = 2.5.dp),
      imageVector = ImageVector.vectorResource(id = R.drawable.thread_icons_room),
      contentDescription = "",
      tint = Color(193, 200, 217)
    )
    val avatar = remember { event.senderProfile.avatarData(event.senderId) }
    if(avatar != null)
      Avatar(avatarData = avatar, size = 20.dp)
    when (event.message) {
      is Message.Audio -> LastMessageText(event.message.body)
      is Message.Emote -> LastMessageText(event.message.body)
      is Message.File -> LastMessageText(event.message.body)
      is Message.Image -> LastMessageText(event.message.body)
      is Message.Notice -> LastMessageText(event.message.body)
      is Message.Sticker -> LastMessageText(event.message.body)
      is Message.Text -> LastMessageText(event.message.body)
      is Message.Video -> LastMessageText(event.message.body)
      is Message.FailedToParseMessageLike -> TODO()
      is Message.FailedToParseState -> TODO()
      is Message.ProfileChange -> TODO()
      Message.RedactedMessage -> TODO()
      is Message.RoomMembership -> TODO()
      is Message.State -> TODO()
      is Message.UnableToDecrypt -> TODO()
    }

  }
}