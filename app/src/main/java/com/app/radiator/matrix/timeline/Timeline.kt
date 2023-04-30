@file:OptIn(DelicateCoroutinesApi::class)

package com.app.radiator.matrix.timeline

import android.util.Log
import androidx.compose.runtime.*
import com.app.radiator.matrix.htmlparse.HTMLParser
import com.app.radiator.ui.components.AvatarData
import com.app.radiator.ui.components.ParsedMessageNode
import com.app.radiator.ui.components.avatarData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.*

import org.matrix.rustcomponents.sdk.Message as FFIMessage
import org.matrix.rustcomponents.sdk.RepliedToEventDetails as FFIRepliedToEventDetails
import org.matrix.rustcomponents.sdk.InReplyToDetails as FFIInReplyToDetails
import org.matrix.rustcomponents.sdk.OtherState as FFIOtherState
import org.matrix.rustcomponents.sdk.ImageInfo as FFIImageInfo
import org.matrix.rustcomponents.sdk.ProfileDetails as FFIProfileDetails

sealed interface ProfileDetails {
  @Immutable
  data class Error(val message: String) : ProfileDetails

  @Immutable
  data class Ready(
    val avatarUrl: String?,
    val displayName: String?,
    val displayNameAmbiguous: Boolean,
  ) : ProfileDetails

  @Immutable
  object Pending : ProfileDetails

  @Immutable
  object Unavailable : ProfileDetails
}

fun FFIProfileDetails.avatarData(userId: String): AvatarData? {
  return when (this) {
    is FFIProfileDetails.Ready -> AvatarData(
      id = userId, name = this.displayName, url = this.avatarUrl
    )

    FFIProfileDetails.Pending -> TODO("Pending Profile details not handled yet")
    else -> null
  }
}

fun FFIProfileDetails.marshal(): ProfileDetails {
  return when (this) {
    is FFIProfileDetails.Error -> ProfileDetails.Error(message = message)
    is FFIProfileDetails.Ready -> ProfileDetails.Ready(
      avatarUrl = this.avatarUrl,
      displayName = displayName,
      displayNameAmbiguous = displayNameAmbiguous
    )

    FFIProfileDetails.Pending -> ProfileDetails.Pending
    FFIProfileDetails.Unavailable -> ProfileDetails.Unavailable
  }
}

interface RepliedToEventDetails {
  data class Error(val errorMessage: String) : RepliedToEventDetails
  data class Ready(val message: Message, val sender: String, val senderProfile: ProfileDetails) :
    RepliedToEventDetails

  object Pending : RepliedToEventDetails
  object Unavailable : RepliedToEventDetails
}

fun FFIRepliedToEventDetails.marshal(
  room: Room,
  eventId: String?,
  msgIsLocal: Boolean,
): RepliedToEventDetails {
  return when (this) {
    is org.matrix.rustcomponents.sdk.RepliedToEventDetails.Error -> RepliedToEventDetails.Error(
      message
    )

    is org.matrix.rustcomponents.sdk.RepliedToEventDetails.Ready -> RepliedToEventDetails.Ready(
      message = message.use { it.marshal(thisEventId = eventId, room = room, msgIsLocal) },
      sender = sender,
      senderProfile = senderProfile.marshal()
    )

    org.matrix.rustcomponents.sdk.RepliedToEventDetails.Unavailable -> {
      if (!msgIsLocal && eventId != null) {
        try {
          GlobalScope.launch {
            withContext(Dispatchers.IO) {
              room.fetchEventDetails(eventId)
            }
          }
        } catch (ex: Exception) {
          Log.d("Fetching event details", "Failed because $ex")
        }
      }
      RepliedToEventDetails.Unavailable
    }

    org.matrix.rustcomponents.sdk.RepliedToEventDetails.Pending -> RepliedToEventDetails.Pending
  }
}

data class InReplyToDetails(val event: RepliedToEventDetails, val eventId: String)

fun FFIInReplyToDetails.marshal(
  thisEventId: String?,
  room: Room,
  msgIsLocal: Boolean,
): InReplyToDetails =
  InReplyToDetails(
    event = event.marshal(room = room, thisEventId, msgIsLocal = msgIsLocal),
    eventId = eventId
  )

