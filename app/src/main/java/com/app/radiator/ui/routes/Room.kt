package com.app.radiator.ui.routes

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.radiator.matrix.timeline.TimelineItemVariant
import com.app.radiator.matrix.timeline.VirtualTimelineItem
import com.app.radiator.ui.components.AvatarData
import com.app.radiator.ui.components.DayDivider
import com.app.radiator.ui.components.RoomMessageItem
import com.app.radiator.matrix.timeline.ProfileDetails
import com.app.radiator.matrix.timeline.TimelineState
import com.app.radiator.ui.components.LoadingAnimation
import com.app.radiator.ui.components.MessageComposer
import kotlinx.coroutines.launch
import java.text.DateFormat

fun ProfileDetails.avatarData(userId: String): AvatarData? {
  when (this) {
    is ProfileDetails.Ready -> return AvatarData(
      id = userId, name = this.displayName, url = this.avatarUrl
    )

    ProfileDetails.Pending -> TODO("Pending Profile details not handled yet")
    else -> return null
  }
}

@Composable
fun RoomRoute(
  timelineState: TimelineState,
) {
  val lazyListState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()
  val messages = timelineState.currentStateFlow.collectAsState(emptyList())

  fun reachedTopOfList(index: Int): Boolean = index == 0

  Box(modifier = Modifier.background(color = Color.White)) {
    Scaffold(content = { padding ->
      LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        state = lazyListState,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Bottom,
        reverseLayout = false
      ) {
        itemsIndexed(
          items = messages.value,
          contentType = { _, timelineItem -> timelineItem.contentType() },
          key = { _, timelineItem -> timelineItem.id() },
        ) { index, timelineItem ->
          when (timelineItem) {
            is TimelineItemVariant.Event -> {
              RoomMessageItem(
                item = timelineItem,
                avatarData = timelineItem.senderProfile.avatarData(timelineItem.sender),
                shouldGroup = timelineItem.groupedByUser,
              )
            }

            is TimelineItemVariant.Virtual -> {
              Spacer(modifier = Modifier.height(5.dp))
              Box() {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.Center,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  when (timelineItem.virtual) {
                    is VirtualTimelineItem.DayDivider -> {
                      DayDivider(
                        DateFormat.getDateInstance()
                          .format(timelineItem.virtual.ts.toLong())
                      )
                    }

                    VirtualTimelineItem.LoadingIndicator -> {
                      LoadingAnimation(size = 100.dp)
                      Log.i("RoomRoute", "Loading indicator seen")
                    }

                    VirtualTimelineItem.ReadMarker -> {}
                    VirtualTimelineItem.TimelineStart -> {}
                  }
                }
              }
              Spacer(modifier = Modifier.height(5.dp))
            }

            TimelineItemVariant.Unknown -> TODO("Should never happen")
          }

          if (reachedTopOfList(index)) {
            timelineState.requestMore()
          }
        }
      }
    }, bottomBar = {
      MessageComposer(sendMessageOp = { timelineState.sendMessage(it) })
    },
      floatingActionButton = {
        // TODO: _maybe_ have to toggle off when at the bottom, but this comes with an additoinal cost
        //  it means this composable will get a recomposition triggered _every time the user scrolls_
        //  - I'm not willing to pay that cost, right now
        FloatingActionButton(
          onClick = {
            coroutineScope.launch {
              lazyListState.animateScrollToItem(messages.value.size)
            }
          },
          shape = CircleShape,
          modifier = Modifier
              .align(Alignment.BottomCenter)
              .size(40.dp),
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
          contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
          Icon(Icons.Default.KeyboardArrowDown, "")
        }
      },
      floatingActionButtonPosition = FabPosition.End
    )

    // Auto-scroll when new timeline items appear
    LaunchedEffect(messages.value) {
      coroutineScope.launch {
        if (lazyListState.isScrolledToTheEnd() && !lazyListState.isScrollInProgress) {
          lazyListState.animateScrollToItem(messages.value.size)
        }
      }
    }
  }
}

// I have no idea why this is, but it seems as though, 2 is the magic number here; if we say 0, 1 or 2, this all goes to shit
// we have to compare (less-than) against 3, to "be sure" that we're at the end. Seems lazy column isn't *exact* in it's measurements
fun LazyListState.isScrolledToTheEnd() =
  (layoutInfo.totalItemsCount - (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0)) < 3