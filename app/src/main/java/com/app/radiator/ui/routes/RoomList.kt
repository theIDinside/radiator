package com.app.radiator.ui.routes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.app.radiator.matrix.MatrixClient
import org.matrix.rustcomponents.sdk.SlidingSyncRoom

@Composable
fun RoomList(roomsList: List<SlidingSyncRoom>, navController: NavHostController) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
        .verticalScroll(
            rememberScrollState()
        )) {

    }
}