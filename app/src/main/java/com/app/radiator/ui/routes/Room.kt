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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
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
import androidx.navigation.compose.rememberNavController
import com.app.radiator.Routes
import com.app.radiator.matrix.timeline.TimelineItemVariant
import com.app.radiator.matrix.timeline.VirtualTimelineItem
import com.app.radiator.ui.components.AvatarData
import com.app.radiator.ui.components.DayDivider
import com.app.radiator.ui.components.RoomMessageItem
import com.app.radiator.matrix.timeline.ProfileDetails
import com.app.radiator.matrix.timeline.TimelineState
import com.app.radiator.ui.components.Avatar
import com.app.radiator.ui.components.LoadingAnimation
import com.app.radiator.ui.components.MessageComposer
import kotlinx.coroutines.launch
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

@Preview
@Composable
fun PreviewRoomTopBar() {
  val interactionSource = remember { MutableInteractionSource() }
  val navCtrl = rememberNavController()
  RoomTopBar(
    navController = navCtrl,
    avatarData = AvatarData(id = "TestRoom", "Test Room", url = null),
    interactionSource = interactionSource
  )
}

sealed class DropDownMenuItems(
  val text: String,
  val icon: ImageVector,
  private val iconAndTextColor: Color = Color.DarkGray,
) {
  @Immutable
  object Settings : DropDownMenuItems(text = "Settings", icon = Icons.Outlined.Settings)

  @Immutable
  object Search : DropDownMenuItems(text = "Search", icon = Icons.Outlined.Email)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomTopBar(
  navController: NavHostController,
  avatarData: AvatarData,
  interactionSource: MutableInteractionSource,
) {
  val contextForToast = LocalContext.current.applicationContext
  val (expanded, setExpanded) = remember {
    mutableStateOf(false)
  }
  Box(modifier = Modifier.border(width = 2.dp, color = Color.LightGray)) {
    TopAppBar(
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Start,
          modifier = Modifier.clickable {
            navController.navigate(Routes.RoomDetails.route + "/${avatarData.id}")
          }
        ) {
          Avatar(modifier = Modifier, avatarData = avatarData)
          Spacer(Modifier.width(10.dp))
          Text(text = avatarData.name!!)
        }
      },
      actions = {
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
              Toast.makeText(contextForToast, DropDownMenuItems.Settings.text, Toast.LENGTH_SHORT)
                .show()
              setExpanded(false)
              navController.navigate(Routes.RoomDetails.route + "/${avatarData.id}")
            }, enabled = true, interactionSource = interactionSource,
            text = {
              DropDownMenuItems.Settings.Text()
            }, leadingIcon = {
              DropDownMenuItems.Settings.Icon()
            }
          )

          DropdownMenuItem(
            onClick = {
              Toast.makeText(contextForToast, DropDownMenuItems.Search.text, Toast.LENGTH_SHORT)
                .show()
              setExpanded(false)
            }, enabled = true, interactionSource = interactionSource,
            text = {
              DropDownMenuItems.Search.Text()
            }, leadingIcon = {
              DropDownMenuItems.Search.Icon()
            }
          )

          DropdownMenuItem(
            onClick = {
              Toast.makeText(contextForToast, DropDownMenuItems.Invite.text, Toast.LENGTH_SHORT)
                .show()
              setExpanded(false)
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

@Composable
fun RoomRoute(
  navController: NavHostController,
  timelineState: TimelineState,
) {
  val lazyListState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()
  val interactionSource = remember { MutableInteractionSource() }
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
              if (timelineItem.userCanSee) {
                RoomMessageItem(item = timelineItem,
                  avatarData = timelineItem.senderProfile.avatarData(timelineItem.sender),
                  shouldGroup = timelineItem.groupedByUser,
                  onClick = {
                    Log.d("RoomMessageItemClick", "Clicked message item")
                  })
              }
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
                        DateFormat.getDateInstance().format(timelineItem.virtual.ts.toLong())
                      )
                    }

                    VirtualTimelineItem.LoadingIndicator -> {
                      LoadingAnimation(size = 100.dp)
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
    }, topBar = {
      RoomTopBar(
        navController = navController,
        avatarData = timelineState.avatar(),
        interactionSource = interactionSource
      )
    }, floatingActionButton = {
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

// I have no idea why this is, but it seems as though, 2 is the magic number here; if we say 0, 1 or 2, this all goes to shit
// we have to compare (less-than) against 3, to "be sure" that we're at the end. Seems lazy column isn't *exact* in it's measurements
fun LazyListState.isScrolledToTheEnd() =
  (layoutInfo.totalItemsCount - (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0)) < 3