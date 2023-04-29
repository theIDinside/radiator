package com.app.radiator.ui.components.general

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun CenteredRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    content()
  }
}