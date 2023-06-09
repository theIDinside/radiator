package com.app.radiator.ui.routes

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.navigation.NavHostController
import com.app.radiator.Routes
import com.app.radiator.matrix.timeline.IEvent
import com.app.radiator.matrix.timeline.ITimeline
import com.app.radiator.matrix.timeline.Message
import com.app.radiator.matrix.timeline.VirtualTimelineItem
import com.app.radiator.ui.components.AvatarData
import com.app.radiator.ui.components.DayDivider
import com.app.radiator.ui.components.RoomMessageItem
import com.app.radiator.matrix.timeline.ProfileDetails
import com.app.radiator.ui.components.Avatar
import com.app.radiator.ui.components.LoadingAnimation
import com.app.radiator.ui.components.MessageDrawerContent
import com.app.radiator.ui.components.MessageComposer
import com.app.radiator.ui.components.MessageComposerState
import com.app.radiator.ui.components.ComposerState
import com.app.radiator.ui.components.MessageDrawerAction
import com.app.radiator.ui.components.MessageDrawerContentInterface
import com.app.radiator.ui.components.SearchBar
import com.app.radiator.ui.components.general.CenteredRow
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat

fun ProfileDetails.avatarData(userId: String): AvatarData? {
  return when (this) {
    is ProfileDetails.Ready -> AvatarData(
      id = userId, name = this.displayName, url = this.avatarUrl
    )

    ProfileDetails.Pending -> TODO("Pending Profile details not handled yet")
    else -> null
  }
}

sealed class DropDownMenuItems(
  val text: String,
  val icon: ImageVector,
  private val iconAndTextColor: Color = Color.DarkGray,
) {
  @Immutable
  object Settings : DropDownMenuItems(text = "Settings", icon = Icons.Outlined.Settings)

  @Immutable
  object Invite : DropDownMenuItems(text = "Invite", icon = Icons.Outlined.Person)

  @Composable
  fun Text() {
    Text(
      text = text,
      fontWeight = FontWeight.Medium,
      fontSize = 16.sp,
      color = iconAndTextColor
    )
  }

  @Composable
  fun Icon() {
    Icon(
      imageVector = icon,
      contentDescription = text,
      tint = iconAndTextColor
    )
  }
}

@Preview
@Composable
fun PreviewSearchBar() {
  SearchBar(onSearch = {}, onDone = {})
}