interface EventSendState {
  object NotSendYet : EventSendState
  data class SendingFailed(val error: String) : EventSendState
  data class Sent(val eventId: String) : EventSendState
}

sealed interface OtherState {
  fun displayText(userId: String): String? {
    return when (this) {
      is RoomAvatar -> "$userId changed room avatar to $url"
      is RoomName -> "$userId changed room name to ${this.name}"
      is RoomThirdPartyInvite -> "$userId 3rd party invited $displayName"
      is RoomTopic -> "$userId changed room topic to $topic"
      else -> null
    }
  }

  data class Custom(val eventType: String) : OtherState
  data class RoomAvatar(val url: String?) : OtherState
  data class RoomName(val name: String?) : OtherState
  data class RoomThirdPartyInvite(val displayName: String?) : OtherState
  data class RoomTopic(val topic: String?) : OtherState
  object PolicyRuleRoom : OtherState
  object PolicyRuleServer : OtherState
  object PolicyRuleUser : OtherState
  object RoomAliases : OtherState
  object RoomCanonicalAlias : OtherState
  object RoomCreate : OtherState
  object RoomEncryption : OtherState
  object RoomGuestAccess : OtherState
  object RoomHistoryVisibility : OtherState
  object RoomJoinRules : OtherState
  object RoomPinnedEvents : OtherState
  object RoomPowerLevels : OtherState
  object RoomServerAcl : OtherState
  object RoomTombstone : OtherState
  object SpaceChild : OtherState
  object SpaceParent : OtherState
}

fun FFIOtherState.marshal(): Pair<OtherState, Boolean> {
  return when (this) {
    is FFIOtherState.Custom -> Pair(OtherState.Custom(this.eventType), false)
    is FFIOtherState.RoomAvatar -> Pair(OtherState.RoomAvatar(this.url), true)
    is FFIOtherState.RoomName -> Pair(OtherState.RoomName(this.name), true)
    is FFIOtherState.RoomThirdPartyInvite -> Pair(
      OtherState.RoomThirdPartyInvite(this.displayName),
      true
    )
    is FFIOtherState.RoomTopic -> Pair(OtherState.RoomTopic(this.topic), true)
    FFIOtherState.PolicyRuleRoom -> Pair(OtherState.PolicyRuleRoom, false)
    FFIOtherState.PolicyRuleServer -> Pair(OtherState.PolicyRuleServer, false)
    FFIOtherState.PolicyRuleUser -> Pair(OtherState.PolicyRuleUser, false)
    FFIOtherState.RoomAliases -> Pair(OtherState.RoomAliases, false)
    FFIOtherState.RoomCanonicalAlias -> Pair(OtherState.RoomCanonicalAlias, false)
    FFIOtherState.RoomCreate -> Pair(OtherState.RoomCreate, false)
    FFIOtherState.RoomEncryption -> Pair(OtherState.RoomEncryption, false)
    FFIOtherState.RoomGuestAccess -> Pair(OtherState.RoomGuestAccess, false)
    FFIOtherState.RoomHistoryVisibility -> Pair(OtherState.RoomHistoryVisibility, false)
    FFIOtherState.RoomJoinRules -> Pair(OtherState.RoomJoinRules, false)
    FFIOtherState.RoomPinnedEvents -> Pair(OtherState.RoomPinnedEvents, false)
    FFIOtherState.RoomPowerLevels -> Pair(OtherState.RoomPowerLevels, false)
    FFIOtherState.RoomServerAcl -> Pair(OtherState.RoomServerAcl, false)
    FFIOtherState.RoomTombstone -> Pair(OtherState.RoomTombstone, false)
    FFIOtherState.SpaceChild -> Pair(OtherState.SpaceChild, false)
    FFIOtherState.SpaceParent -> Pair(OtherState.SpaceParent, false)
  }
}

fun org.matrix.rustcomponents.sdk.VideoInfo.marshal() = VideoInfo(
  duration = this.duration,
  height = this.height,
  width = this.width,
  mimetype = this.mimetype,
  size = this.size,
  thumbnailInfo = this.thumbnailInfo?.copy(),
  thumbnailSource = this.thumbnailSource?.use { it.url() },
  blurhash = this.blurhash
)

