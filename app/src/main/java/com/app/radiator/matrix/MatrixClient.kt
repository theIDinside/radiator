package com.app.radiator.matrix

import android.util.Log
import androidx.compose.ui.text.AnnotatedString
import com.app.radiator.*
import com.app.radiator.matrix.timeline.TimelineState
import com.app.radiator.matrix.timeline.displayName
import com.app.radiator.matrix.timeline.marshal
import com.app.radiator.ui.components.RoomSummary
import com.app.radiator.ui.components.avatarData
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.matrix.rustcomponents.sdk.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeLines
import kotlin.reflect.full.memberProperties

typealias RoomSummaryMap = HashMap<String, RoomSummary>

private fun defaultDispatchers(): CoroutineDispatchers {
  return CoroutineDispatchers(
    io = Dispatchers.IO,
    computation = Dispatchers.Default,
    main = Dispatchers.Main,
    diffUpdateDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
  )
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
  val sessionFileName = "session"
  val file = SystemInterface.getApplicationFilePath(sessionFileName)
  val serialized = serializeSession(session = session)
  file.writeLines(serialized)
  println("Wrote: ${serializeSession(session = session).joinToString { "" }} to $sessionFileName")
}

class UnexpectedFormat(additionalInfo: String = "") :
  Exception("File contained unexpected format. $additionalInfo") {}

fun deserializeSession(): Session? {
  val sessionFileName = "session"
  val file = SystemInterface.getApplicationFilePath(sessionFileName)
  if (!file.exists())
    return null
  val lines = file.readLines()
  if (lines.size != 6) throw UnexpectedFormat("Did not contain 6 lines")
  val values = Session::class.memberProperties.associate { prop ->
    val value =
      if (lines[serializeFormat[prop.name]!!] == "null") null else lines[serializeFormat[prop.name]!!]
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
    if (atomicHasStarted.compareAndSet(false, true)) {
      taskHandle = slidingSync.sync()
    }
  }

  fun stop() {
    if (atomicHasStarted.compareAndSet(true, false)) {
      taskHandle.use {
        it.cancel()
      }
    }
  }
}

// Override the SlidingSyncObserver interface, so that we can communicate the updates
// in arbitrary ways (that suits us)
interface SummaryFlow : SlidingSyncObserver {
  fun summaryFlow(): StateFlow<List<RoomSummary>>

  fun setDebugLogger(logger: (UpdateSummary) -> Unit)
}


typealias RoomId = String

interface SlidingSyncRoomManager : Disposable {
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

  fun getRoomSummary(roomId: String): RoomSummary
  fun getSummariesOf(rooms: List<RoomId>): List<RoomSummary>
  fun getAllSummaries(): List<RoomSummary>

  fun getSlidingSyncRoom(roomId: String): SlidingSyncRoom

  fun disposeOfTimelineState()
  fun getTimelineState(roomId: String): TimelineState
}

// Make _all_ wrapped types public. Everything else, is fucking stupid.
class MatrixClient constructor(val dispatchers: CoroutineDispatchers = defaultDispatchers()) {

