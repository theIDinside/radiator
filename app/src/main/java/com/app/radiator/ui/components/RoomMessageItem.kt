package com.app.radiator.ui.components

import android.annotation.SuppressLint
import android.icu.text.SimpleDateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.radiator.matrix.store.AsyncImageStorage
import com.app.radiator.matrix.store.MxcURI
import com.app.radiator.matrix.timeline.*
import com.app.radiator.matrix.timeline.displayName
import java.util.*

val preview = TimelineItemVariant.Event(
    id = Math.random().toInt(),
    uniqueIdentifier = "uniqueId",
    eventId = "eventId",
    isEditable = false,
    isLocal = false,
    isOwn = false,
    isRemote = true,
    localSendState = EventSendState.Sent("eventId"),
    reactions = listOf(),
    sender = "@simonfarre:matrix.org",
    senderProfile = ProfileDetails.Ready(
        null,
        displayName = "simonfarre",
        displayNameAmbiguous = false
    ),
    timestamp = 0u,
    message = Message.Text(
        body = "Text message",
        inReplyTo = null,
        isEdited = false,
        formatted = null
    ),
    groupedByUser = false
)

val RoomViewLeftOffset = 7.dp

@Preview
@Composable
fun DayDivider(date: String = "April 10, 2023") {
    //Row(horizontalArrangement = Arrangement.Center) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
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
    //}

}

@SuppressLint("SimpleDateFormat")
@OptIn(ExperimentalFoundationApi::class)
@Preview
@Composable
fun RoomMessageItem(
    item: TimelineItemVariant.Event = preview,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onClickHold: () -> Unit = {},
    content: @Composable () -> Unit = {},
    shouldGroup: Boolean = false,
    avatarData: AvatarData? = AvatarData(
        preview.sender,
        preview.senderProfile.displayName(),
        url = null
    ),
    isMe: Boolean = false,
) {
    val coroutineScope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(color = Color.White),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column() {
                // TODO: user avatar + user name + time stamp display row
                if (!shouldGroup) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val innerModifier =
                            Modifier
                                .padding(horizontal = 5.dp, vertical = 5.dp)
                                .offset(y = 2.dp)
                        if (avatarData != null) {
                            Column(modifier = innerModifier) {
                                Avatar(avatarData = avatarData, size = 25.dp)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val userNameDisplayText = buildAnnotatedString {
                                withStyle(SpanStyle(color = Color(255, 165, 0))) {
                                    append(item.senderProfile.displayName() ?: item.sender)
                                }
                            }
                            Text(userNameDisplayText)
                        }
                    }
                }
                // TODO: row of message content
                Row(
                    modifier = Modifier
                        .offset(RoomViewLeftOffset)
                        .clickable { },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val messageTimeStampText =
                        SimpleDateFormat("HH:mm").format(Date(item.timestamp.toLong())).toString()
                    val messageTimeStamp = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.Gray)) {
                            append(messageTimeStampText)
                        }
                    }
                    Text(text = messageTimeStamp, fontSize = 8.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    when (val contentTypeItem = item.message) {
                        is Message.Text -> {
                            Text(contentTypeItem.body)
                        }

                        is Message.Image -> {
                            val uri = MxcURI.Download(contentTypeItem.source)
                            AsyncImageStorage.AsyncImageWithLoadingAnimation(
                                modifier = Modifier
                                    .border(width = 2.dp, color = Color.Black),
                                coroutineScope = coroutineScope,
                                url = uri
                            )
                        }

                        else -> {
                            Text(contentTypeItem.toString())
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun VirtualRoomItem() {

}