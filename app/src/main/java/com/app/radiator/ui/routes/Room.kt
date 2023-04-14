package com.app.radiator.ui.routes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.radiator.matrix.timeline.TimelineItemVariant
import com.app.radiator.matrix.timeline.VirtualTimelineItem
import com.app.radiator.ui.components.AvatarData
import com.app.radiator.ui.components.DayDivider
import com.app.radiator.ui.components.RoomMessageItem
import kotlinx.collections.immutable.ImmutableList
import com.app.radiator.matrix.timeline.ProfileDetails
import java.text.DateFormat

fun ProfileDetails.avatarData(): AvatarData? {
    when (this) {
        is ProfileDetails.Ready -> return AvatarData(
            this.displayName!!,
            this.displayName,
            this.avatarUrl
        )
        else -> return null
        // ProfileTimelineDetails.Unavailable -> return
        // ProfileTimelineDetails.Pending -> TODO()
    }
}

@Composable
fun RoomRoute(
    messages: List<TimelineItemVariant>,
    requestMore: () -> Unit = { println("request more not implemented") },
) {
    val lazyListState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Bottom,
        reverseLayout = false
    ) {
        itemsIndexed(
            items = messages,
            contentType = { _, timelineItem -> timelineItem.contentType() },
            key = { _, timelineItem -> timelineItem.id() },
        ) { index, timelineItem ->
            when (timelineItem) {
                is TimelineItemVariant.Event -> {
                    RoomMessageItem(
                        item = timelineItem,
                        avatarData = timelineItem.senderProfile.avatarData(),
                        shouldGroup = timelineItem.groupedByUser
                    )
                }
                is TimelineItemVariant.Virtual -> {
                    Box() {
                        Row(modifier = Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.Center,verticalAlignment = Alignment.CenterVertically) {
                            when (timelineItem.virtual) {
                                is VirtualTimelineItem.DayDivider -> {
                                    DayDivider(
                                        DateFormat.getDateInstance()
                                            .format(timelineItem.virtual.ts.toLong())
                                    )
                                }
                                VirtualTimelineItem.LoadingIndicator -> {}
                                VirtualTimelineItem.ReadMarker -> {}
                                VirtualTimelineItem.TimelineStart -> {}
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                TimelineItemVariant.Unknown -> TODO("Should never happen")
            }

            if (index == messages.lastIndex) {
                requestMore()
            }
        }
    }
}