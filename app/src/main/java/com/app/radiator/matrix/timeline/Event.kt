package com.app.radiator.matrix.timeline

import android.util.Log
import androidx.compose.runtime.Immutable
import com.app.radiator.matrix.htmlparse.HTMLParser
import com.app.radiator.ui.components.ParsedMessageNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.AudioInfo
import org.matrix.rustcomponents.sdk.EncryptedMessage
import org.matrix.rustcomponents.sdk.EventTimelineItem
import org.matrix.rustcomponents.sdk.FormattedBody
import org.matrix.rustcomponents.sdk.InThreadDetails
import org.matrix.rustcomponents.sdk.MembershipChange
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.Reaction
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.ThumbnailInfo
import org.matrix.rustcomponents.sdk.TimelineItemContent
import org.matrix.rustcomponents.sdk.TimelineItemContentKind
import org.matrix.rustcomponents.sdk.use

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

fun org.matrix.rustcomponents.sdk.ProfileDetails.marshal(): ProfileDetails {
  return when (this) {
    is org.matrix.rustcomponents.sdk.ProfileDetails.Error -> ProfileDetails.Error(message = message)
    is org.matrix.rustcomponents.sdk.ProfileDetails.Ready -> ProfileDetails.Ready(
      avatarUrl = this.avatarUrl,
      displayName = displayName,
      displayNameAmbiguous = displayNameAmbiguous
    )

    org.matrix.rustcomponents.sdk.ProfileDetails.Pending -> ProfileDetails.Pending
    org.matrix.rustcomponents.sdk.ProfileDetails.Unavailable -> ProfileDetails.Unavailable
  }
}

interface RepliedToEventDetails {
  data class Error(val errorMessage: String) : RepliedToEventDetails
  data class Ready(val message: Message, val sender: String, val senderProfile: ProfileDetails) :
    RepliedToEventDetails

  object Pending : RepliedToEventDetails
  object Unavailable : RepliedToEventDetails
}

