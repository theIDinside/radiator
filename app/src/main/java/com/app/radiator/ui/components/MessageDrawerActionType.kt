package com.app.radiator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.radiator.ui.components.general.CenteredRow

interface MessageDrawerContentInterface<Action> {
  fun actions(): List<MessageDrawerAction<Action>>
}
@Immutable
data class MessageDrawerAction<E>(
  val text: String,
  val icon: ImageVector,
  val desc: String,
  val action: E,
)

val messageDrawerActionFontSize = 20.sp

@Composable
fun<Action> MessageDrawerContent(drawerInterface: MessageDrawerContentInterface<Action>, eventSink: (Action) -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(color = Color(45, 45, 55))
      .border(color = Color(45, 45, 45), width = 4.dp, shape = RoundedCornerShape(5.dp))
      .padding(start = 5.dp, top = 5.dp, bottom = 5.dp)
  ) {
    for (action in drawerInterface.actions()) {
      CenteredRow(
        modifier = Modifier
          .padding(bottom = 5.dp)
          .clickable {
            eventSink(action.action)
          }
      ) {
        Icon(imageVector = action.icon, contentDescription = action.desc)
        Text(
          text = action.text,
          modifier = Modifier.padding(start = 10.dp),
          color = Color.LightGray,
          fontSize = messageDrawerActionFontSize
        )
      }
    }
  }
}