data class VideoInfo(
  val duration: ULong?,
  val height: ULong?,
  val width: ULong?,
  val mimetype: String?,
  val size: ULong?,
  val thumbnailInfo: ThumbnailInfo?,
  val thumbnailSource: String?,
  val blurhash: String?,
)


fun FFIImageInfo.marshal() = ImageInfo(
  height = this.height,
  width = this.width,
  mimetype = this.mimetype,
  size = this.size,
  thumbnailInfo = this.thumbnailInfo,
  thumbnailSource = this.thumbnailSource.use { it?.url() },
  blurhash = this.blurhash
)

data class ImageInfo(
  val height: ULong?,
  val width: ULong?,
  val mimetype: String?,
  val size: ULong?,
  val thumbnailInfo: ThumbnailInfo?,
  val thumbnailSource: String?,
  val blurhash: String?,
)

fun org.matrix.rustcomponents.sdk.FileInfo.marshal() = FileInfo(mimeType = this.mimetype,
  size = this.size,
  thumbnailInfo = this.thumbnailInfo?.copy(),
  thumbnailSource = this.thumbnailSource.use { it?.url() })

data class FileInfo(
  val mimeType: String?,
  val size: ULong?,
  val thumbnailInfo: ThumbnailInfo?,
  val thumbnailSource: String?,
)


@Immutable
sealed interface Message {
  // Message Types
  @Immutable
  data class Audio(
    val body: String,
    val inReplyTo: InReplyToDetails?,
    val isEdited: Boolean,
    val info: AudioInfo?,
    val source: String,
  ) : Message

  @Immutable
  data class Emote(
    val body: String,
    val inReplyTo: InReplyToDetails?,
    val isEdited: Boolean,
    val formatted: FormattedBody?,
  ) : Message

  @Immutable
  data class File(
    val body: String,
    val inReplyTo: InReplyToDetails?,
    val isEdited: Boolean,
    val info: FileInfo?,
    val source: String,
  ) : Message

  @Immutable
  data class Image(
    val body: String,
    val inReplyTo: InReplyToDetails?,
    val isEdited: Boolean,
    val info: ImageInfo?,
    val source: String,
  ) : Message

  @Immutable
  data class Notice(
    val body: String,
    val inReplyTo: InReplyToDetails?,
    val isEdited: Boolean,
    val formatted: FormattedBody?,
  ) : Message

  @Immutable
  data class Text(
    val body: String,
    val inReplyTo: InReplyToDetails?,
    val isEdited: Boolean,
    val formatted: FormattedBody?,
    val document: ParsedMessageNode? = null,
  ) : Message

  @Immutable
  data class Video(
    val body: String,
    val inReplyTo: InReplyToDetails?,
    val isEdited: Boolean,
    val info: VideoInfo?,
    val source: String,
  ) : Message

  @Immutable
  // Event types
  data class FailedToParseMessageLike(val eventType: String, val error: String) : Message

  @Immutable
  data class FailedToParseState(
    val eventType: String,
    val stateKey: String,
    val error: String,
  ) : Message

  @Immutable
  data class ProfileChange(
    val displayName: String?,
    val prevDisplayName: String?,
    val avatarUrl: String?,
    val prevAvatarUrl: String?,
  ) : Message

  @Immutable
  data class RoomMembership(
    val userId: String,
    val change: MembershipChange?,
  ) : Message

  @Immutable
  data class State(val stateKey: String, val content: OtherState) : Message

  @Immutable
  data class Sticker(val body: String, val info: ImageInfo, val url: String) : Message

  @Immutable
  data class UnableToDecrypt(val msg: EncryptedMessage) : Message

  @Immutable
  object RedactedMessage : Message
}

// EventItems in the timeline that has been marshaled (copied) from the Rust SDK backend.

@Immutable
interface VirtualTimelineItem {
  @Immutable
  data class DayDivider(val ts: ULong) : VirtualTimelineItem

  @Immutable
  object LoadingIndicator : VirtualTimelineItem

  @Immutable
  object ReadMarker : VirtualTimelineItem

