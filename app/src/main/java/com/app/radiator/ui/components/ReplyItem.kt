package com.app.radiator.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.radiator.matrix.timeline.displayName

val ReplyBoxColor = Color(248, 248, 253)
val ReplyBoxBorderColor = Color(238, 238, 243)

val InReplyToText = AnnotatedString(
  "in reply to: ", spanStyles = listOf(
    AnnotatedString.Range(
      SpanStyle(
        color = Color.Blue, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline
      ), 0, 11
    ), AnnotatedString.Range(
      SpanStyle(
        color = Color.Blue,
        fontWeight = FontWeight.Bold,
      ), 12, 13
    )
  )
)

@Composable
fun ReplyBox(content: @Composable () -> Unit) {
  Box(modifier = Modifier
    .padding(start = 11.dp)
    .fillMaxWidth()
    .clickable {
      Log.i("ReplyItemClicked", "ReplyItemClicked")
    }
    .background(
      color = ReplyBoxColor, shape = RoundedCornerShape(4.dp)
    )
    .border(width = 2.dp, color = ReplyBoxBorderColor, RoundedCornerShape(5.dp))
    .clip(RoundedCornerShape(3.dp))) {
    content()
  }
}

@Composable
fun InReplyToCard(avatarData: AvatarData?, onClickUserRepliedTo: () -> Unit) {
  Row(modifier = Modifier.padding(start = 5.dp, top = 3.dp)) {
    Text(text = InReplyToText)
    Card(
      modifier = Modifier.clickable { onClickUserRepliedTo() },
      shape = RoundedCornerShape(9.dp),
      elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
      Row(
        modifier = Modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Spacer(Modifier.width(2.dp))
        if (avatarData != null) Avatar(avatarData = avatarData, size = 13.dp)
        Spacer(Modifier.width(5.dp))
        Text(text = avatarData?.name ?: "unavailable")
        Spacer(Modifier.width(5.dp))
      }
    }
  }

}

@Preview
@Composable
fun ReplyItem(
  textThatWasRepliedTo: AnnotatedString = AnnotatedString("Foo\nBar"),
  avatarData: AvatarData? = AvatarData(
    id = preview.sender, name = preview.senderProfile.displayName(), url = null
  ),
  onClickUserRepliedTo: () -> Unit = {},
) {
  ReplyBox()
  {
    Column(
      modifier = Modifier.replyModifier()
    ) {
      InReplyToCard(avatarData=avatarData, onClickUserRepliedTo = onClickUserRepliedTo)
      Text(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 5.dp), text = textThatWasRepliedTo
      )
    }
  }
}

fun Modifier.replyModifier(): Modifier = this
  .padding(start = 5.dp, top = 5.dp, bottom = 5.dp)

@Composable
fun ReplyItemMessageNode(
  rootMessageNode: ParsedMessageNode,
  avatarData: AvatarData?,
  onClickUserRepliedTo: () -> Unit = {},
) {
  ReplyBox {
    Column(
      modifier = Modifier
        .replyModifier()
    ) {
      InReplyToCard(avatarData=avatarData, onClickUserRepliedTo = onClickUserRepliedTo)
      Row(modifier = Modifier.padding(start = 10.dp, top = 10.dp)) {
        rootMessageNode.Display(
          modifier = Modifier, textStyle = null, onClickedEvent = parsedNodeClickHandlerLogger
        )
      }
    }
  }
}