package com.app.radiator.matrix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import com.app.radiator.ClientErrorHandler
import com.app.radiator.CoroutineDispatchers
import com.app.radiator.logError
import com.app.radiator.ui.components.RoomListRoomSummary
import com.app.radiator.ui.components.avatarData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.matrix.rustcomponents.sdk.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeLines
import kotlin.reflect.full.memberProperties

private fun defaultDispatchers(): CoroutineDispatchers {
    return CoroutineDispatchers(
        io = Dispatchers.IO,
        computation = Dispatchers.Default,
        main = Dispatchers.Main,
        diffUpdateDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    )
}

fun convertUserIdToFileName(userId: String): String {
    var endPos = userId.indexOfLast { it == ':' }
    if(endPos == -1) endPos = userId.length
    return userId.substring(1 .. endPos)
}

class SerializedSession(private val data: String) : Iterable<String> {
    override fun iterator(): Iterator<String> = data.lines().map { it.trim() }.iterator()
}

// todo(improvement): serialize to a better format than a simple "TOML"-like format
private fun serializeSession(session: Session): SerializedSession {
    val data =
"""${session.userId}
${session.homeserverUrl}
${session.deviceId}
${session.accessToken}
${session.refreshToken}
${session.slidingSyncProxy}""".trimMargin()
    return SerializedSession(data)
}

val serializeFormat: Map<String, Int> = listOf(
    Pair("userId", 0),
    Pair("homeserverUrl", 1),
    Pair("deviceId", 2),
    Pair("accessToken", 3),
    Pair("refreshToken", 4),
    Pair("slidingSyncProxy", 5),
).toMap()

private fun saveSerializedSession(session: Session) {
    val sanitizeFileName = convertUserIdToFileName(session.userId)
    val file = SystemInterface.getApplicationFilePath(sanitizeFileName)
    val serialized = serializeSession(session=session)
    file.writeLines(serialized)
    println("Wrote: ${serializeSession(session=session).joinToString { "" }} to $sanitizeFileName")
}

class UnexpectedFormat(additionalInfo: String = "") : Exception("File contained unexpected format. $additionalInfo") {}

fun deserializeSession(userId: String): Session? {
    val sanitizeFileName = convertUserIdToFileName(userId)
    val file = SystemInterface.getApplicationFilePath("$sanitizeFileName")
    if (!file.exists())
        return null
    val lines = file.readLines()
    if (lines.size != 6) throw UnexpectedFormat("Did not contain 6 lines")
    val values = Session::class.memberProperties.associate { prop ->
        val value = if(lines[serializeFormat[prop.name]!!] == "null") null else lines[serializeFormat[prop.name]!!]
        Pair(prop.name, value)
    }
    return Session(
        deviceId = values["deviceId"]!!,
        accessToken = values["accessToken"]!!,
        refreshToken = values["refreshToken"],
        homeserverUrl = values["homeserverUrl"]!!,
        slidingSyncProxy = values["slidingSyncProxy"],
        userId = values["userId"]!!
    )
}

data class SlidingSyncJobManager(val slidingSync: SlidingSync) {
    private val atomicHasStarted = AtomicBoolean(true)
    private var taskHandle = slidingSync.sync()

    fun start() {
        if(atomicHasStarted.compareAndSet(false, true)) {
           taskHandle = slidingSync.sync()
        }
    }

    fun stop() {
        if(atomicHasStarted.compareAndSet(true, false)) {
            taskHandle.use {
                it.cancel()
            }
        }
    }
}

// Override the SlidingSyncObserver interface, so that we can communicate the updates
// in arbitrary ways (that suits us)
interface SummaryFlow : SlidingSyncObserver {
    fun summaryFlow(): SharedFlow<List<RoomListRoomSummary>>

    fun setDebugLogger(logger: () -> Unit)
}


typealias RoomId = String;

interface SlidingSyncRoomManager {
    fun pushFront(roomId: RoomId)
    fun pushBack(roomId: RoomId)
    fun popBack()
    fun popFront()
    fun removeRoom(roomId: RoomId)
    fun removeRoomAtIndex(index: Int)
    fun insert(index: Int, roomId: RoomId)
    fun set(index: Int, roomId: RoomId)

    fun clear()
    fun reset(roomIds: List<RoomId>)

