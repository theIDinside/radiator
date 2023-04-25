package com.app.radiator.ui.routes

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.matrix.rustcomponents.sdk.SlidingSyncRoom

@Preview
@Composable
fun PreviewRoomDetails() {

}

@Composable
fun DisplayRoomDetails() {

}

@Composable
fun RoomDetails(room: SlidingSyncRoom) {
  Column {
    Text(text = room.roomId())
  }
}