  @Immutable
  object TimelineStart : VirtualTimelineItem
}

fun FFIProfileDetails.displayName(): String? {
  return when (this) {
    is FFIProfileDetails.Error -> this.message
    is FFIProfileDetails.Ready -> this.displayName
    FFIProfileDetails.Pending,
    FFIProfileDetails.Unavailable,
    -> null
  }
}

@Immutable
sealed interface TimelineItemVariant {

  fun contentType(): Int = when (this) {
    is Event -> 0
    is Virtual -> 1
    is Fill -> 2
    Unknown -> 3
  }

  fun id(): Int = when (this) {
    is Event -> id
    is Virtual -> id
    is Fill -> 0
    Unknown -> -1
  }

  fun sender(): String? = when (this) {
    is Event -> this.sender
    else -> null
  }

  @Immutable
  data class Event(
    val id: Int,
    val uniqueIdentifier: String,
    val eventId: String?,
    val isEditable: Boolean,
    val isLocal: Boolean,
    val isOwn: Boolean,
    val isRemote: Boolean,
    val localSendState: EventSendState?,
    val reactions: List<Reaction>,
    val sender: String,
    val senderProfile: FFIProfileDetails,
    val timestamp: ULong,
    val message: Message,
    val groupedByUser: Boolean,
    val userCanSee: Boolean = true,
  ) : TimelineItemVariant

  @Immutable
  data class Virtual(val id: Int, val virtual: VirtualTimelineItem) : TimelineItemVariant

  @Immutable
  object Fill : TimelineItemVariant

  @Immutable
  object Unknown : TimelineItemVariant
}


fun org.matrix.rustcomponents.sdk.EventSendState.marshal(): EventSendState = when (this) {
  is org.matrix.rustcomponents.sdk.EventSendState.NotSendYet -> EventSendState.NotSendYet
  is org.matrix.rustcomponents.sdk.EventSendState.SendingFailed -> EventSendState.SendingFailed(
    this.error
  )

  is org.matrix.rustcomponents.sdk.EventSendState.Sent -> EventSendState.Sent(this.eventId)
}

private fun EventTimelineItem.marshal(
  lastUserSeen: String?,
  room: Room,
): TimelineItemVariant.Event {
  val sender = this.sender()
  val continuous = lastUserSeen?.equals(sender) ?: false
  val msgIsLocal = isLocal()
  val (message, visibleToUsers) = this.content()
    .use { it.marshal(this.eventId(), room, msgIsLocal) }
  if (this.isLocal()) {
    Log.i("EventTimelineItem", "Encountered local event")
  }
  return TimelineItemVariant.Event(
    id = (0..Int.MAX_VALUE).random(),
    uniqueIdentifier = this.uniqueIdentifier(),
    eventId = this.eventId(),
    isEditable = this.isEditable(),
    isLocal = this.isLocal(),
    isOwn = this.isOwn(),
    isRemote = this.isRemote(),
    localSendState = this.localSendState()?.marshal(),
    reactions = this.reactions().map { Reaction(it.key, it.count) },
    sender = sender,
    senderProfile = this.senderProfile(),
    timestamp = this.timestamp(),
    message = message,
    groupedByUser = continuous,
    userCanSee = visibleToUsers
  )
}

val messageBuilder = HTMLParser(blackListedTags = setOf("blockquote", "br"))

