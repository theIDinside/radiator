package com.app.radiator.matrix.timeline

import android.util.Log
import com.app.radiator.ui.components.AvatarData
import com.app.radiator.ui.components.avatarData
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
import org.matrix.rustcomponents.sdk.*

internal inline fun <T> List<T>.findFirstIndexOf(predicate: (T) -> Boolean): Int? {
  var index = 0
  for (item in this) {
    if (predicate(item))
      return index
    index++
  }
  return null
}

class TimelineStateObject(
  private val slidingSyncRoom: SlidingSyncRoom,
  private val superVisorCoroutineScope: CoroutineScope,
  private val applyDiffDispatcher: CoroutineDispatcher,
) : ITimeline {
  val roomId = slidingSyncRoom.roomId()
  private val oldRoom = slidingSyncRoom.fullRoom()!!

  // Timeline state; protected by mutex
  private val mutex = Mutex()
  private val events = arrayListOf<IEvent>()
  private val eventsMetadata = HashMap<String, EventMetadata>()
  // ----

  private val timelineProducer = MutableStateFlow(events.toImmutableList())
  private val timelineSubscriber = timelineProducer.asStateFlow()
  override fun roomId(): String {
    return roomId
  }

  override fun subscribeTimeline() = timelineSubscriber

  private var llId = 0
  private val makeLazyListId: () -> Int = {
    // val id = (0..Int.MAX_VALUE).random()
    llId += 1
    llId
  }

  // Thread related logic
  private val doUpdateOfThreadRoots: (IEvent.Event) -> Unit = { item ->
    item.threadDetails?.let {
      seenThreadRoots[item.threadDetails.threadId]?.let {
        if(it.timestamp < item.timestamp) {
          seenThreadRoots[item.threadDetails.threadId] = item
        }
      } ?: run {
        seenThreadRoots[item.threadDetails.threadId] = item
      }
    }
  }

  private var currentlyOpenThread: String? = null
  private val seenThreadRoots = HashMap<String, IEvent.Event>()
  private val threadItemProducer = MutableStateFlow(emptyList<IEvent>().toImmutableList())
  val threadSubscriber = threadItemProducer.asStateFlow()

  private lateinit var taskHandle: TaskHandle

  private val initialized = MutableStateFlow(false)
  private val isInitialized = initialized.asStateFlow()

  init {
    val roomSubscription =
      RoomSubscription(timelineLimit = null, requiredState = listOf(RequiredState("*", "*")))
    var senderId: String? = null
    superVisorCoroutineScope.launch {
      val result = slidingSyncRoom.subscribeAndAddTimelineListener(
        this@TimelineStateObject, roomSubscription
      )
      result.items.forEach { ti ->
        val (evt, md) = toIEvent(
          ti,
          lastUserSeen = senderId,
          oldRoom,
          makeLazyListId,
          doUpdateOfThreadRoots
        )
        events.add(evt)
        md?.let { eventsMetadata[evt.id()!!] = md }
        senderId = evt.sender()
      }
      timelineProducer.value = events.toImmutableList()
      taskHandle = result.taskHandle
      initialized.emit(true)
    }
  }

  override fun timelineHasThread(eventId: String): Boolean {
    return seenThreadRoots.contains(eventId)
  }

  override fun getLatestSeenItemOfThread(eventId: String): IEvent.Event? {
    return seenThreadRoots[eventId]
  }

  override fun threadItemFlow(threadId: String): StateFlow<List<IEvent>> {
    currentlyOpenThread = threadId
    val start = events.findFirstIndexOf { it is IEvent.Event && it.eventId == currentlyOpenThread }
    val thread = events.subList(start ?: 0, events.size).filter {
      when (it) {
        is IEvent.Event -> it.threadDetails?.threadId == currentlyOpenThread
        else -> true
      }
    }.toImmutableList()
    threadItemProducer.value = thread
    return threadSubscriber
  }

  private val timelineStateRange =
    MutableStateFlow(CanRequestMoreState(hasMore = true, isLoading = false))

  override fun isInit(): Flow<Boolean> {
    return isInitialized.map { it }
  }

  override suspend fun withLockOnState(block: () -> Unit) {
    mutex.withLock { block() }
  }

  override fun patchDiff(diff: TimelineDiff) {
    when (diff.change()) {
      TimelineChange.APPEND -> {
        for ((evt, md) in diff.append()
          ?.map {
            toIEvent(
              it,
              lastUserSeen = this.events.lastOrNull()?.sender(),
              room = oldRoom,
              makeLazyListId,
              updateThreadRoots = doUpdateOfThreadRoots
            )
          }
          .orEmpty()) {
          this.events.add(evt)
          if (md != null)
            this.eventsMetadata[evt.id()!!] = md
        }
      }

      TimelineChange.CLEAR -> {
        this.events.clear()
        this.eventsMetadata.clear()
      }

      TimelineChange.INSERT -> diff.insert()?.use {
        val (evt, md) = toIEvent(
          it.item,
          lastUserSeen = this.events.lastOrNull()?.sender(),
          room = oldRoom,
          makeLazyListId,
          updateThreadRoots = doUpdateOfThreadRoots
        )
        this.events.add(it.index.toInt(), evt)
        if (md != null)
          this.eventsMetadata[evt.id()!!] = md
      }

      TimelineChange.SET -> diff.set()?.use {
        val idx = it.index.toInt()
        if (idx > this.events.size) {
          for (fill in this.events.size..idx) {
            this.events.add(fill, IEvent.Fill)
          }
        }
        val (evt, md) = toIEvent(
          it.item,
          lastUserSeen = this.events.getOrNull(idx - 1)?.sender(),
          room = oldRoom,
          makeLazyListId,
          updateThreadRoots = doUpdateOfThreadRoots
        )
        this.events[idx] = evt
        if (md != null) {
          this.eventsMetadata[evt.id()!!] = md
        }

        val shouldNotGroup: (String?, String?) -> Boolean = { a, b ->
          a != b && (a != null && b != null)
        }
        if (shouldNotGroup(
            this.events.getOrNull(idx + 1)?.sender(),
            this.events.getOrNull(idx + 1)?.sender()
          )
        ) {
          if ((this.events[idx + 1] as IEvent.Event).groupedByUser) {
            this.events[idx + 1] =
              (this.events[idx + 1] as IEvent.Event).copy(groupedByUser = false)
          }
        }
      }

      TimelineChange.REMOVE -> diff.remove()?.let {
        val idx = it.toInt()
        this.events[idx].letEvent { e -> this.eventsMetadata.remove(e.eventId) }
        this.events.removeAt(idx)
      }

      TimelineChange.PUSH_BACK -> diff.pushBack()?.use {
        val (evt, md) = toIEvent(
          it,
          lastUserSeen = this.events.lastOrNull()?.sender(),
          room = oldRoom,
          makeLazyListId,
          updateThreadRoots = doUpdateOfThreadRoots
        )
        this.events.add(evt)
        md?.let { this.eventsMetadata[evt.id()!!] = md }
      }

      TimelineChange.PUSH_FRONT -> diff.pushFront()?.use {
        val (evt, md) = toIEvent(
          it,
          lastUserSeen = null,
          room = oldRoom,
          makeLazyListId,
          updateThreadRoots = doUpdateOfThreadRoots
        )
        this.events.firstOrNull()?.letEvent { first ->
          this.events[0] = first.copy(groupedByUser = true)
        }
        md?.let { this.eventsMetadata[evt.id()!!] = md }
        this.events.add(0, evt)
      }

      TimelineChange.POP_BACK -> {
        this.events.removeLastOrNull()?.letEvent { this.eventsMetadata.remove(it.eventId) }
      }

      TimelineChange.POP_FRONT -> {
        this.events.removeFirstOrNull()?.letEvent { this.eventsMetadata.remove(it.eventId) }
      }

      TimelineChange.RESET -> diff.reset()?.let { items ->
        this.events.clear()
        this.eventsMetadata.clear()
        var senderId: String? = null
        for (item in items) {
          val (evt, md) = toIEvent(
            item,
            lastUserSeen = senderId,
            room = oldRoom,
            makeLazyListId,
            updateThreadRoots = doUpdateOfThreadRoots
          )
          senderId = evt.sender()
          this.events.add(evt)
          evt.letEvent { this.eventsMetadata[it.eventId] = md!! }
        }
      }
    }
  }

  override fun onUpdate(update: TimelineDiff) {
    superVisorCoroutineScope.launch {
      withContext(applyDiffDispatcher) {
        withLockOnState {
          update.use {
            patchDiff(it)
          }
          if (currentlyOpenThread != null) {
            val start =
              events.findFirstIndexOf { it is IEvent.Event && it.eventId == currentlyOpenThread }
            val thread = events.subList(start ?: 0, events.size).filter {
              when (it) {
                is IEvent.Event -> it.threadDetails?.threadId == currentlyOpenThread
                else -> true
              }
            }.toImmutableList()
            threadItemProducer.value = thread
          }
          timelineProducer.value = events.toImmutableList()
        }
      }
      when (val firstItem = events.firstOrNull()) {
        is IEvent.Virtual -> updateCanRequestMoreState(firstItem.virtual)
        else -> updateCanRequestMoreState(null)
      }
    }
  }

  /// Updates
  override fun updateCanRequestMoreState(virtualItem: VirtualTimelineItem?) {
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

  override fun canUpdateStateProducer(): StateFlow<CanRequestMoreState> {
    return this.timelineStateRange.asStateFlow()
  }

  override fun requestMore() {
    this.superVisorCoroutineScope.launch(this.applyDiffDispatcher) {
      val options = PaginationOptions.UntilNumItems(25u, 25u)
      oldRoom.paginateBackwards(options)
    }
  }

  override fun dispose() {
    Log.d("Timeline", "Disposing of timeline")
    taskHandle.use { it.cancel() }
    superVisorCoroutineScope.cancel()
  }

  override fun sendMessage(id: String, msg: String) {
    slidingSyncRoom.fullRoom()?.use {
      val msgAsMarkdown = messageEventContentFromMarkdown(msg)
      it.send(msgAsMarkdown, id)
    }
  }

  override fun sendReply(id: String, reply: TimelineAction.Reply) {
    slidingSyncRoom.fullRoom()?.use {
      it.sendReply(msg = reply.msg, inReplyToEventId = reply.eventId, txnId = id)
    }
  }

  override fun sendReaction(reaction: TimelineAction.React) {
    slidingSyncRoom.fullRoom()?.use {
      it.sendReaction(eventId = reaction.eventId, key = reaction.reaction)
    }
  }

  override fun editMessage(id: String, edit: TimelineAction.Edit) {
    slidingSyncRoom.fullRoom()?.use {
      it.edit(newMsg = edit.newContent, originalEventId = edit.eventId, txnId = id)
    }
  }

  override fun timelineSend(action: TimelineAction) {
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

  override fun avatar(): AvatarData = avatarData(this.slidingSyncRoom)

}
data class CanRequestMoreState(val hasMore: Boolean, val isLoading: Boolean)
sealed interface TimelineAction {
  data class Message(val msg: String) : TimelineAction
  data class Reply(val msg: String, val eventId: String) : TimelineAction
  data class ThreadReply(val msg: String, val eventId: String) : TimelineAction
  data class React(val reaction: String, val eventId: String) : TimelineAction
  data class Edit(val newContent: String, val eventId: String) : TimelineAction
}