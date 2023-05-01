package com.app.radiator.ui.routes

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.app.radiator.matrix.timeline.TimelineItemVariant
import com.app.radiator.matrix.timeline.TimelineState
import com.app.radiator.ui.components.Avatar
import com.app.radiator.ui.components.AvatarData
import com.app.radiator.ui.components.ComposerState
import com.app.radiator.ui.components.MessageComposer
import com.app.radiator.ui.components.MessageComposerState
import com.app.radiator.ui.components.MessageDrawerAction
import com.app.radiator.ui.components.MessageDrawerContent
import com.app.radiator.ui.components.MessageDrawerContentInterface
import com.app.radiator.ui.components.RoomMessageItem
import com.app.radiator.ui.components.SearchBar
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadTopBar(
  avatarData: AvatarData,
  onSearch: (String) -> Unit,
) {

  // val interactionSource = remember { MutableInteractionSource() }
  // val (expanded, setExpanded) = remember { mutableStateOf(false) }
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
          // setExpanded(true)
        }) {
          Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Open Options"
          )
        }
      }
    )
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ThreadRoute(
  navController: NavHostController,
  timelineState: TimelineState,
  messageComposer: MessageComposerState,
  threadEventId: String,
) {
  val lazyListState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()
  val messages = timelineState.threadItemFlow(threadEventId).collectAsState()
  val lastSearchHitIndex = remember { mutableStateOf(0) }

  val itemActionsBottomSheetState = rememberModalBottomSheetState(
    initialValue = ModalBottomSheetValue.Hidden,
  )

  val clickedItem: MutableState<TimelineItemVariant.Event?> = remember {
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
      MessageDrawerContent(ThreadMessageDrawer) { event ->
        coroutineScope.launch {
          itemActionsBottomSheetState.hide()
          val item = clickedItem.value!!.copy(reactions = listOf())
          when (event) {
            ThreadMessageDrawerActionType.Reply -> messageComposer.setState(ComposerState.Reply(item))
            ThreadMessageDrawerActionType.React -> messageComposer.setState(ComposerState.React(item))
            ThreadMessageDrawerActionType.Edit -> messageComposer.setState(ComposerState.Edit(item))
            ThreadMessageDrawerActionType.Delete, ThreadMessageDrawerActionType.Share, ThreadMessageDrawerActionType.Quote -> {}
          }
        }
      }
    }
  ) {
    Box(modifier = Modifier.background(color = Color.White)) {
      Scaffold(content = { padding ->
        ThreadList(
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
        ThreadTopBar(
          avatarData = timelineState.avatar(),
          onSearch = { searchString ->
            coroutineScope.launch {
              searchTimeline(searchString, lazyListState, lastSearchHitIndex, messages)
            }
          },
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
fun ThreadList(
  navController: NavHostController,
  timelineState: TimelineState,
  padding: PaddingValues,
  lazyListState: LazyListState,
  messages: State<List<TimelineItemVariant>>,
  itemActionsBottomSheetState: ModalBottomSheetState,
  clickedItemPublisher: MutableState<TimelineItemVariant.Event?>,
  requestMore: () -> Unit,
) {
  fun reachedTopOfList(index: Int): Boolean {
    return index == 0
  }

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
      key = { _, timelineItem -> timelineItem.id() },
    ) { index, timelineItem ->
      when (timelineItem) {
        is TimelineItemVariant.Event -> {
          RoomMessageItem(
            item = timelineItem,
            onClick = {
              Log.d("RoomMessageItemClick", "Clicked message item $timelineItem.eventId")
            },
            onClickHold = {
              coroutineScope.launch {
                itemActionsBottomSheetState.show()
                clickedItemPublisher.value = it
              }
            },
          )
        }

        is TimelineItemVariant.Virtual -> VirtualItem(timelineItem = timelineItem)
        TimelineItemVariant.Unknown -> {}
        is TimelineItemVariant.Fill -> {}
      }

      if (reachedTopOfList(index)) {
        requestMore()
      }
    }
  }
}

@Immutable
enum class ThreadMessageDrawerActionType {
  Reply, React, Edit, Delete, Share, Quote
}

object ThreadMessageDrawer : MessageDrawerContentInterface<ThreadMessageDrawerActionType> {
  private val actions = persistentListOf(
    MessageDrawerAction("Reply", Icons.Default.Send, desc = "Reply to message", action = ThreadMessageDrawerActionType.Reply),
    MessageDrawerAction("Reaction", Icons.Default.ThumbUp, desc = "React to message", action = ThreadMessageDrawerActionType.React),
    MessageDrawerAction("Edit", Icons.Default.Edit, desc = "Edit message", action = ThreadMessageDrawerActionType.Edit),
    MessageDrawerAction("Delete", Icons.Default.Delete, desc = "Delete message", action = ThreadMessageDrawerActionType.Delete),
    MessageDrawerAction("Share", Icons.Default.Share, desc = "Share", action = ThreadMessageDrawerActionType.Share),
    MessageDrawerAction("Quote", Icons.Default.Favorite, desc = "Quote", action = ThreadMessageDrawerActionType.Quote),
  )
  override fun actions(): List<MessageDrawerAction<ThreadMessageDrawerActionType>> = actions
}