package com.app.radiator.ui.routes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.app.radiator.matrix.MatrixClient
import com.app.radiator.ui.components.RoomSummary
import com.app.radiator.ui.components.RoomSummaryRow
import kotlinx.collections.immutable.ImmutableCollection
import org.matrix.rustcomponents.sdk.SlidingSyncRoom

@Composable
fun RoomList(navController: NavHostController, roomList: ImmutableCollection<RoomSummary>, onClick: (RoomSummary) -> Unit = {}) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        roomList.forEach {
            RoomSummaryRow(room = it, onClick = onClick)
            Spacer(
                modifier = Modifier
                    .height(5.dp)
                    .border(BorderStroke(width = 1.dp, Color.Green))
            )
        }
    }
}