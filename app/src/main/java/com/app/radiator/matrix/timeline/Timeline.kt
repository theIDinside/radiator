package com.app.radiator.matrix.timeline

import android.util.Log
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

interface ProfileDetails {
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

fun FFIRepliedToEventDetails.marshal(): RepliedToEventDetails {
    return when (this) {
        is org.matrix.rustcomponents.sdk.RepliedToEventDetails.Error -> RepliedToEventDetails.Error(
            message
        )

        is org.matrix.rustcomponents.sdk.RepliedToEventDetails.Ready -> RepliedToEventDetails.Ready(
            message = message.use { it.marshal() },
            sender = sender,
            senderProfile = senderProfile.marshal()
        )

        org.matrix.rustcomponents.sdk.RepliedToEventDetails.Unavailable -> RepliedToEventDetails.Unavailable
        org.matrix.rustcomponents.sdk.RepliedToEventDetails.Pending -> RepliedToEventDetails.Pending
    }
}

data class InReplyToDetails(val event: RepliedToEventDetails, val eventId: String)

fun FFIInReplyToDetails.marshal(): InReplyToDetails =
    InReplyToDetails(event = event.marshal(), eventId = eventId)

interface EventSendState {
    object NotSendYet : EventSendState
    data class SendingFailed(val error: String) : EventSendState
    data class Sent(val eventId: String) : EventSendState
}

interface OtherState {
    data class Custom(val eventType: String) : OtherState
    object PolicyRuleRoom : OtherState

    object PolicyRuleServer : OtherState

    object PolicyRuleUser : OtherState

    object RoomAliases : OtherState

    data class RoomAvatar constructor(val url: String?) : OtherState

    object RoomCanonicalAlias : OtherState

    object RoomCreate : OtherState

    object RoomEncryption : OtherState

    object RoomGuestAccess : OtherState

    object RoomHistoryVisibility : OtherState

    object RoomJoinRules : OtherState

    data class RoomName(val name: String?) : OtherState

    object RoomPinnedEvents : OtherState

    object RoomPowerLevels : OtherState

    object RoomServerAcl : OtherState

    data class RoomThirdPartyInvite(val displayName: String?) : OtherState

    object RoomTombstone : OtherState

    data class RoomTopic(val topic: String?) : OtherState

    object SpaceChild : OtherState

    object SpaceParent : OtherState
}

fun FFIOtherState.marshal(): OtherState {
    return when (this) {
        is FFIOtherState.Custom -> OtherState.Custom(this.eventType)
        is FFIOtherState.RoomAvatar -> OtherState.RoomAvatar(this.url)
        is FFIOtherState.RoomName -> OtherState.RoomName(this.name)
        is FFIOtherState.RoomThirdPartyInvite -> OtherState.RoomThirdPartyInvite(
            this.displayName
        )

        is FFIOtherState.RoomTopic -> OtherState.RoomTopic(this.topic)
        FFIOtherState.PolicyRuleRoom -> OtherState.PolicyRuleRoom
        FFIOtherState.PolicyRuleServer -> OtherState.PolicyRuleServer
        FFIOtherState.PolicyRuleUser -> OtherState.PolicyRuleUser
        FFIOtherState.RoomAliases -> OtherState.RoomAliases
        FFIOtherState.RoomCanonicalAlias -> OtherState.RoomCanonicalAlias
        FFIOtherState.RoomCreate -> OtherState.RoomCreate
        FFIOtherState.RoomEncryption -> OtherState.RoomEncryption
        FFIOtherState.RoomGuestAccess -> OtherState.RoomGuestAccess
        FFIOtherState.RoomHistoryVisibility -> OtherState.RoomHistoryVisibility
        FFIOtherState.RoomJoinRules -> OtherState.RoomJoinRules
        FFIOtherState.RoomPinnedEvents -> OtherState.RoomPinnedEvents
        FFIOtherState.RoomPowerLevels -> OtherState.RoomPowerLevels
        FFIOtherState.RoomServerAcl -> OtherState.RoomServerAcl
        FFIOtherState.RoomTombstone -> OtherState.RoomTombstone
        FFIOtherState.SpaceChild -> OtherState.SpaceChild
        FFIOtherState.SpaceParent -> OtherState.SpaceParent
    }
}

/*
sealed interface EncryptedMessage {
    data class MegolmV1AesSha2(val sessionId: String) : EncryptedMessage
    data class OlmV1Curve25519AesSha2(val senderKey: String) : EncryptedMessage
    object Unknown : EncryptedMessage
}
*/

/*
enum class MembershipChange {
    NONE, ERROR, JOINED, LEFT, BANNED,
    UNBANNED, KICKED, INVITED, KICKED_AND_BANNED,
    INVITATION_ACCEPTED, INVITATION_REJECTED,
    INVITATION_REVOKED, KNOCKED, KNOCK_ACCEPTED,
    KNOCK_RETRACTED, KNOCK_DENIED, NOT_IMPLEMENTED;
}
*/
// enum class MessageFormat { HTML, UNKNOWN; }

/*
data class ThumbnailInfo(
    val height: ULong?,
    val width: ULong?,
    val mimetype: String?,
    val size: ULong?,
)
*/

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
interface Message {
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

fun ProfileDetails.displayName(): String? {
    return when (this) {
        is ProfileDetails.Error -> this.message
        is ProfileDetails.Ready -> this.displayName
        else -> null
    }
}

@Immutable
sealed interface TimelineItemVariant {
    fun contentType(): Int = when (this) {
        is Event -> 0
        is Virtual -> 1
        Unknown -> 2
    }