  private val slidingSyncListCoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.io)
  private val slidingSyncState = MutableStateFlow(SlidingSyncState.NOT_LOADED)
  private lateinit var sessionVerification: SessionVerification
  var hadSession = false

  init {
    val authenticationService = AuthenticationService(
      SystemInterface.applicationDataDir(),
      null,
      null
    )
    val path = SystemInterface.getApplicationFilePath("session")
    if (path.exists()) {
      val session = deserializeSession()
      if (session != null) {
        runBlocking {
          slidingSyncListCoroutineScope.launch {
            try {
              authenticationService.configureHomeserver(
                homeServer
              )
            } catch (e: AuthenticationException) {
              authenticationService.configureHomeserver(
                session.slidingSyncProxy!!
              )
            }
            this@MatrixClient.restoreSession(
              authService = authenticationService,
              session = session
            )
            hadSession = true
          }
        }
      }
    }
  }

  /// Companion object manages the SlidingSync rooms that we've been sent so far
  /// It is also responsible for producing the final data output that is given to
  /// jetpack compose to render in the form of `RoomSummary`
  val slidingSyncRoomManager = object : SlidingSyncRoomManager {
    val slidingSyncRoomsMap = HashMap<String, SlidingSyncRoom>()
    val slidingSyncRoomList = ArrayList<RoomId>()
    var timelineState: TimelineState? = null

    private fun list() = slidingSyncRoomList

    override fun destroy() {
      slidingSyncRoomsMap.values.forEach {
        it.destroy()
      }
      slidingSyncRoomsMap.clear()
    }

    override fun pushFront(roomId: RoomId) {}
    override fun pushBack(roomId: RoomId) {}
    override fun popBack() {}
    override fun popFront() {}
    override fun removeRoom(roomId: RoomId) {}
    override fun removeRoomAtIndex(index: Int) {}
    override fun insert(index: Int, roomId: RoomId) {}
    override fun set(index: Int, roomId: RoomId) {}
    override fun clear() {}
    override fun reset(roomIds: List<RoomId>) {}

    override fun getSlidingSyncRoom(roomId: String): SlidingSyncRoom {
      var room = slidingSyncRoomsMap[roomId]
      if (room == null) {
        room = slidingSync().getRoom(roomId)!!
        slidingSyncRoomsMap[roomId] = room
      }
      return room
    }

    override fun disposeOfTimelineState() {
      timelineState?.dispose()
      timelineState = null
    }

    override fun getTimelineState(roomId: String): TimelineState {
      if(timelineState?.roomId == roomId) return timelineState!!
      if(timelineState != null) throw Exception("Previously cached timeline state not diposed of")

      timelineState = TimelineState(
        roomId= roomId,
        slidingSyncRoomsMap[roomId]!!,
        coroutineScope = CoroutineScope(SupervisorJob() + dispatchers.io),
        diffApplyDispatcher = dispatchers.diffUpdateDispatcher
      )
      return timelineState!!
    }


    override fun getRoomSummary(roomId: String): RoomSummary {
      if (slidingSyncRoomsMap[roomId] == null) {
        slidingSyncRoomsMap[roomId] = slidingSync().getRoom(roomId)!!
      }

      val ss = slidingSyncRoomsMap[roomId]!!
      val avatarData = avatarData(ss)
      val latestRoomMessage = ss.latestRoomMessage()?.use {
        // TODO(implement extracting actual text message, for now just debug print it)

        when (val kind = it.content().kind()) {
          is TimelineItemContentKind.FailedToParseMessageLike -> kind.error
          is TimelineItemContentKind.FailedToParseState -> kind.error
          is TimelineItemContentKind.Message -> it.content().asMessage()?.use { msg ->
            when (val type = msg.msgtype()) {
              is MessageType.Audio -> type.content.body
              is MessageType.Emote -> type.content.body
              is MessageType.File -> type.content.body
              is MessageType.Image -> {
                it.senderProfile().marshal().displayName()?.let { userName ->
                  "$userName sent ${type.content.body}"
                } ?: type.content.body
              }

              is MessageType.Notice -> type.content.body
              is MessageType.Text -> type.content.body
              is MessageType.Video -> type.content.body
              null -> TODO()
            }
          }

          is TimelineItemContentKind.ProfileChange -> "profile change by foo"
          is TimelineItemContentKind.RedactedMessage -> kind.toString()
          is TimelineItemContentKind.RoomMembership -> "${kind.userId} changed user id"
          is TimelineItemContentKind.State -> "state foo $kind.stateKey"
          is TimelineItemContentKind.Sticker -> "sticked for $kind.url"
          is TimelineItemContentKind.UnableToDecrypt -> "Unable to decrypt: ${kind.msg}"
        }
      }
      return RoomSummary(
        id = 0,
        roomId = roomId,
        name = ss.name() ?: "Unknown name",
        hasUnread = ss.unreadNotifications().use
        { it.notificationCount() != 0u },
        timestamp = null,
        lastMessage = AnnotatedString(latestRoomMessage ?: "Could not retrieve message"),
        avatarData = avatarData,
        isPlaceholder = false
      )
    }

    override fun getSummariesOf(rooms: List<RoomId>): List<RoomSummary> =
      rooms.map { getRoomSummary(it) }.toList()

    override fun getAllSummaries(): List<RoomSummary> =
      list().map { getRoomSummary(it) }.toList()
  }

  val slidingSyncListener = object : SummaryFlow {
    var eventDebugLogger: (UpdateSummary) -> Unit = {}
    private val roomMap = RoomSummaryMap()
    private val summaryEmitter =
      MutableStateFlow<List<RoomSummary>>(emptyList())
    val updateSummaryFlow: StateFlow<List<RoomSummary>> = summaryEmitter.asStateFlow()

    override fun summaryFlow(): StateFlow<List<RoomSummary>> {
      return updateSummaryFlow
    }

    override fun setDebugLogger(logger: (UpdateSummary) -> Unit) {
      eventDebugLogger = logger
    }

    override fun didReceiveSyncUpdate(summary: UpdateSummary) {
      eventDebugLogger(summary)
      // N.B. summary emitter blocks this coroutine until _all_ subscribers have been notified.
      slidingSyncListCoroutineScope.launch {
        withContext(dispatchers.io) {
          val summaries =
            summary.rooms.map { slidingSyncRoomManager.getRoomSummary(it) }.toList()
          for (room in summaries) {
            roomMap[room.roomId] = room
          }
          summaryEmitter.value = roomMap.values.toImmutableList()
          summaryEmitter.emit(summaryEmitter.value)
        }
      }
    }
  }

  lateinit var client: Client
  lateinit var slidingSyncJobManager: SlidingSyncJobManager
  lateinit var slidingSyncList: SlidingSyncList

  var sessionDataFlow = MutableStateFlow<Session?>(null)
  var syncPacketCount: Int = 0

  private fun slidingSync(): SlidingSync {
    return slidingSyncJobManager.slidingSync
  }

  private val slidingSyncListRoomListObserver = object : SlidingSyncListRoomListObserver {
    private var slidingSyncEventDebugLogger: (SlidingSyncListRoomsListDiff) -> Unit = {}

    fun setDebugLogger(logger: (SlidingSyncListRoomsListDiff) -> Unit) {
      slidingSyncEventDebugLogger = logger
    }

    private fun handleRoomListEntry(entry: RoomListEntry, action: (roomId: String?) -> Unit) {
      when (entry) {
        RoomListEntry.Empty -> action(null)
        is RoomListEntry.Filled -> action(entry.roomId)
        is RoomListEntry.Invalidated -> action(entry.roomId)
      }
    }

    fun RoomListEntry.getRoomId(): String? = when (this) {
      RoomListEntry.Empty -> null
      is RoomListEntry.Filled -> this.roomId
      is RoomListEntry.Invalidated -> this.roomId
    }

    override fun didReceiveUpdate(diff: SlidingSyncListRoomsListDiff) {
      slidingSyncEventDebugLogger(diff)
      when (diff) {
        is SlidingSyncListRoomsListDiff.Append -> {
          for (roomDiff in diff.values) {
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

  val slidingSyncStateObserver = object : SlidingSyncListStateObserver {
    override fun didReceiveUpdate(newState: SlidingSyncState) {
      slidingSyncState.value = newState
    }
  }


  suspend fun restoreSession(authService: AuthenticationService, session: Session) {
    val ref = this
    withContext(Dispatchers.Main) {
      runCatching {
        client = authService.restoreWithAccessToken(
          token = session.accessToken,
          deviceId = session.deviceId
        )
        client.setDelegate(ClientErrorHandler())
        sessionVerification = SessionVerification(ref)
        sessionVerification.requestVerification()
        slidingSyncJobManager = initSlidingSync()
      }.onSuccess {
        val newSessionState = client.session()
        saveSerializedSession(newSessionState)
        sessionDataFlow.value = newSessionState
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

  private fun initSlidingSync(): SlidingSyncJobManager {
    if (::slidingSyncJobManager.isInitialized)
      return slidingSyncJobManager
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
      .syncMode(mode = SlidingSyncMode.SELECTIVE)
      .addRange(0u, 20u)
      .use {
        it.build()
      }


    slidingSyncList.observeRoomList(slidingSyncListRoomListObserver)
    slidingSyncListRoomListObserver.setDebugLogger {
      Log.d("SlidingSyncDiff", "SlidingSyncListRoomsListDiff update $it")
    }

    slidingSyncList.observeState(slidingSyncStateObserver)
    val slidingSync = client
      .slidingSync()
      .homeserver("https://slidingsync.lab.matrix.org")
      .withAllExtensions()
      // .withCommonExtensions()
      .storageKey("syncStorage")
      .addList(slidingSyncList)
      .use {
        it.build()
      }
    slidingSyncListener.setDebugLogger { summary ->
      Log.d(
        "Sliding Sync",
        "SyncUpdate #$syncPacketCount: $summary in scope $slidingSyncListCoroutineScope"
      )
      syncPacketCount += 1
    }
    slidingSync.setObserver(slidingSyncListener)
    return SlidingSyncJobManager(slidingSync = slidingSync)
  }

  fun isLoggedIn(): Flow<Boolean> {
    return sessionDataFlow.map { it != null }
  }
}