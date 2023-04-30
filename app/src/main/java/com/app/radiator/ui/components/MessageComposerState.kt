package com.app.radiator.ui.components

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.app.radiator.R
import com.app.radiator.matrix.timeline.Message
import com.app.radiator.matrix.timeline.TimelineAction
import com.app.radiator.matrix.timeline.TimelineItemVariant
import com.app.radiator.matrix.timeline.TimelineState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Immutable
sealed interface ComposerAction {
  @Immutable
  object Message : ComposerAction

  @Immutable
  sealed class MessageWithContext(open val eventId: String) : ComposerAction

  @Immutable
  data class Reply(override val eventId: String) : MessageWithContext(eventId)

  @Immutable
  data class ThreadReply(override val eventId: String) : MessageWithContext(eventId)

  @Immutable
  data class React(override val eventId: String) : MessageWithContext(eventId)

  @Immutable
  data class Edit(override val eventId: String) : MessageWithContext(eventId)
}

@Immutable
sealed interface ComposerState {
  @Immutable
  object NewMessage : ComposerState

  @Immutable
  data class Reply(val item: TimelineItemVariant.Event) : ComposerState

  @Immutable
  data class React(val item: TimelineItemVariant.Event) : ComposerState

  @Immutable
  data class Edit(val item: TimelineItemVariant.Event) : ComposerState

  @Immutable
  data class ThreadReply(val item: TimelineItemVariant.Event) : ComposerState

  fun timelineItem(): TimelineItemVariant.Event? {
    return when (this) {
      NewMessage -> null
      is Edit -> item
      is React -> item
      is Reply -> item
      is ThreadReply -> item
    }
  }
}

class MessageComposerState(
  private val composerCoroutineScope: CoroutineScope,
  private val timeline: TimelineState,
) {
  val flow = MutableStateFlow<ComposerState>(ComposerState.NewMessage)
  private var msgContent = mutableStateOf("")
  private var actionToTake: ComposerAction = ComposerAction.Message

  fun updateText(data: String) {
    msgContent.value = data
  }

  fun composerText() = msgContent.value

  private fun asText(msg: Message): Message.Text? {
    return when (msg) {
      is Message.Text -> msg
      else -> null
    }
  }

  private fun setCurrentAction(action: ComposerAction) {
    actionToTake = action
  }

  fun setState(state: ComposerState) {
    when (state) {
      ComposerState.NewMessage -> {
        updateText("")
        setCurrentAction(ComposerAction.Message)
      }

      is ComposerState.Edit -> {
        asText(state.item.message)?.let { updateText(it.body) }
        setCurrentAction(ComposerAction.Edit(state.item.eventId!!))
      }

      is ComposerState.React -> {
        updateText("")
        setCurrentAction(ComposerAction.React(state.item.eventId!!))
      }

      is ComposerState.Reply -> {
        updateText("")
        setCurrentAction(ComposerAction.Reply(state.item.eventId!!))
      }

      is ComposerState.ThreadReply -> {
        updateText("")
        setCurrentAction(ComposerAction.ThreadReply(state.item.eventId!!))
      }
    }
    composerCoroutineScope.launch {
      flow.emit(state)
    }
  }

  fun send() {
    val timelineAction = when (val act = actionToTake) {
      ComposerAction.Message -> TimelineAction.Message(this.msgContent.value)
      is ComposerAction.Edit -> TimelineAction.Edit(this.msgContent.value, act.eventId)
      is ComposerAction.React -> TimelineAction.Edit(this.msgContent.value, act.eventId)
      is ComposerAction.Reply -> TimelineAction.Reply(this.msgContent.value, act.eventId)
      is ComposerAction.ThreadReply -> TimelineAction.ThreadReply(
        this.msgContent.value, act.eventId
      )
    }
    timeline.send(timelineAction)
    setState(ComposerState.NewMessage)
  }
}

fun showAddOnToMessage(toastContext: Context, string: String) {
  Toast.makeText(toastContext, string, Toast.LENGTH_LONG).show()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DisplayComposerEditor(messageComposer: MessageComposerState) {
  val keyboardController = LocalSoftwareKeyboardController.current
  val mContext = LocalContext.current

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(color = Color.White), verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier, contentAlignment = Alignment.Center, propagateMinConstraints = true
    ) {
      Row(modifier = Modifier, horizontalArrangement = Arrangement.Center) {
        Spacer(modifier = Modifier.width(10.dp))
        Button(
          modifier = Modifier
            .background(color = Color.White)
            .size(35.dp)
            .offset(x = 2.5.dp),
          onClick = { showAddOnToMessage(mContext, "Add images, links, etc...") },
          colors = ButtonDefaults.buttonColors(
            containerColor = Color.LightGray,
            contentColor = Color.Black,
            disabledContainerColor = Color.Gray,
            disabledContentColor = Color.Gray
          ),
          contentPadding = PaddingValues(0.dp),
          shape = CircleShape
        ) {
          Icon(
            Icons.Default.Add,
            contentDescription = "content description",
            tint = MaterialTheme.colorScheme.primary
          )
        }
      }
    }
    Spacer(modifier = Modifier.width(15.dp))
    Box(modifier = Modifier.padding(top = 5.dp, bottom = 5.dp, end = 5.dp)) {
      OutlinedTextField(modifier = Modifier.fillMaxWidth(),
        maxLines = 10,
        value = messageComposer.composerText(),
        colors = TextFieldDefaults.colors(
          focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
        ),
        onValueChange = { value -> messageComposer.updateText(value) },
        keyboardOptions = KeyboardOptions(
          imeAction = ImeAction.Send, keyboardType = KeyboardType.Text
        ),
        keyboardActions = KeyboardActions(onSend = {
          messageComposer.send()
          keyboardController?.hide()
        }),
        label = { Text("Message...") })
    }
  }
}

@Composable
fun MessageComposer(messageComposer: MessageComposerState) {
  val item = messageComposer.flow.collectAsState().value.timelineItem()
  Column {
    if (item != null) {
      Box(
        modifier = Modifier
          .border(
            width = 4.dp, color = Color(94, 108, 121, 255), shape = RoundedCornerShape(5.dp)
          )
          .clip(RoundedCornerShape(5.dp))
          .padding(7.dp)
          .heightIn(0.dp, 300.dp)
      ) {
        RoomMessageItem(item = item)
        Icon(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .scale(0.5f)
            .clickable { messageComposer.setState(ComposerState.NewMessage) },
          imageVector = ImageVector.vectorResource(id = R.drawable.close_button_32),
          contentDescription = null,
          tint = Color.Red
        )
      }
      BackHandler(enabled = true) {
        messageComposer.setState(ComposerState.NewMessage)
      }
    }
    DisplayComposerEditor(messageComposer = messageComposer)
  }
}