@Preview
@Composable
fun PreviewRoomTopBar() {
  val contextForToast = LocalContext.current.applicationContext
  RoomTopBar(
    avatarData = AvatarData(id = "TestRoom", "Test Room", url = null),
    onSearch = {
      Toast.makeText(contextForToast, "Should search for $it", Toast.LENGTH_LONG).show()
    },
    onRoomDetailsClick = {
      Toast.makeText(contextForToast, "Should open room details page", Toast.LENGTH_LONG).show()
    },
    onInviteClick = {
      Toast.makeText(contextForToast, "Should open Invite page", Toast.LENGTH_LONG).show()
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomTopBar(
  avatarData: AvatarData,
  onSearch: (String) -> Unit,
  onRoomDetailsClick: () -> Unit,
  onInviteClick: () -> Unit,
) {

  val interactionSource = remember { MutableInteractionSource() }

  val (expanded, setExpanded) = remember {
    mutableStateOf(false)
  }
  val (searchingRoom, setSearchRoom) = remember {
    mutableStateOf(false)
  }
  fun cancelSearch() {
    setSearchRoom(false)
  }

  @Composable
  fun showSearch() {
    IconButton(onClick = {
      setSearchRoom(true)
    }) {
      Icon(imageVector = Icons.Default.Search, contentDescription = "Search in room for...")
    }
  }

  Box(modifier = Modifier.border(width = 2.dp, color = Color.LightGray)) {
    TopAppBar(
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Start,
          modifier = Modifier.clickable { onRoomDetailsClick() }
        ) {
          Avatar(modifier = Modifier, avatarData = avatarData)
          Spacer(Modifier.width(10.dp))
          Text(text = avatarData.name!!)
          Spacer(Modifier.weight(1.0f))
        }
      },
      actions = {
        if (!searchingRoom) {
          showSearch()
        } else {
          SearchBar(onSearch = onSearch, onDone = { cancelSearch() })
        }
        IconButton(onClick = {
          setExpanded(true)
        }) {
          Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Open Options"
          )
        }
        DropdownMenu(
          modifier = Modifier.width(width = 150.dp),
          expanded = expanded,
          onDismissRequest = {
            setExpanded(false)
          },
          // adjust the position
          offset = DpOffset(x = (-102).dp, y = (-64).dp),
          properties = PopupProperties()
        ) {

          DropdownMenuItem(
            onClick = {
              setExpanded(false)
              onRoomDetailsClick()
            }, enabled = true, interactionSource = interactionSource,
            text = {
              DropDownMenuItems.Settings.Text()
            }, leadingIcon = {
              DropDownMenuItems.Settings.Icon()
            }
          )

          DropdownMenuItem(
            onClick = {
              setExpanded(false)
              onInviteClick()
            }, enabled = true, interactionSource = interactionSource,
            text = {
              DropDownMenuItems.Invite.Text()
            }, leadingIcon = {
              DropDownMenuItems.Invite.Icon()
            }
          )
        }
      }
    )
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun RoomRoute(
  navController: NavHostController,
  timelineState: ITimeline,
  messageComposer: MessageComposerState,
) {

  val lazyListState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()
  val messages = timelineState.subscribeTimeline().collectAsState(emptyList())
  val contextForToast = LocalContext.current.applicationContext
  val lastSearchHitIndex = remember { mutableStateOf(0) }

  val itemActionsBottomSheetState = rememberModalBottomSheetState(
    initialValue = ModalBottomSheetValue.Hidden,
  )

  val clickedItem: MutableState<IEvent.Event?> = remember {
    mutableStateOf(null)
  }

  LaunchedEffect(itemActionsBottomSheetState.isVisible) {
    if (!itemActionsBottomSheetState.isVisible) {
      clickedItem.value = null
    }
  }

  ModalBottomSheetLayout(
    modifier = Modifier,
    sheetState = itemActionsBottomSheetState,
    sheetContent = {
      MessageDrawerContent(TimelineMessageDrawer) { event ->
        coroutineScope.launch {
          itemActionsBottomSheetState.hide()
          val item = clickedItem.value!!.copy(reactions = listOf())
          when (event) {
            TimelineMessageDrawerActionType.Reply -> messageComposer.setState(
              ComposerState.Reply(
                item
              )
            )

            TimelineMessageDrawerActionType.ThreadReply -> messageComposer.setState(
              ComposerState.ThreadReply(
                item
              )
            )

            TimelineMessageDrawerActionType.React -> messageComposer.setState(
              ComposerState.React(
                item
              )
            )

            TimelineMessageDrawerActionType.Edit -> messageComposer.setState(ComposerState.Edit(item))
            TimelineMessageDrawerActionType.Delete, TimelineMessageDrawerActionType.Share, TimelineMessageDrawerActionType.Quote -> {}
          }
        }
      }
    }
  ) {
    Box(modifier = Modifier.background(color = Color.White)) {
      Scaffold(content = { padding ->
        MessageList(
          navController,
          timelineState = timelineState,
          padding = padding,
          lazyListState = lazyListState,
          messages = messages,
          itemActionsBottomSheetState = itemActionsBottomSheetState,
          clickedItemPublisher = clickedItem,
          requestMore = { timelineState.requestMore() }
        )
      }, bottomBar = {
        MessageComposer(messageComposer)
      }, topBar = {
        RoomTopBar(
          avatarData = timelineState.avatar(),
          onSearch = { searchString ->
            coroutineScope.launch {
              searchTimeline(searchString, lazyListState, lastSearchHitIndex, messages)
            }
          },
          onRoomDetailsClick = {
            navController.navigate(Routes.RoomDetails.route + "/${timelineState.roomId()}")
            Toast.makeText(contextForToast, "Should open room details page", Toast.LENGTH_LONG)
              .show()
          },
          onInviteClick = {
            Toast.makeText(contextForToast, "Should open Invite page", Toast.LENGTH_LONG).show()
          }
        )
      }, floatingActionButton = {
        // TODO: _maybe_ have to toggle off when at the bottom, but this comes with an additoinal cost
        //  it means this composable will get a recomposition triggered _every time the user scrolls_
        //  - I'm not willing to pay that cost, right now
        FloatingActionButton(
          onClick = {
            coroutineScope.launch {
              if (messages.value.size - lazyListState.firstVisibleItemIndex > 25) {
                lazyListState.scrollToItem(messages.value.size)
              } else {
                lazyListState.animateScrollToItem(messages.value.size)
              }
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
      }, floatingActionButtonPosition = FabPosition.End
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
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MessageList(
  navController: NavHostController,
  timelineState: ITimeline,
  padding: PaddingValues,
  lazyListState: LazyListState,
  messages: State<List<IEvent>>,
  itemActionsBottomSheetState: ModalBottomSheetState,
  clickedItemPublisher: MutableState<IEvent.Event?>,
  requestMore: () -> Unit,
) {
  fun reachedTopOfList(index: Int): Boolean {
    return index == 0
  }

  val paginationState = remember { timelineState.canUpdateStateProducer().value }

  val coroutineScope = rememberCoroutineScope()
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
      key = { _, timelineItem -> timelineItem.lazyListId() },
    ) { index, timelineItem ->
      when (timelineItem) {
        is IEvent.Event -> {
          if (timelineItem.userCanSee && timelineItem.threadDetails == null) {
            val lastMsg = remember { timelineState.getLatestSeenItemOfThread(timelineItem.eventId) }
            Column {
              RoomMessageItem(
                item = timelineItem,
                ifThreadLastMessage = lastMsg,
                onClick = {
                  if (timelineState.timelineHasThread(timelineItem.eventId)) {
                    navController.navigate(Routes.Thread.route + "/${timelineItem.eventId}/${timelineState.roomId()}")
                  } else {
                    Log.d("RoomMessageItemClick", "Clicked message item $timelineItem.eventId")
                  }
                },
                onClickHold = {
                  coroutineScope.launch {
                    itemActionsBottomSheetState.show()
                    clickedItemPublisher.value = it
                  }
                },
              )
            }
          }
        }

        is IEvent.Virtual -> VirtualItem(timelineItem = timelineItem)
        IEvent.Unknown -> {}
        is IEvent.Fill -> {}
      }

      if (reachedTopOfList(index) && paginationState.hasMore) {
        requestMore()
      }
    }
  }
}

suspend fun searchTimeline(
  searchString: String,
  lazyListState: LazyListState,
  lastSearchHitIndex: MutableState<Int>,
  messages: State<List<IEvent>>,
) {
  withContext(Dispatchers.IO) {
    var idx = lastSearchHitIndex.value
    val lastFound = lastSearchHitIndex.value
    var found = false
    try {
      for (item in messages.value.subList(lastFound, messages.value.size)) {
        if (item is IEvent.Event && item.message is Message.Text) {
          if (idx != lastFound && item.message.body.contains(searchString)) {
            found = true
            break
          }
        }
        idx++
      }
      if (!found && lastSearchHitIndex.value != 0) {
        lastSearchHitIndex.value = 0
      } else {
        lastSearchHitIndex.value = idx
        // N.B. calling `lazyListState.scrollToItem` from anywhere but main throws exception
        withContext(Dispatchers.Main) {
          lazyListState.scrollToItem(idx, -20)
        }
      }
    } catch (ex: Exception) {
      Log.d("Search", "Search threw exception $ex")
    }
  }
}

@Composable
fun VirtualItem(timelineItem: IEvent.Virtual) {
  Spacer(modifier = Modifier.height(5.dp))
  CenteredRow(
    modifier = Modifier.fillMaxWidth(),
  ) {
    when (timelineItem.virtual) {
      is VirtualTimelineItem.DayDivider -> DayDivider(
        DateFormat.getDateInstance().format(timelineItem.virtual.ts.toLong())
      )

      VirtualTimelineItem.LoadingIndicator -> LoadingAnimation(size = 100.dp)
      VirtualTimelineItem.ReadMarker -> {}
      VirtualTimelineItem.TimelineStart -> {}
    }
  }
  Spacer(modifier = Modifier.height(5.dp))
}


// I have no idea why this is, but it seems as though, 2 is the magic number here; if we say 0, 1 or 2, this all goes to shit
// we have to compare (less-than) against 3, to "be sure" that we're at the end. Seems lazy column isn't *exact* in it's measurements
fun LazyListState.isScrolledToTheEnd() =
  (layoutInfo.totalItemsCount - (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0)) < 3

@Immutable
enum class TimelineMessageDrawerActionType {
  Reply, ThreadReply, React, Edit, Delete, Share, Quote
}

object TimelineMessageDrawer : MessageDrawerContentInterface<TimelineMessageDrawerActionType> {
  private val actions = persistentListOf(
    MessageDrawerAction(
      "Reply",
      Icons.Default.Send,
      desc = "Reply to message",
      action = TimelineMessageDrawerActionType.Reply
    ),
    MessageDrawerAction(
      "Reply in thread",
      Icons.Default.Refresh,
      desc = "Reply in thread",
      action = TimelineMessageDrawerActionType.ThreadReply
    ),
    MessageDrawerAction(
      "Reaction",
      Icons.Default.ThumbUp,
      desc = "React to message",
      action = TimelineMessageDrawerActionType.React
    ),
    MessageDrawerAction(
      "Edit",
      Icons.Default.Edit,
      desc = "Edit message",
      action = TimelineMessageDrawerActionType.Edit
    ),
    MessageDrawerAction(
      "Delete",
      Icons.Default.Delete,
      desc = "Delete message",
      action = TimelineMessageDrawerActionType.Delete
    ),
    MessageDrawerAction(
      "Share",
      Icons.Default.Share,
      desc = "Share",
      action = TimelineMessageDrawerActionType.Share
    ),
    MessageDrawerAction(
      "Quote",
      Icons.Default.Favorite,
      desc = "Quote",
      action = TimelineMessageDrawerActionType.Quote
    ),
  )

  override fun actions(): List<MessageDrawerAction<TimelineMessageDrawerActionType>> = actions
}