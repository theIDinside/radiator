package com.app.radiator.ui.components

import android.icu.text.SimpleDateFormat
import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.util.LinkifyCompat
import com.app.radiator.matrix.store.AsyncImageStorage
import com.app.radiator.matrix.store.MxcURI
import com.app.radiator.matrix.timeline.*
import com.app.radiator.matrix.timeline.displayName
import com.app.radiator.ui.routes.avatarData
import com.app.radiator.ui.theme.LinkColor
import org.matrix.rustcomponents.sdk.Reaction
import java.util.*

val previewReactions = listOf(
  Reaction("\uD83E\uDD29", 4u),
  Reaction("\uD83D\uDE18", 9u),
  Reaction("\uD83D\uDE17", 24u),
  Reaction("\uD83D\uDE1A", 48u),
  Reaction("\uD83D\uDE0A", 72u),
  Reaction("\uD83D\uDE19", 8u),
  Reaction("\uD83E\uDD17", 19u),
  Reaction("\uD83E\uDD2D", 23u),
  Reaction("\uD83D\uDE10", 68u),
  Reaction("\uD83E\uDD28", 13u)
).sortedBy { it.count }

val preview = TimelineItemVariant.Event(
  id = Math.random().toInt(),
  uniqueIdentifier = "uniqueId",
  eventId = "eventId",
  isEditable = false,
  isLocal = false,
  isOwn = false,
  isRemote = true,
  localSendState = EventSendState.Sent("eventId"),
  reactions = previewReactions,
  sender = "@simonfarre:matrix.org",
  senderProfile = org.matrix.rustcomponents.sdk.ProfileDetails.Ready(
    avatarUrl=null, displayName = "simonfarre", displayNameAmbiguous = false
  ),
  timestamp = 0u,
  message = Message.Text(
    body = "Text message", inReplyTo = null, isEdited = false, formatted = null
  ),
  groupedByUser = false,
  threadId = null
)

val RoomViewLeftOffset = 7.dp

@Preview
@Composable
fun DayDivider(date: String = "April 10, 2023") {
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .fillMaxWidth()
      .background(color = Color.White)
  ) {
    // TODO: replace with drawBehind?
    Box(
      modifier = Modifier
        .offset(x = 35.dp)
        .size(width = 100.dp, height = 1.dp)
        .background(Color.DarkGray)
        .align(Alignment.CenterStart)
    ) {}
    Text(date, fontSize = 10.sp)
    Box(
      modifier = Modifier
        .offset(x = -35.dp)
        .size(width = 100.dp, height = 1.dp)
        .background(Color.DarkGray)
        .align(Alignment.CenterEnd)
    ) {}
  }

}

@Preview
@Composable
fun TestTwoTextFieldsAsNewline() {
  Column() {
    Text(text = "Foo")
    Text(text = "Bar")
    Text(text = "Foo\nBar")
  }
}

val parsedNodeClickHandlerLogger: (node: ParsedMessageNode) -> Unit = {
  when (it) {
    is ParsedMessageNode.CodeBlock -> Log.d(
      "MessageNodeClick",
      "Clicked code block"
    )

    is ParsedMessageNode.Heading -> Log.d("MessageNodeClick", "Clicked heading")
    is ParsedMessageNode.HrefNode -> Log.d(
      "MessageNodeClick",
      "Clicked link (URL: ${it.url})"
    )

    is ParsedMessageNode.ListNode -> Log.d(
      "MessageNodeClick",
      "Clicked list item"
    )

    is ParsedMessageNode.OrderedList -> Log.d(
      "MessageNodeClick",
      "Clicked ordered list"
    )

    is ParsedMessageNode.Paragraph -> Log.d(
      "MessageNodeClick",
      "Clicked paragraph"
    )

    is ParsedMessageNode.Root -> Log.d("MessageNodeClick", "Clicked root")
    is ParsedMessageNode.TextNode -> {}
    is ParsedMessageNode.Unhandled -> {}
    is ParsedMessageNode.UnorderedList -> Log.d(
      "MessageNodeClick",
      "Clicked unordered list"
    )
  }
}