    fun getRoomSummary(roomId: String): RoomListRoomSummary
    fun getRoomWithIndexSummary(index: Int): RoomListRoomSummary

    fun getSummariesByIndex(indices: List<Int>): List<RoomListRoomSummary>
    fun getSummariesOf(indices: List<RoomId>): List<RoomListRoomSummary>
    fun getAllSummaries(): List<RoomListRoomSummary>
}

// Make _all_ wrapped types public. Everything else, is fucking stupid.
class MatrixClient constructor(val dispatchers: CoroutineDispatchers = defaultDispatchers()) {

    val slidingSyncRoomManager = object : SlidingSyncRoomManager {
        val slidingSyncRoomsMap = HashMap<String, SlidingSyncRoom>()
        val slidingSyncRoomList = ArrayList<RoomId>()

        private fun list() = slidingSyncRoomList
        private fun map() = slidingSyncRoomsMap

        override fun pushFront(roomId: RoomId) {
            insert(0, roomId)
        }

        override fun pushBack(roomId: RoomId) {
            val room = slidingSync().getRoom(roomId)!!
            slidingSyncRoomList.add(roomId)
            slidingSyncRoomsMap[roomId] = room
        }

        override fun popBack() {
            val sz = list().size
            val roomId = list().removeAt(sz - 1)
            map().remove(roomId)?.destroy()
        }

        override fun popFront() {
            val roomId = list().removeAt(0)
            map().remove(roomId)?.destroy()
        }

        override fun removeRoom(roomId: RoomId) {
            map().remove(roomId)?.destroy()
            list().removeIf { it == roomId }
        }

        override fun removeRoomAtIndex(index: Int) {
            val roomId = list()[index]
            map().remove(roomId)?.destroy()
        }

        override fun insert(index: Int, roomId: RoomId) {
            val room = slidingSync().getRoom(roomId)!!
            list().add(index, roomId)
            map()[roomId] = room
        }

        override fun set(index: Int, roomId: RoomId) {
            val roomIdToBeRemoved = list()[index]
            map().remove(roomIdToBeRemoved)?.destroy()

            list()[index] = roomId
            val room = slidingSync().getRoom(roomId)!!
            map()[roomId] = room
        }

        override fun clear() {
            map().values.forEach { it.destroy() }
            map().clear()
            list().clear()
        }

        override fun reset(roomIds: List<RoomId>) {
            clear()
            for(roomId in roomIds) {
                pushBack(roomId)
            }
        }

        override fun getRoomSummary(roomId: String): RoomListRoomSummary {
            if(map()[roomId] == null) {
                map()[roomId] = slidingSync().getRoom(roomId)!!
            }
            val ss = map()[roomId]!!
            val room = ss.fullRoom()!!
            val name = ss.name()

            val avatarData = avatarData(ss, room)
            val latestRoomMessage = ss.latestRoomMessage()?.use {
                // TODO(implement extracting actual text message, for now just debug print it)
                when(val kind = it.content().kind()) {
                    is TimelineItemContentKind.FailedToParseMessageLike -> kind.error
                    is TimelineItemContentKind.FailedToParseState -> kind.error
                    is TimelineItemContentKind.Message -> it.content().asMessage()?.use { msg -> msg.body() }
                    is TimelineItemContentKind.ProfileChange -> "profile change by foo"
                    is TimelineItemContentKind.RedactedMessage -> kind.toString()
                    is TimelineItemContentKind.RoomMembership -> "${kind.userId} changed user id"
                    is TimelineItemContentKind.State -> "state foo $kind.stateKey"
                    is TimelineItemContentKind.Sticker -> "sticked for $kind.url"
                    is TimelineItemContentKind.UnableToDecrypt -> "Unable to decrypt: ${kind.msg.toString()}"
                }
            }
            return RoomListRoomSummary(
                id = room.id(),
                roomId = room.id(),
                name = ss.name() ?: "Unknown name",
                hasUnread = ss.unreadNotifications().use { it.notificationCount() != 0u },
                timestamp = null,
                lastMessage = AnnotatedString(latestRoomMessage ?: "Could not retrieve message"),
                avatarData = avatarData,
                isPlaceholder = false
            )
        }

        override fun getRoomWithIndexSummary(index: Int): RoomListRoomSummary {
            val roomId = list()[index]
            return getRoomSummary(roomId)
        }

        override fun getSummariesByIndex(indices: List<Int>): List<RoomListRoomSummary> =
            indices.map { getRoomWithIndexSummary(it) }.toList()

        override fun getSummariesOf(rooms: List<RoomId>): List<RoomListRoomSummary> =
            rooms.map { getRoomSummary(it) }.toList()

        override fun getAllSummaries(): List<RoomListRoomSummary> = list().map { getRoomSummary(it) }.toList()
    }