    fun id(): Int = when (this) {
        is Event -> id
        is Virtual -> id
        Unknown -> TODO("We should never end up here")
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
        val senderProfile: ProfileDetails,
        val timestamp: ULong,
        val message: Message,
        val groupedByUser: Boolean,
    ) : TimelineItemVariant

    @Immutable
    data class Virtual(val id: Int, val virtual: VirtualTimelineItem) : TimelineItemVariant

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

private fun EventTimelineItem.marshal(lastUserSeen: String?): TimelineItemVariant.Event {
    val sender = this.sender()
    val continuous = lastUserSeen?.equals(sender) ?: false
    return TimelineItemVariant.Event(
        id = (0..Int.MAX_VALUE).random(),
        uniqueIdentifier = this.uniqueIdentifier(),
        eventId = this.eventId(),
        isEditable = this.isEditable(),
        isLocal = this.isLocal(),
        isOwn = this.isOwn(),
        isRemote = this.isRemote(),
        localSendState = this.localSendState()?.marshal(),
        reactions = this.reactions()?.map { Reaction(it.key, it.count) }.orEmpty(),
        sender = sender,
        senderProfile = this.senderProfile().marshal(),
        timestamp = this.timestamp(),
        message = this.content().use { it.marshal() },
        groupedByUser = continuous
    )
}

fun FFIMessage.marshal(): Message {
    return when (val msgType = msgtype()!!) {
        is MessageType.Audio -> Message.Audio(body = body(),
            inReplyTo = inReplyTo()?.use { it.marshal() },
            isEdited = isEdited(),
            info = msgType.content.info,
            source = msgType.content.source.use { it.url() })

        is MessageType.Emote -> Message.Emote(
            body = body(),
            inReplyTo = inReplyTo()?.use { it.marshal() },
            isEdited = isEdited(),
            formatted = msgType.content.formatted?.copy()
        )

        is MessageType.File -> Message.File(body = body(),
            inReplyTo = inReplyTo()?.use { it.marshal() },
            isEdited = isEdited(),
            info = msgType.content.info?.marshal(),
            source = msgType.content.source.use { it.url() })

        is MessageType.Image -> Message.Image(body = body(),
            inReplyTo = inReplyTo()?.use { it.marshal() },
            isEdited = isEdited(),
            info = msgType.content.info?.marshal(),
            source = msgType.content.source.use { it.url() })

        is MessageType.Notice -> Message.Notice(
            body = body(),
            inReplyTo = inReplyTo()?.use { it.marshal() },
            isEdited = isEdited(),
            formatted = msgType.content.formatted?.copy()
        )

        is MessageType.Text -> Message.Text(
            body = body(),
            inReplyTo = inReplyTo()?.use { it.marshal() },
            isEdited = isEdited(),
            formatted = msgType.content.formatted?.copy()
        )

        is MessageType.Video -> Message.Video(body = body(),
            inReplyTo = inReplyTo()?.use { it.marshal() },
            isEdited = isEdited(),
            info = msgType.content.info?.marshal(),
            source = msgType.content.source.use { it.url() })
    }
}

private fun TimelineItemContent.marshal(): Message {
    return when (val kind = this.kind()) {
        is TimelineItemContentKind.FailedToParseMessageLike -> Message.FailedToParseMessageLike(
            eventType = kind.eventType, error = kind.error
        )

        is TimelineItemContentKind.FailedToParseState -> Message.FailedToParseState(
            eventType = kind.eventType, error = kind.error, stateKey = kind.stateKey
        )

        is TimelineItemContentKind.ProfileChange -> Message.ProfileChange(
            displayName = kind.displayName,
            prevDisplayName = kind.prevDisplayName,
            avatarUrl = kind.avatarUrl,
            prevAvatarUrl = kind.prevAvatarUrl
        )

        is TimelineItemContentKind.RoomMembership -> Message.RoomMembership(
            userId = kind.userId, change = kind.change
        )

        is TimelineItemContentKind.State -> Message.State(
            stateKey = kind.stateKey, content = kind.content.marshal()
        )

        is TimelineItemContentKind.Sticker -> Message.Sticker(
            body = kind.body, info = kind.info.marshal(), url = kind.url
        )

        is TimelineItemContentKind.UnableToDecrypt -> Message.UnableToDecrypt(msg = kind.msg)
        TimelineItemContentKind.Message -> {
            val message = this.asMessage()!!
            return message.marshal()
        }

        TimelineItemContentKind.RedactedMessage -> Message.RedactedMessage
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
    private val slidingSyncRoom: SlidingSyncRoom,
    private val coroutineScope: CoroutineScope,
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

    init {
        val roomSubscription =
            RoomSubscription(timelineLimit = null, requiredState = listOf(RequiredState("*", "*")))
        var senderId: String? = null
        coroutineScope.launch {
            val result = slidingSyncRoom.subscribeAndAddTimelineListener(
                this@TimelineState, roomSubscription
            )
            val initList = result.items.map { ti ->
                val item = takeMap(ti, lastUserSeen = senderId)
                senderId = item.sender()
                item
            }.toList()
            timelineItems.value = initList
            taskHandle = result.taskHandle
            initialized.value = true
        }
    }

    fun isInit(): Flow<Boolean> {
        return isInitialized.map { it }
    }

    override fun onUpdate(update: TimelineDiff) {
        coroutineScope.launch {
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
        this.coroutineScope.launch(this.diffApplyDispatcher) {
            Log.i("Timeline", "requestMore")
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
    ): TimelineItemVariant = item.use {

        it.asEvent()?.marshal(lastUserSeen)?.let { i -> return i }

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
        // apply diff
        when (diff.change()) {
            TimelineChange.APPEND -> {
                val append =
                    diff.append()?.map { takeMap(it, this.lastOrNull()?.sender()) }.orEmpty()
                addAll(append)
            }

            TimelineChange.CLEAR -> clear()
            TimelineChange.INSERT -> diff.insert()?.use {
                add(it.index.toInt(), takeMap(it.item, null))
            }

            TimelineChange.SET -> diff.set()?.use {
                val idx = it.index.toInt()
                set(idx, takeMap(it.item, this.getOrNull(idx - 1)?.sender()))
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
                add(takeMap(it, lastOrNull()?.sender()))
            }

            TimelineChange.PUSH_FRONT -> diff.pushFront()?.use {
                val item = takeMap(it, null)
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
                    val item = takeMap(it, senderId)
                    senderId = item.sender()
                    item
                })
            }
        }
    }

    fun dispose() {
        Log.d("Timeline", "Disposing of timeline")
        taskHandle.use { it.cancel() }
        coroutineScope.cancel()
    }

    fun sendMessage(msg: String) {
        this.coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                val id = genTransactionId()
                val msgAsMarkdown = messageEventContentFromMarkdown(msg)
                slidingSyncRoom.fullRoom()?.use {
                    it.send(msgAsMarkdown, id)
                }
            }.onFailure {
                Log.d("Timeline", "Send message fail")
            }.onSuccess {
                Log.i("Timeline", "Send message succeeded")
            }
        }
    }
}