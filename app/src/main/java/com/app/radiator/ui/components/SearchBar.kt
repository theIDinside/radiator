package com.app.radiator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchBar(onSearch: (String) -> Unit, onDone: () -> Unit) {
  val (messageInput, setMessage) = remember { mutableStateOf("") }
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusRequester = remember { FocusRequester() }
  Row(
    modifier = Modifier.background(color = Color.White),
    verticalAlignment = Alignment.CenterVertically
  ) {
    OutlinedTextField(
      modifier = Modifier
        .fillMaxWidth()
        .focusTarget()
        .weight(0.5f)
        .focusRequester(focusRequester),
      value = messageInput,
      colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White
      ),
      textStyle = TextStyle(fontSize = 14.sp),
      onValueChange = setMessage,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions = KeyboardActions(
        onSend = {
          onSearch(messageInput)
        }, onDone = {
          keyboardController?.hide()
          onDone()
        }, onNext = {

        }),
      label = { Text("Search for...") },
      trailingIcon = {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Exit search mode",
          modifier = Modifier.clickable {
            onDone()
          })
      }
    )
  }
  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }
}