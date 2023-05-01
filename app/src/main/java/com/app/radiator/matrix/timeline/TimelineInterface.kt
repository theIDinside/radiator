package com.app.radiator.matrix.timeline

import com.app.radiator.ui.components.AvatarData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.TimelineListener

// Interface for performance testing different implementations of our timeline
interface ITimeline : TimelineListener {
  fun roomId() : String

  fun subscribeTimeline(): StateFlow<ImmutableList<IEvent>>
  fun timelineHasThread(eventId: String): Boolean

  fun threadItemFlow(threadId: String): StateFlow<List<IEvent>>
  fun isInit(): Flow<Boolean>

  suspend fun withLockOnState(block: () -> Unit)
  fun patchDiff(diff: TimelineDiff)

  /// Updates
  fun updateCanRequestMoreState(virtualItem: VirtualTimelineItem?)
  fun canUpdateStateProducer(): StateFlow<CanRequestMoreState>
  fun requestMore()
  fun dispose()

  fun sendMessage(id: String, msg: String)
  fun sendReply(id: String, reply: TimelineAction.Reply)
  fun sendReaction(reaction: TimelineAction.React)
  fun editMessage(id: String, edit: TimelineAction.Edit)
  fun timelineSend(action: TimelineAction)
  fun avatar(): AvatarData
}