    val slidingSyncListener = object : SummaryFlow {
        private val coroutineScope: CoroutineScope = SystemInterface.appCoroutineScope()
        var eventListener: () -> Unit = {}
        private val extraBufferCapacity: Int = 128
        private val summaryEmitter = MutableSharedFlow<List<RoomListRoomSummary>>(extraBufferCapacity = extraBufferCapacity)
        val updateSummaryFlow: SharedFlow<List<RoomListRoomSummary>> = summaryEmitter.asSharedFlow()

        override fun summaryFlow(): SharedFlow<List<RoomListRoomSummary>> {
            return updateSummaryFlow
        }

        override fun setDebugLogger(logger: () -> Unit) {
            eventListener = logger
        }

        override fun didReceiveSyncUpdate(summary: UpdateSummary) {
            println("SyncUpdate (${System.identityHashCode(summary)}): $summary in scope $coroutineScope")
            // N.B. summary emitter blocks this coroutine until _all_ subscribers have been notified.
            coroutineScope.launch {
                val summaries = summary.rooms.map { slidingSyncRoomManager.getRoomSummary(it) }.toList()
                for(room in summary.rooms) {
                    roomListSummaries.addAll(summaries)
                }
                summaryEmitter.emit(summaries)
            }
        }
    }
    lateinit var client: Client
    lateinit var slidingSyncJobManager: SlidingSyncJobManager
    lateinit var slidingSyncList: SlidingSyncList
    lateinit var slidingSyncRooms: ArrayList<SlidingSyncRoom?>
    var roomListEntries = MutableStateFlow<List<RoomListEntry>>(ArrayList())
    val slidingSyncRoomsMap = HashMap<String, SlidingSyncRoom>()

    val roomSummaries = MutableStateFlow(listOf<RoomListRoomSummary>())
    val roomListSummaries = mutableListOf<RoomListRoomSummary>()

    private var sessionDataFlow = MutableStateFlow<Session?>(null)
    var syncPacketCount: Int = 0

    private val slidingSyncListCoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val slidingSyncState = MutableStateFlow(SlidingSyncState.NOT_LOADED)

    private fun slidingSync(): SlidingSync {
        return slidingSyncJobManager.slidingSync
    }

    @Composable
    fun rememberGetRoomSummaries() = remember {
        roomListSummaries
    }

    private val slidingSyncListRoomListObserver = object : SlidingSyncListRoomListObserver {

        private fun handleRoomListEntry(entry: RoomListEntry, action: (roomId: String?) -> Unit) {
            when(entry) {
                RoomListEntry.Empty -> action(null)
                is RoomListEntry.Filled -> action(entry.roomId)
                is RoomListEntry.Invalidated -> action(entry.roomId)
            }
        }

        fun RoomListEntry.getRoomId(): String? = when(this) {
                RoomListEntry.Empty -> null
                is RoomListEntry.Filled -> this.roomId
                is RoomListEntry.Invalidated -> this.roomId
        }

        override fun didReceiveUpdate(diff: SlidingSyncListRoomsListDiff) {
            println("SlidingSyncListRoomsListDiff update $diff")
            when(diff){
                is SlidingSyncListRoomsListDiff.Append -> {
                    for(roomDiff in diff.values) {
                        handleRoomListEntry(roomDiff) { roomId ->
                            slidingSyncRoomManager.pushBack(roomId!!)
                        }
                    }
                }
                SlidingSyncListRoomsListDiff.Clear -> {
                    slidingSyncRoomManager.clear()
                }
                is SlidingSyncListRoomsListDiff.Insert -> {
                    handleRoomListEntry(diff.value) { roomId ->
                        slidingSyncRoomManager.insert(diff.index.toInt(), roomId!!)
                    }
                }
                SlidingSyncListRoomsListDiff.PopBack -> {
                    slidingSyncRoomManager.popBack()
                }
                SlidingSyncListRoomsListDiff.PopFront -> {
                    slidingSyncRoomManager.popFront()
                }
                is SlidingSyncListRoomsListDiff.PushBack -> {
                    handleRoomListEntry(diff.value) { roomId ->
                        slidingSyncRoomManager.pushBack(roomId!!)
                    }
                }
                is SlidingSyncListRoomsListDiff.PushFront -> {
                    handleRoomListEntry(diff.value) { roomId ->
                        slidingSyncRoomManager.pushFront(roomId!!)
                    }
                }
                is SlidingSyncListRoomsListDiff.Remove -> {
                    slidingSyncRoomManager.removeRoomAtIndex(diff.index.toInt())
                }
                is SlidingSyncListRoomsListDiff.Reset -> {
                    slidingSyncRoomManager.reset(diff.values.mapNotNull {
                        it.getRoomId()
                    }.toList())
                }
                is SlidingSyncListRoomsListDiff.Set -> {
                    handleRoomListEntry(diff.value) { roomId ->
                        slidingSyncRoomManager.set(diff.index.toInt(), roomId!!)
                    }
                }
            }
        }
    }