fun FFIMessage.marshal(thisEventId: String?, room: Room, msgIsLocal: Boolean): Message {
  return when (val msgType = msgtype()!!) {
    is MessageType.Audio -> Message.Audio(body = body(),
      inReplyTo = inReplyTo()?.use {
        it.marshal(
          thisEventId = thisEventId,
          room = room,
          msgIsLocal = msgIsLocal
        )
      },
      isEdited = isEdited(),
      info = msgType.content.info,
      source = msgType.content.source.use { it.url() })

    is MessageType.Emote -> Message.Emote(
      body = body(),
      inReplyTo = inReplyTo()?.use {
        it.marshal(
          thisEventId = thisEventId,
          room = room,
          msgIsLocal = msgIsLocal
        )
      },
      isEdited = isEdited(),
      formatted = msgType.content.formatted?.copy()
    )

    is MessageType.File -> Message.File(body = body(),
      inReplyTo = inReplyTo()?.use {
        it.marshal(
          thisEventId = thisEventId,
          room = room,
          msgIsLocal = msgIsLocal
        )
      },
      isEdited = isEdited(),
      info = msgType.content.info?.marshal(),
      source = msgType.content.source.use { it.url() })

    is MessageType.Image -> Message.Image(body = body(),
      inReplyTo = inReplyTo()?.use {
        it.marshal(
          thisEventId = thisEventId,
          room = room,
          msgIsLocal = msgIsLocal
        )
      },
      isEdited = isEdited(),
      info = msgType.content.info?.marshal(),
      source = msgType.content.source.use { it.url() })

    is MessageType.Notice -> Message.Notice(
      body = body(),
      inReplyTo = inReplyTo()?.use {
        it.marshal(
          thisEventId = thisEventId,
          room = room,
          msgIsLocal = msgIsLocal
        )
      },
      isEdited = isEdited(),
      formatted = msgType.content.formatted?.copy()
    )

    is MessageType.Text -> {
      val mxStripped = stripMxReplyBlock(msgType.content.formatted?.copy())
      val doc = if (mxStripped != null) {
        try {
          messageBuilder.parse(mxStripped.body)
        } catch (ex: Exception) {
          Log.d("HTMLParse", "Parse failed: $ex on contents ${mxStripped.body}")
          null
        }
      } else null
      val body_ = body()
      Message.Text(
        body = body_,
        inReplyTo = inReplyTo()?.use {
          it.marshal(
            thisEventId = thisEventId,
            room = room,
            msgIsLocal = msgIsLocal
          )
        },
        isEdited = isEdited(),
        formatted = mxStripped,
        document = doc
      )
    }

    is MessageType.Video -> Message.Video(body = body(),
      inReplyTo = inReplyTo()?.use {
        it.marshal(
          thisEventId = thisEventId,
          room = room,
          msgIsLocal = msgIsLocal
        )
      },
      isEdited = isEdited(),
      info = msgType.content.info?.marshal(),
      source = msgType.content.source.use { it.url() })
  }
}

fun stripMxReplyBlock(fmtBodyCopy: FormattedBody?): FormattedBody? {
  return fmtBodyCopy?.copy(body = fmtBodyCopy.body.substringAfterLast("</mx-reply>"))
}

@Immutable
data class ItemContentParse(val messsage: Message, val isVisible: Boolean)

private fun TimelineItemContent.marshal(
  thisEventId: String?,
  room: Room,
  msgIsLocal: Boolean,
): ItemContentParse {
  return when (val kind = this.kind()) {
    is TimelineItemContentKind.FailedToParseMessageLike -> ItemContentParse(
      Message.FailedToParseMessageLike(
        eventType = kind.eventType, error = kind.error
      ), true
    )

    is TimelineItemContentKind.FailedToParseState -> ItemContentParse(
      Message.FailedToParseState(
        eventType = kind.eventType, error = kind.error, stateKey = kind.stateKey
      ), false
    )

    is TimelineItemContentKind.ProfileChange -> ItemContentParse(
      Message.ProfileChange(
        displayName = kind.displayName,
        prevDisplayName = kind.prevDisplayName,
        avatarUrl = kind.avatarUrl,
        prevAvatarUrl = kind.prevAvatarUrl
      ), true
    )

    is TimelineItemContentKind.RoomMembership -> ItemContentParse(
      Message.RoomMembership(
        userId = kind.userId, change = kind.change
      ), true
    )

    is TimelineItemContentKind.State -> {
      val (content, roomVisibility) = kind.content.marshal()
      ItemContentParse(Message.State(stateKey = kind.stateKey, content = content), roomVisibility)
    }

    is TimelineItemContentKind.Sticker -> ItemContentParse(
      Message.Sticker(
        body = kind.body, info = kind.info.marshal(), url = kind.url
      ), true
    )

    is TimelineItemContentKind.UnableToDecrypt -> ItemContentParse(
      Message.UnableToDecrypt(msg = kind.msg),
      true
    )

    TimelineItemContentKind.Message -> {
      val message = this.asMessage()!!
      return ItemContentParse(
        message.marshal(thisEventId, room = room, msgIsLocal = msgIsLocal),
        true
      )
    }

    TimelineItemContentKind.RedactedMessage -> ItemContentParse(Message.RedactedMessage, true)
  }
}

