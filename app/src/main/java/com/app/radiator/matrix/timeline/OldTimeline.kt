package com.app.radiator.matrix.timeline

import android.util.Log
import com.app.radiator.ui.components.AvatarData
import com.app.radiator.ui.components.avatarData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.PaginationOptions
import org.matrix.rustcomponents.sdk.RequiredState
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomSubscription
import org.matrix.rustcomponents.sdk.SlidingSyncRoom
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.TimelineChange
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.TimelineItem
import org.matrix.rustcomponents.sdk.TimelineListener
import org.matrix.rustcomponents.sdk.genTransactionId
import org.matrix.rustcomponents.sdk.messageEventContentFromMarkdown
import org.matrix.rustcomponents.sdk.use

/**
 * Is responsible for subscribing as a listener to SlidingSyncRoom's `onUpdate`.
 * When receiving an update, it then applies those updates to the current Timeline's state,
 * which is represented by a Stream (list) of `TimelineItemVariant`s so which then the UI
 * can render.
 */
class TimelineState(
  val roomId: String,
  val slidingSyncRoom: SlidingSyncRoom,
  private val superVisorCoroutineScope: CoroutineScope,
  private val diffApplyDispatcher: CoroutineDispatcher,
) : TimelineListener {
  private val initialized = MutableStateFlow(false)
  private val isInitialized = initialized.asStateFlow()
  private val timelineItems: MutableStateFlow<ImmutableList<TimelineItemVariant>> =
    MutableStateFlow(emptyList<TimelineItemVariant>().toImmutableList())
  val threadRoots = HashSet<String>()
  private val mutex = Mutex()
  val currentStateFlow = timelineItems.asStateFlow()

  private var currentlyWatchedThread: String? = null
  private val threadItems: MutableStateFlow<ImmutableList<TimelineItemVariant>> =
    MutableStateFlow(emptyList<TimelineItemVariant>().toImmutableList())

  private lateinit var taskHandle: TaskHandle
  private val timelineStateRange =
    MutableStateFlow(CanRequestMoreState(hasMore = true, isLoading = false))
  val oldRoom = slidingSyncRoom.fullRoom()!!

  init {
    val roomSubscription =
      RoomSubscription(timelineLimit = null, requiredState = listOf(RequiredState("*", "*")))
    var senderId: String? = null
    superVisorCoroutineScope.launch {
      val result = slidingSyncRoom.subscribeAndAddTimelineListener(
        this@TimelineState, roomSubscription
      )
      val initList = result.items.map { ti ->
        val item = takeMap(ti, lastUserSeen = senderId, oldRoom)
        senderId = item.sender()
        item
      }.toList()
      timelineItems.value = initList.toImmutableList()
      taskHandle = result.taskHandle
      initialized.value = true
    }
  }

  fun subscribeTimeline(): StateFlow<ImmutableList<TimelineItemVariant>> {
    return currentStateFlow
  }

  fun timelineHasThread(eventId: String): Boolean {
    return threadRoots.contains(eventId)
  }

  fun threadItemFlow(threadId: String): StateFlow<List<TimelineItemVariant>> {
    currentlyWatchedThread = threadId
    val timeline = timelineItems.value.toImmutableList()
    var start = 0
    for(item in timeline) {
      if(item is TimelineItemVariant.Event && item.eventId == currentlyWatchedThread) {
        break
      }
      start++
    }

    val thread = timeline.subList(start, timeline.size).filter {
      when (it) {
        is TimelineItemVariant.Event -> it.threadDetails?.threadId == currentlyWatchedThread
        else -> true
      }
    }.toImmutableList()
    threadItems.value = thread
    return this.threadItems.asStateFlow()
  }

  fun avatar(): AvatarData = avatarData(this.slidingSyncRoom)

  fun isInit(): Flow<Boolean> {
    return isInitialized.map { it }
  }

  suspend fun withLock(block: () -> Unit) {
    TODO("Not yet implemented")
  }

  fun patchDiff(diff: TimelineDiff) {
    TODO("Not yet implemented")
  }

  override fun onUpdate(update: TimelineDiff) {
    superVisorCoroutineScope.launch {
      withContext(diffApplyDispatcher) {
        mutex.withLock {
          updateTimelineItems {
            update.use {
              patchDiff(update)
            }
          }
        }
      }
      when (val firstItem = timelineItems.value.firstOrNull()) {
        is TimelineItemVariant.Virtual -> updateCanRequestMoreState(firstItem.virtual)
        else -> updateCanRequestMoreState(null)
      }
    }
  }

  fun requestMore() {
    this.superVisorCoroutineScope.launch(this.diffApplyDispatcher) {
      val options = PaginationOptions.UntilNumItems(5u, 5u)
      slidingSyncRoom.fullRoom()?.paginateBackwards(options)
    }
  }

  /// Updates
  fun updateCanRequestMoreState(virtualItem: VirtualTimelineItem?) {
    val currentState = this.timelineStateRange.value
    val newState = when (virtualItem) {
      VirtualTimelineItem.TimelineStart -> currentState.copy(
        hasMore = false, isLoading = false
      )

      VirtualTimelineItem.LoadingIndicator -> currentState.copy(
        hasMore = true, isLoading = true
      )

      else -> currentState.copy(hasMore = true, isLoading = false)
    }
    this.timelineStateRange.value = newState
  }

  private fun updateTimelineItems(block: MutableList<TimelineItemVariant>.() -> Unit) {
    val mutableList = timelineItems.value.toMutableList()
    block(mutableList)
    val newTimeline = mutableList.toImmutableList()
    if (currentlyWatchedThread != null) {
      var start = 0
      for(item in newTimeline) {
        if(item is TimelineItemVariant.Event && item.eventId == currentlyWatchedThread) {
          break
        }
        start++
      }
      val thread = newTimeline.subList(start, mutableList.size).filter {
        when (it) {
          is TimelineItemVariant.Event -> it.threadDetails?.threadId == currentlyWatchedThread
          else -> true
        }
      }.toImmutableList()
      threadItems.value = thread
    }
    timelineItems.value = newTimeline
  }

  private fun takeMap(
    item: TimelineItem,
    lastUserSeen: String?,
    room: Room,
  ): TimelineItemVariant = item.use {

    it.asEvent()?.use { evt ->
      evt.marshal(lastUserSeen, room = room)
    }?.let { i ->
      updateThreadRoots(i)
      return i
    }

    it.asVirtual()?.let { asVirtual ->
      val id = (0..Int.MAX_VALUE).random()
      return when (asVirtual) {
        is org.matrix.rustcomponents.sdk.VirtualTimelineItem.DayDivider -> TimelineItemVariant.Virtual(
          id, VirtualTimelineItem.DayDivider(asVirtual.ts)
        )

        org.matrix.rustcomponents.sdk.VirtualTimelineItem.LoadingIndicator -> TimelineItemVariant.Virtual(
          id, VirtualTimelineItem.LoadingIndicator
        )

        org.matrix.rustcomponents.sdk.VirtualTimelineItem.ReadMarker -> TimelineItemVariant.Virtual(
          id, VirtualTimelineItem.ReadMarker
        )

        org.matrix.rustcomponents.sdk.VirtualTimelineItem.TimelineStart -> TimelineItemVariant.Virtual(
          id, VirtualTimelineItem.TimelineStart
        )
      }
    }
    return TimelineItemVariant.Unknown
  }

  private fun updateThreadRoots(item: TimelineItemVariant.Event) {
    item.threadDetails?.let { threadRoots.add(item.threadDetails.threadId) }
  }

  private fun MutableList<TimelineItemVariant>.patchDiff(diff: TimelineDiff) {
    when (diff.change()) {
      TimelineChange.APPEND -> {
        val append = diff.append()
          ?.map { takeMap(it, lastUserSeen = this.lastOrNull()?.sender(), room = oldRoom) }
          .orEmpty()
        addAll(append)
      }

      TimelineChange.CLEAR -> clear()
      TimelineChange.INSERT -> diff.insert()?.use {
        add(it.index.toInt(), takeMap(it.item, lastUserSeen = null, room = oldRoom))
      }

      TimelineChange.SET -> diff.set()?.use {
        val idx = it.index.toInt()
        if (idx > this.size) {
          for (fill in this.size..idx) {
            this.add(fill, TimelineItemVariant.Fill)
          }
        }
        set(idx, takeMap(it.item, lastUserSeen = this.getOrNull(idx - 1)?.sender(), room = oldRoom))
        val shouldNotGroup: (String?, String?) -> Boolean = { a, b ->
          a != b && (a != null && b != null)
        }
        if (shouldNotGroup(getOrNull(idx + 1)?.sender(), getOrNull(idx + 1)?.sender())) {
          if ((this[idx + 1] as TimelineItemVariant.Event).groupedByUser) {
            this[idx + 1] = (this[idx + 1] as TimelineItemVariant.Event).copy(groupedByUser = false)
          }
        }
      }

      TimelineChange.REMOVE -> diff.remove()?.let {
        removeAt(it.toInt())
      }

      TimelineChange.PUSH_BACK -> diff.pushBack()?.use {
        add(takeMap(it, lastUserSeen = lastOrNull()?.sender(), room = oldRoom))
      }

      TimelineChange.PUSH_FRONT -> diff.pushFront()?.use {
        val item = takeMap(it, lastUserSeen = null, room = oldRoom)
        firstOrNull()?.let { first ->
          if (first is TimelineItemVariant.Event && first.senderId == item.sender()) {
            this[0] = first.copy(groupedByUser = true)
          }
        }
        add(0, item)
      }

      TimelineChange.POP_BACK -> removeLastOrNull()
      TimelineChange.POP_FRONT -> {
        removeFirstOrNull()
      }

      TimelineChange.RESET -> diff.reset()?.let { items ->
        clear()
        var senderId: String? = null
        addAll(items.map {
          val item = takeMap(it, lastUserSeen = senderId, room = oldRoom)
          senderId = item.sender()
          item
        })
      }
    }
  }

  fun dispose() {
    Log.d("Timeline", "Disposing of timeline")
    taskHandle.use { it.cancel() }
    superVisorCoroutineScope.cancel()
  }

  fun sendMessage(id: String, msg: String) {
    slidingSyncRoom.fullRoom()?.use {
      val msgAsMarkdown = messageEventContentFromMarkdown(msg)
      it.send(msgAsMarkdown, id)
    }
  }

  fun sendReply(id: String, reply: TimelineAction.Reply) {
    slidingSyncRoom.fullRoom()?.use {
      it.sendReply(msg = reply.msg, inReplyToEventId = reply.eventId, txnId = id)
    }
  }

  fun sendReaction(reaction: TimelineAction.React) {
    slidingSyncRoom.fullRoom()?.use {
      it.sendReaction(eventId = reaction.eventId, key = reaction.reaction)
    }
  }

  fun editMessage(id: String, edit: TimelineAction.Edit) {
    slidingSyncRoom.fullRoom()?.use {
      it.edit(newMsg = edit.newContent, originalEventId = edit.eventId, txnId = id)
    }
  }

  fun timelineSend(action: TimelineAction) {
    superVisorCoroutineScope.launch {
      withContext(Dispatchers.IO) {
        when (action) {
          is TimelineAction.Message -> sendMessage(id = genTransactionId(), msg = action.msg)
          is TimelineAction.Reply -> sendReply(id = genTransactionId(), action)
          is TimelineAction.React -> sendReaction(action)
          is TimelineAction.Edit -> editMessage(id = genTransactionId(), edit = action)
          is TimelineAction.ThreadReply -> TODO()
        }
      }
    }
  }
}