fun org.matrix.rustcomponents.sdk.RepliedToEventDetails.marshal(
  room: Room,
  eventId: String?,
  msgIsLocal: Boolean,
  inThreadDetails: InThreadDetails?,
): RepliedToEventDetails {
  return when (this) {
    is org.matrix.rustcomponents.sdk.RepliedToEventDetails.Error -> RepliedToEventDetails.Error(
      message
    )

    is org.matrix.rustcomponents.sdk.RepliedToEventDetails.Ready -> {
      val (msg, inReply, threadId) = message.use {
        it.marshal(
          thisEventId = eventId, room = room, msgIsLocal
        )
      }
      RepliedToEventDetails.Ready(
        message = msg, sender = sender, senderProfile = senderProfile.marshal()
      )
    }

    org.matrix.rustcomponents.sdk.RepliedToEventDetails.Unavailable -> {
      val shouldFetchReply = inThreadDetails?.let { !it.isFallback } ?: true
      if (!msgIsLocal && eventId != null && shouldFetchReply) {
        try {
          GlobalScope.launch {
            withContext(Dispatchers.IO) {
              Log.d("Event Details", "Fetching event details for $eventId")
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

fun org.matrix.rustcomponents.sdk.InReplyToDetails.marshal(
  thisEventId: String?,
  room: Room,
  msgIsLocal: Boolean,
  inThreadDetails: InThreadDetails?,
): InReplyToDetails = InReplyToDetails(
  event = event.marshal(
    room = room,
    thisEventId,
    msgIsLocal = msgIsLocal,
    inThreadDetails = inThreadDetails
  ), eventId = eventId
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

fun org.matrix.rustcomponents.sdk.OtherState.marshal(): Pair<OtherState, Boolean> {
  return when (this) {
    is org.matrix.rustcomponents.sdk.OtherState.Custom -> Pair(
      OtherState.Custom(this.eventType),
      false
    )

    is org.matrix.rustcomponents.sdk.OtherState.RoomAvatar -> Pair(
      OtherState.RoomAvatar(this.url),
      true
    )

    is org.matrix.rustcomponents.sdk.OtherState.RoomName -> Pair(
      OtherState.RoomName(this.name),
      true
    )

    is org.matrix.rustcomponents.sdk.OtherState.RoomThirdPartyInvite -> Pair(
      OtherState.RoomThirdPartyInvite(this.displayName), true
    )

    is org.matrix.rustcomponents.sdk.OtherState.RoomTopic -> Pair(
      OtherState.RoomTopic(this.topic),
      true
    )

    org.matrix.rustcomponents.sdk.OtherState.PolicyRuleRoom -> Pair(
      OtherState.PolicyRuleRoom,
      false
    )

    org.matrix.rustcomponents.sdk.OtherState.PolicyRuleServer -> Pair(
      OtherState.PolicyRuleServer,
      false
    )

    org.matrix.rustcomponents.sdk.OtherState.PolicyRuleUser -> Pair(
      OtherState.PolicyRuleUser,
      false
    )

    org.matrix.rustcomponents.sdk.OtherState.RoomAliases -> Pair(OtherState.RoomAliases, false)
    org.matrix.rustcomponents.sdk.OtherState.RoomCanonicalAlias -> Pair(
      OtherState.RoomCanonicalAlias,
      false
    )

    org.matrix.rustcomponents.sdk.OtherState.RoomCreate -> Pair(OtherState.RoomCreate, false)
    org.matrix.rustcomponents.sdk.OtherState.RoomEncryption -> Pair(
      OtherState.RoomEncryption,
      false
    )

    org.matrix.rustcomponents.sdk.OtherState.RoomGuestAccess -> Pair(
      OtherState.RoomGuestAccess,
      false
    )

    org.matrix.rustcomponents.sdk.OtherState.RoomHistoryVisibility -> Pair(
      OtherState.RoomHistoryVisibility,
      false
    )

    org.matrix.rustcomponents.sdk.OtherState.RoomJoinRules -> Pair(OtherState.RoomJoinRules, false)
    org.matrix.rustcomponents.sdk.OtherState.RoomPinnedEvents -> Pair(
      OtherState.RoomPinnedEvents,
      false
    )

    org.matrix.rustcomponents.sdk.OtherState.RoomPowerLevels -> Pair(
      OtherState.RoomPowerLevels,
      false
    )

    org.matrix.rustcomponents.sdk.OtherState.RoomServerAcl -> Pair(OtherState.RoomServerAcl, false)
    org.matrix.rustcomponents.sdk.OtherState.RoomTombstone -> Pair(OtherState.RoomTombstone, false)
    org.matrix.rustcomponents.sdk.OtherState.SpaceChild -> Pair(OtherState.SpaceChild, false)
    org.matrix.rustcomponents.sdk.OtherState.SpaceParent -> Pair(OtherState.SpaceParent, false)
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


fun org.matrix.rustcomponents.sdk.ImageInfo.marshal() = ImageInfo(
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
    val isEdited: Boolean,
    val info: AudioInfo?,
    val source: String,
  ) : Message

  @Immutable
  data class Emote(
    val body: String,
    val isEdited: Boolean,
    val formatted: FormattedBody?,
  ) : Message

  @Immutable
  data class File(
    val body: String,
    val isEdited: Boolean,
    val info: FileInfo?,
    val source: String,
  ) : Message

  @Immutable
  data class Image(
    val body: String,
    val isEdited: Boolean,
    val info: ImageInfo?,
    val source: String,
  ) : Message

  @Immutable
  data class Notice(
    val body: String,
    val isEdited: Boolean,
    val formatted: FormattedBody?,
  ) : Message

  @Immutable
  data class Text(
    val body: String,
    val isEdited: Boolean,
    val formatted: FormattedBody?,
    val document: ParsedMessageNode? = null,
  ) : Message

  @Immutable
  data class Video(
    val body: String,
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

fun ProfileDetails.displayName(): String? {
  return when (this) {
    is ProfileDetails.Error -> this.message
    is ProfileDetails.Ready -> this.displayName
    ProfileDetails.Pending,
    ProfileDetails.Unavailable,
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
    is Event -> this.senderId
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
    val groupedByUser: Boolean,
    val userCanSee: Boolean = true,
    val localSendState: EventSendState?,
    val reactions: List<Reaction>,
    val senderId: String,
    val senderProfile: ProfileDetails,
    val timestamp: ULong,
    val message: Message,
    val inReplyTo: InReplyToDetails?,
    val threadDetails: InThreadDetails?,
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

fun EventTimelineItem.marshal(
  lastUserSeen: String?,
  room: Room,
): TimelineItemVariant.Event {
  val sender = this.sender()
  val continuous = lastUserSeen?.equals(sender) ?: false
  val msgIsLocal = isLocal()
  val (message, visibleToUsers, threadDetails, inReply) = this.content()
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
    senderId = sender,
    senderProfile = this.senderProfile().marshal(),
    timestamp = this.timestamp(),
    message = message,
    groupedByUser = continuous,
    userCanSee = visibleToUsers,
    threadDetails = threadDetails,
    inReplyTo = inReply
  )
}

val messageBuilder = HTMLParser(blackListedTags = setOf("blockquote", "br"))

fun org.matrix.rustcomponents.sdk.Message.marshal(
  thisEventId: String?,
  room: Room,
  msgIsLocal: Boolean,
): Triple<Message, InReplyToDetails?, InThreadDetails?> {
  val inThreadDetails = this.inThread()
  val inReplyDetails = inReplyTo()?.use {
    it.marshal(thisEventId = thisEventId, room = room, msgIsLocal = msgIsLocal, inThreadDetails)
  }
  return when (val msgType = msgtype()!!) {
    is MessageType.Audio -> Triple(Message.Audio(body = body(),
      isEdited = isEdited(),
      info = msgType.content.info,
      source = msgType.content.source.use { it.url() }
    ),
      inReplyDetails,
      inThreadDetails
    )

    is MessageType.Emote -> Triple(
      Message.Emote(
        body = body(),
        isEdited = isEdited(), formatted = msgType.content.formatted?.copy()
      ),
      inReplyDetails,
      inThreadDetails
    )

    is MessageType.File -> Triple(
      Message.File(body = body(),
        isEdited = isEdited(),
        info = msgType.content.info?.marshal(),
        source = msgType.content.source.use { it.url() }),
      inReplyDetails,
      inThreadDetails
    )

    is MessageType.Image -> Triple(
      Message.Image(body = body(),
        isEdited = isEdited(),
        info = msgType.content.info?.marshal(),
        source = msgType.content.source.use { it.url() }),
      inReplyDetails,
      inThreadDetails
    )

    is MessageType.Notice -> Triple(
      Message.Notice(
        body = body(), isEdited = isEdited(), formatted = msgType.content.formatted?.copy()
      ),
      inReplyDetails,
      inThreadDetails
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
      Triple(
        Message.Text(
          body = body_, isEdited = isEdited(), formatted = mxStripped, document = doc
        ),
        inReplyDetails, inThreadDetails
      )
    }

    is MessageType.Video -> Triple(
      Message.Video(body = body(),
        isEdited = isEdited(),
        info = msgType.content.info?.marshal(),
        source = msgType.content.source.use { it.url() }),
      inReplyDetails,
      inThreadDetails
    )
  }
}

fun stripMxReplyBlock(fmtBodyCopy: FormattedBody?): FormattedBody? {
  return fmtBodyCopy?.copy(body = fmtBodyCopy.body.substringAfterLast("</mx-reply>"))
}

@Immutable
data class ItemContentParse(
  val msg: Message,
  val isVisible: Boolean,
  val threadDetails: InThreadDetails?,
  val inReplyTo: InReplyToDetails?,
)

fun TimelineItemContent.marshal(
  thisEventId: String?,
  room: Room,
  msgIsLocal: Boolean,
): ItemContentParse {
  return when (val kind = this.kind()) {
    is TimelineItemContentKind.FailedToParseMessageLike -> ItemContentParse(
      Message.FailedToParseMessageLike(
        eventType = kind.eventType, error = kind.error
      ), true, null, null
    )

    is TimelineItemContentKind.FailedToParseState -> ItemContentParse(
      Message.FailedToParseState(
        eventType = kind.eventType, error = kind.error, stateKey = kind.stateKey
      ), false, null, null
    )

    is TimelineItemContentKind.ProfileChange -> ItemContentParse(
      Message.ProfileChange(
        displayName = kind.displayName,
        prevDisplayName = kind.prevDisplayName,
        avatarUrl = kind.avatarUrl,
        prevAvatarUrl = kind.prevAvatarUrl
      ), true, null, null
    )

    is TimelineItemContentKind.RoomMembership -> ItemContentParse(
      Message.RoomMembership(
        userId = kind.userId, change = kind.change
      ), true, null, null
    )

    is TimelineItemContentKind.State -> {
      val (content, roomVisibility) = kind.content.marshal()
      ItemContentParse(
        Message.State(stateKey = kind.stateKey, content = content), roomVisibility, null, null
      )
    }

    is TimelineItemContentKind.Sticker -> ItemContentParse(
      Message.Sticker(
        body = kind.body, info = kind.info.marshal(), url = kind.url
      ), true, null, null
    )

    is TimelineItemContentKind.UnableToDecrypt -> ItemContentParse(
      Message.UnableToDecrypt(msg = kind.msg), true, null, null
    )

    TimelineItemContentKind.Message -> {
      val message = this.asMessage()!!
      val (msg, inReply, thread) = message.marshal(
        thisEventId,
        room = room,
        msgIsLocal = msgIsLocal
      )
      return ItemContentParse(msg, isVisible = true, inReplyTo = inReply, threadDetails = thread)
    }

    TimelineItemContentKind.RedactedMessage -> ItemContentParse(
      Message.RedactedMessage,
      true,
      null,
      null
    )
  }
}