    fun roomList(): SharedFlow<List<RoomListRoomSummary>> {
        return roomSummaries.asSharedFlow()
    }

    val slidingSyncStateObserver = object : SlidingSyncListStateObserver {
        override fun didReceiveUpdate(newState: SlidingSyncState) {
            slidingSyncState.value = newState
        }
    }


    suspend fun restoreSession(authService: AuthenticationService, session: Session) {
        withContext(Dispatchers.IO) {
            runCatching {
                client = authService.restoreWithAccessToken(token=session.accessToken, deviceId = session.deviceId)
                client.setDelegate(ClientErrorHandler())
                slidingSyncJobManager = initSlidingSync()
            }.onSuccess {
                println("Logged in...")
                val session = client.session()
                saveSerializedSession(session)
                sessionDataFlow.value = session
            }.onFailure {
                logError("[LOG IN EXCEPTION]", it)
            }
        }
    }

    suspend fun login(authService: AuthenticationService, userName: String, password: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                client = authService.login(
                    username = userName,
                    password = password,
                    initialDeviceName = "radiator device",
                    deviceId = null
                )
                client.setDelegate(ClientErrorHandler())
                slidingSyncJobManager = initSlidingSync()
            }.onSuccess {
                println("Logged in...")
                val session = client.session()
                saveSerializedSession(session)
                sessionDataFlow.value = session
            }.onFailure {
                logError("[LOG IN EXCEPTION]", it)
            }
        }
    }

    private fun initSlidingSync() : SlidingSyncJobManager {
        val slidingSyncFilters =
            SlidingSyncRequestListFilters(
                isDm = null,
                spaces = emptyList(),
                isEncrypted = null,
                isInvite = false,
                isTombstoned = false,
                roomTypes = emptyList(),
                notRoomTypes = listOf("m.space"),
                roomNameLike = null,
                tags = emptyList(),
                notTags = emptyList()
            )

        slidingSyncList = SlidingSyncListBuilder()
            .timelineLimit(limit = 1u)
            .requiredState(
                requiredState = listOf(
                    RequiredState(key = "m.room.avatar", value = ""),
                    RequiredState(key = "m.room.encryption", value = ""),
                )
            )
            .filters(slidingSyncFilters)
            .name(name = "CurrentlyVisibleRooms")
            .sendUpdatesForItems(true)
            .syncMode(mode = SlidingSyncMode.SELECTIVE)
            .addRange(0u, 20u)
            .use {
                it.build()
            }


        slidingSyncList.observeRoomList(slidingSyncListRoomListObserver)

        slidingSyncList.observeState(slidingSyncStateObserver)
        val slidingSync = client
            .slidingSync()
            .homeserver("https://slidingsync.lab.matrix.org")
            .withCommonExtensions()
            .coldCache("radiator")
            .addList(slidingSyncList)
            .use {
                it.build()
            }
        slidingSyncListener.setDebugLogger {
            syncPacketCount += 1
            println("Sync Packets received $syncPacketCount")
        }
        slidingSync.setObserver(slidingSyncListener)
        return SlidingSyncJobManager(slidingSync = slidingSync)
    }

    fun isLoggedIn(): Flow<Boolean> {
        return sessionDataFlow.map { it != null }
    }
}