data class CanRequestMoreState(val hasMore: Boolean, val isLoading: Boolean)

/**
 * Is responsible for subscribing as a listener to SlidingSyncRoom's `onUpdate`.
 * When receiving an update, it then applies those updates to the current Timeline's state,
 * which is represented by a Stream (list) of `TimelineItemVariant`s so which then the UI
 * can render.
 */
class TimelineState(
  val roomId: String,
  private val slidingSyncRoom: SlidingSyncRoom,
  private val superVisorCoroutineScope: CoroutineScope,
  private val diffApplyDispatcher: CoroutineDispatcher,
) : TimelineListener {
  private val initialized = MutableStateFlow(false)
  private val isInitialized = initialized.asStateFlow()
  private val timelineItems: MutableStateFlow<List<TimelineItemVariant>> =
    MutableStateFlow(emptyList())
  private val mutex = Mutex()
  val currentStateFlow = timelineItems.asStateFlow()
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
      timelineItems.value = initList
      taskHandle = result.taskHandle
      initialized.value = true
    }
  }

  fun avatar(): AvatarData = avatarData(this.slidingSyncRoom)

  fun isInit(): Flow<Boolean> {
    return isInitialized.map { it }
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
  private fun updateCanRequestMoreState(virtualItem: VirtualTimelineItem?) {
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
    timelineItems.value = mutableList
  }

  private fun takeMap(
    item: TimelineItem,
    lastUserSeen: String?,
    room: Room,
  ): TimelineItemVariant = item.use {

    it.asEvent()?.use { evt -> evt.marshal(lastUserSeen, room = room) }?.let { i -> return i }

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

  private fun MutableList<TimelineItemVariant>.patchDiff(diff: TimelineDiff) {
    when (diff.change()) {
      TimelineChange.APPEND -> {
        val append =
          diff.append()
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
            this[idx + 1] =
              (this[idx + 1] as TimelineItemVariant.Event).copy(groupedByUser = false)
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
          if (first is TimelineItemVariant.Event && first.sender == item.sender()) {
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

  private fun sendMessage(id: String, msg: String) {
    slidingSyncRoom.fullRoom()?.use {
      val msgAsMarkdown = messageEventContentFromMarkdown(msg)
      it.send(msgAsMarkdown, id)
    }
  }

  private fun sendReply(id: String, reply: TimelineAction.Reply) {
    slidingSyncRoom.fullRoom()?.use {
      it.sendReply(msg = reply.msg, inReplyToEventId = reply.eventId, txnId = id)
    }
  }

  private fun sendReaction(reaction: TimelineAction.React) {
    slidingSyncRoom.fullRoom()?.use {
      it.sendReaction(eventId = reaction.eventId, key = reaction.reaction)
    }
  }

  private fun editMessage(id: String, edit: TimelineAction.Edit) {
    slidingSyncRoom.fullRoom()?.use {
      it.edit(newMsg = edit.newContent, originalEventId = edit.eventId, txnId = id)
    }
  }

  fun send(action: TimelineAction) {
    superVisorCoroutineScope.launch {
      withContext(Dispatchers.IO) {
        when (action) {
          is TimelineAction.Message -> sendMessage(id = genTransactionId(), msg = action.msg)
          is TimelineAction.Reply -> sendReply(id = genTransactionId(), action)
          is TimelineAction.React -> sendReaction(action)
          is TimelineAction.Edit -> editMessage(id = genTransactionId(), edit=action)
          is TimelineAction.ThreadReply -> TODO()
        }
      }
    }
  }
}

sealed interface TimelineAction {
  data class Message(val msg: String) : TimelineAction
  data class Reply(val msg: String, val eventId: String) : TimelineAction
  data class ThreadReply(val msg: String, val eventId: String) : TimelineAction
  data class React(val reaction: String, val eventId: String) : TimelineAction

  data class Edit(val newContent: String, val eventId: String) : TimelineAction
}