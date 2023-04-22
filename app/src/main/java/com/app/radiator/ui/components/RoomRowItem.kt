package com.app.radiator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.app.radiator.ui.theme.*
import org.matrix.rustcomponents.sdk.SlidingSyncRoom


private val minHeight = 72.dp

val AvatarGradientStart = Color(0x0F4CA1AF)
val AvatarGradientEnd = Color(0xFFF000E5)

@Immutable
data class AvatarData(
    val id: String,
    val name: String?,
    val url: String? = null,
) {
    fun getInitial(): String {
        val firstChar = name?.firstOrNull() ?: id.getOrNull(1) ?: '?'
        return firstChar.uppercase()
    }
}

fun avatarData(slidingSyncRoom: SlidingSyncRoom): AvatarData {
    return AvatarData(id = slidingSyncRoom.roomId(), name = slidingSyncRoom.name(), url = slidingSyncRoom.avatarUrl())
}

@Immutable
data class RoomSummary(
    val id: Int,
    val roomId: String,
    val name: String = "",
    val hasUnread: Boolean = false,
    val timestamp: String? = null,
    val lastMessage: AnnotatedString,
    val avatarData: AvatarData = AvatarData(roomId, name),
    val isPlaceholder: Boolean = false,
)

@Composable
internal fun RoomSummaryRow(
    room: RoomSummary,
    modifier: Modifier = Modifier,
    onClick: (RoomSummary) -> Unit = {},
) {
    val clickModifier = if (room.isPlaceholder) {
        modifier
    } else {
        modifier.clickable(
            onClick = { onClick(room) },
            indication = rememberRipple(),
            interactionSource = remember { MutableInteractionSource() }
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .then(clickModifier)
    ) {
        DefaultRoomSummaryRow(room = room)
    }
}

@Composable
internal fun DefaultRoomSummaryRow(
    room: RoomSummary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = CenterVertically
    ) {
        Avatar(avatarData = room.avatarData)
        Column(
            modifier = Modifier
                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)
                .alignByBaseline()
                .weight(1f)
        ) {
            // Name
            Text(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                text = room.name,
                color = SystemGreyLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Last Message
            Text(
                text = room.lastMessage,
                color = Azure,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Timestamp and Unread
        Column(
            modifier = Modifier
                .alignByBaseline(),
        ) {
            Text(
                fontSize = 12.sp,
                text = room.timestamp ?: "",
                color = Azure,
            )
            Spacer(Modifier.size(4.dp))
            val unreadIndicatorColor = if (room.hasUnread) Polly else Color.Transparent
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(unreadIndicatorColor)
                    .align(Alignment.End),
            )
        }
    }
}