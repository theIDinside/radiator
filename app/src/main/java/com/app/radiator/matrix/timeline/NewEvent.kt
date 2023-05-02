package com.app.radiator.matrix.timeline

import android.util.Log
import androidx.compose.runtime.Immutable
import org.matrix.rustcomponents.sdk.EventTimelineItem
import org.matrix.rustcomponents.sdk.InThreadDetails
import org.matrix.rustcomponents.sdk.Reaction
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.TimelineItem
import org.matrix.rustcomponents.sdk.use

fun EventTimelineItem.marshalEvent(
  lastUserSeen: String?,
  room: Room,
  generateListId: () -> Int,
): Pair<IEvent.Event, EventMetadata> {
  val sender = this.sender()
  val continuous = lastUserSeen?.equals(sender) ?: false
  val msgIsLocal = isLocal()
  val (message, visibleToUsers, threadDetails, inReply) = this.content()
    .use { it.marshal(this.eventId(), room, msgIsLocal) }
  if (this.isLocal()) {
    Log.i("EventTimelineItem", "Encountered local event")
  }

  val event = IEvent.Event(
    lazyListId = generateListId(),
    eventId = this.uniqueIdentifier(),
    reactions = this.reactions().map { Reaction(it.key, it.count) },
    senderId = sender,
    senderProfile = this.senderProfile().marshal(),
    timestamp = this.timestamp(),
    message = message,
    groupedByUser = continuous,
    userCanSee = visibleToUsers,
    threadDetails = threadDetails,
    inReplyTo = inReply
  )

  val metadata = EventMetadata(
    isEditable = isEditable(),
    isLocal = isLocal(),
    isOwn = isOwn(),
    isRemote = isRemote(),
    localSendState = this.localSendState()?.marshal()
  )
  return Pair(event, metadata)
}

fun toIEvent(
  convertFrom: TimelineItem,
  lastUserSeen: String?,
  room: Room,
  makeListId: () -> Int,
  updateThreadRoots: (IEvent.Event) -> Unit,
): Pair<IEvent, EventMetadata?> = convertFrom.use {

  it.asEvent()?.use { evt ->
    evt.marshalEvent(lastUserSeen, room = room, makeListId)
  }?.let { i ->
    updateThreadRoots(i.first)
    return i
  }

  it.asVirtual()?.let { asVirtual ->
    val id = makeListId()
    return Pair(
      when (asVirtual) {
        is org.matrix.rustcomponents.sdk.VirtualTimelineItem.DayDivider -> IEvent.Virtual(
          id, VirtualTimelineItem.DayDivider(asVirtual.ts)
        )

        org.matrix.rustcomponents.sdk.VirtualTimelineItem.LoadingIndicator -> IEvent.Virtual(
          id, VirtualTimelineItem.LoadingIndicator
        )

        org.matrix.rustcomponents.sdk.VirtualTimelineItem.ReadMarker -> IEvent.Virtual(
          id, VirtualTimelineItem.ReadMarker
        )

        org.matrix.rustcomponents.sdk.VirtualTimelineItem.TimelineStart -> IEvent.Virtual(
          id, VirtualTimelineItem.TimelineStart
        )
      }, null
    )
  }
  return Pair(IEvent.Unknown, null)
}

// Timeline event data relevant for UI
@Immutable
sealed interface IEvent {
  fun contentType(): Int = when (this) {
    is Event -> 0
    is Virtual -> 1
    is Fill -> 2
    Unknown -> 3
  }

  fun lazyListId(): Int {
    return when(this) {
      is Event -> lazyListId
      is Virtual -> id
      Fill,Unknown -> 0
    }
  }

  fun id(): String? {
    return when (this) {
      is Event -> this.eventId
      Fill, Unknown, is Virtual -> null
    }
  }

  fun sender(): String? {
    return when (this) {
      is Event -> this.senderId
      Fill, Unknown, is Virtual -> null
    }
  }

  @Immutable
  data class Event(
    val lazyListId: Int,
    val eventId: String,
    val userCanSee: Boolean = true,
    val reactions: List<Reaction>,
    val senderId: String,
    val senderProfile: ProfileDetails,
    val timestamp: ULong,
    val message: Message,
    val inReplyTo: InReplyToDetails?,
    val threadDetails: InThreadDetails?,
    val groupedByUser: Boolean,
  ) : IEvent

  @Immutable
  data class Virtual(val id: Int, val virtual: VirtualTimelineItem) : IEvent

  @Immutable
  object Fill : IEvent

  @Immutable
  object Unknown : IEvent
}

inline fun IEvent.letEvent(block: (IEvent.Event) -> Unit) {
  if (this is IEvent.Event) {
    block(this)
  }
}

// Timeline event data not relevant for UI-display, only for interaction
data class EventMetadata(
  val isEditable: Boolean,
  val isLocal: Boolean,
  val isOwn: Boolean,
  val isRemote: Boolean,
  val localSendState: EventSendState?,
)