@Composable
fun TimelineItemVariant.Event.timeStamp(): AnnotatedString = remember {
  val messageTimeStampText =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp.toLong())).toString()
  buildAnnotatedString {
    withStyle(SpanStyle(color = Color.Gray)) {
      append(messageTimeStampText)
    }
  }
}

@Composable
fun TimelineItemVariant.Event.userNameDisplay(): AnnotatedString = remember {
  buildAnnotatedString {
    withStyle(SpanStyle(color = Color(255, 165, 0))) {
      append(senderProfile.displayName() ?: sender)
    }
  }
}

@Preview
@Composable
fun RoomMessageItem(
  modifier: Modifier = Modifier,
  item: TimelineItemVariant.Event = preview,
) {
  RoomMessageItem(modifier=modifier, item=item, onClick = {}, onClickHold = {})
}

@OptIn(ExperimentalFoundationApi::class)
@Preview
@Composable
fun RoomMessageItem(
  modifier: Modifier = Modifier,
  item: TimelineItemVariant.Event = preview,
  onClick: () -> Unit = {},
  onClickHold: (TimelineItemVariant.Event) -> Unit = {},
) {
  val interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
  Box(
    modifier = modifier
      .padding(end = 10.dp, top = 2.dp, bottom = 2.dp)
      .fillMaxWidth()
      .background(color = Color.White),
    contentAlignment = Alignment.CenterStart,
  ) {
    Row(
      modifier = Modifier
        .padding(end = 5.dp)
        .fillMaxWidth()
    ) {
      Column() {
        if (!item.groupedByUser) {
          Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            val avatar = item.senderProfile.avatarData(item.sender)
            if (avatar != null) {
              Avatar(avatarData = avatar, size = 25.dp)
            }

            val userNameDisplayText = item.userNameDisplay()
            Text(userNameDisplayText)
          }
        }
        Row(
          modifier = Modifier
            .offset(RoomViewLeftOffset)
            .fillMaxWidth()
            .combinedClickable(
              indication = LocalIndication.current,
              interactionSource = interactionSource,
              enabled = true,
              onClick = onClick,
              onLongClick = {
                onClickHold(item)
              }), verticalAlignment = Alignment.CenterVertically
        ) {
          val messageTimeStamp = item.timeStamp()
          Text(text = messageTimeStamp, fontSize = 8.sp)
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            when (val contentTypeItem = item.message) {
              is Message.Text -> {
                RoomTextMessage(
                  sender = item.sender,
                  textMsg = contentTypeItem,
                  onClick = onClick,
                  onClickHold = {
                    onClickHold(item)
                  },
                  interactionSource = interactionSource
                )
              }

              is Message.Image -> {
                val uri = MxcURI.Download(contentTypeItem.source)
                AsyncImageStorage.AsyncImageWithLoadingAnimation(
                  modifier = Modifier.border(width = 2.dp, color = Color.Black),
                  url = uri
                )
              }

              is Message.RedactedMessage -> {
                Text("Message deleted", style = TextStyle(color = Color.LightGray))
              }

              is Message.ProfileChange -> {}
              is Message.RoomMembership -> RoomMembership(msg = contentTypeItem)
              is Message.State ->
                contentTypeItem.content.displayText(item.sender)?.let {
                  SubtleRoomNotification(text = it)
                }

              is Message.UnableToDecrypt -> SubtleRoomNotification(text = "Unable to decrypt message")
              is Message.Audio,
              is Message.Emote,
              is Message.FailedToParseMessageLike,
              is Message.FailedToParseState,
              is Message.File,
              is Message.Notice,
              is Message.Sticker,
              is Message.Video
              -> Text(text = "Type: ${contentTypeItem}, ${contentTypeItem.toString()}")
            }
          }
        }
        Row() {
          item.reactions.Display()
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun List<Reaction>.Display() {
  val fontSize = remember { 12.sp }
  FlowRow(Modifier.padding(start = 20.dp, top = 5.dp, end = 30.dp)) {
    for (reaction in this@Display) {
      Row(
        modifier = Modifier
          .padding(start = 2.dp, bottom = 2.dp)
          .background(color = Color(85, 120, 85), shape = RoundedCornerShape(50f))
          .padding(start = 4.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
        horizontalArrangement = Arrangement.End
      ) {
        Text(text = reaction.key, fontSize = fontSize)
        Text(
          text = "${reaction.count}",
          fontSize = fontSize,
          style = TextStyle(color = Color.White)
        )
      }
    }
  }
}

@Composable
fun RoomTextMessage(
  sender: String,
  textMsg: Message.Text,
  onClick: () -> Unit,
  onClickHold: () -> Unit,
  interactionSource: MutableInteractionSource,
) {
  if (textMsg.inReplyTo != null) {
    when (val innerItem = textMsg.inReplyTo.event) {
      is RepliedToEventDetails.Ready -> {
        when (val msg = innerItem.message) {
          is Message.Text -> {
            if (msg.document != null) {
              ReplyItemMessageNode(
                msg.document,
                avatarData = innerItem.senderProfile.avatarData(sender)
              )
            } else {
              ReplyItem(
                AnnotatedString(msg.body),
                avatarData = innerItem.senderProfile.avatarData(sender)
              )
            }
          }
          else -> {}
        }
      }

      is RepliedToEventDetails.Unavailable -> {}
    }
  }
  if (textMsg.document != null) {
    textMsg.document.Display(
      modifier = Modifier,
      textStyle = null,
      onClickedEvent = parsedNodeClickHandlerLogger,
      onLongClick = onClickHold
    )
  } else {
    val annotatedString = remember {
      buildAnnotatedString {
        append(textMsg.body)
        val textSpan = SpannableString(textMsg.body)
        LinkifyCompat.addLinks(textSpan, Linkify.WEB_URLS or Linkify.PHONE_NUMBERS)
        for (span in textSpan.getSpans(0, textSpan.length, URLSpan::class.java)) {
          val begin = textSpan.getSpanStart(span)
          val end = textSpan.getSpanEnd(span)
          addStyle(start = begin, end = end, style = SpanStyle(color = LinkColor))
          addStringAnnotation(
            tag = "URL",
            annotation = span.url,
            start = begin,
            end = end
          )
        }
      }
    }

    val urlHandler = LocalUriHandler.current
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    val pointerInput: suspend PointerInputScope.() -> Unit = {
      detectTapGestures(
        onPress = { offset: Offset ->
          val pressInteraction = PressInteraction.Press(offset)
          interactionSource.emit(pressInteraction)
          val isReleased = tryAwaitRelease()
          if (isReleased) {
            interactionSource.emit(PressInteraction.Release(pressInteraction))
          } else {
            interactionSource.emit(PressInteraction.Cancel(pressInteraction))
          }
        },
        onTap = { offset ->
          layoutResult.value?.let {
            val position = it.getOffsetForPosition(offset)
            annotatedString
              .getStringAnnotations(position, position)
              .firstOrNull()
              ?.let { span ->
                urlHandler.openUri(span.item)
              }
          } ?: run {
            onClick()
          }
        },
        onLongPress = {
          onClickHold()
        })
    }

    Text(
      modifier = Modifier
        .pointerInput(onClick, block = pointerInput),
      text = annotatedString,
      onTextLayout = { layoutResult.value = it }
    )
  }
  if(textMsg.isEdited) {
    SubtleRoomNotification(text = "(edited)")
  }
}

@Composable
fun RoomMembership(msg: Message.RoomMembership) {
  SubtleRoomNotification(text = "${msg.userId} ${msg.change?.name?.lowercase(Locale.getDefault())}")
}

@Composable
fun SubtleRoomNotification(text: String) {
  Text(text = text, fontSize = 12.sp, fontStyle = FontStyle.Italic, color = Color.LightGray)
}

@Preview
@Composable
fun VirtualRoomItem() {

}