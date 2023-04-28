package com.app.radiator

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.app.radiator.matrix.*
import com.app.radiator.ui.components.LoadingAnimation
import com.app.radiator.ui.routes.LoginScreen
import com.app.radiator.ui.routes.RoomList
import com.app.radiator.ui.routes.RoomRoute
import com.app.radiator.ui.theme.RadiatorTheme
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.*
import org.matrix.rustcomponents.sdk.*

class ClientErrorHandler : ClientDelegate {
  override fun didReceiveAuthError(isSoftLogout: Boolean) {
    println("DID INDEED RECEIVE ERROR MOTHERFUCKER")
  }

}

data class CoroutineDispatchers(
  val io: CoroutineDispatcher,
  val computation: CoroutineDispatcher,
  val main: CoroutineDispatcher,
  val diffUpdateDispatcher: CoroutineDispatcher,
)

sealed class Routes(val route: String) {
  object Login : Routes("login")
  object RoomList : Routes("roomlist")
  object Room : Routes("room")
  object RoomDetails : Routes("settings")
}

const val homeServer = "matrix.org"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    applicationSetup(app = application)
    val client = MatrixClient()

    setContent {
      RadiatorTheme {
        // A surface container using the 'background' color from the theme
        Surface(
          modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
        ) {
          val navController = rememberNavController()
          val coroutineScope = rememberCoroutineScope()
          val isLoggedIn = client.isLoggedIn().collectAsState(initial = false)
          NavHost(navController = navController, startDestination = Routes.Login.route) {
            composable(Routes.Login.route) {
              LaunchedEffect(isLoggedIn.value) {
                if (isLoggedIn.value) {
                  navController.navigate(route = Routes.RoomList.route)
                }
              }
              val onLoginClick = { userId: String, password: String ->
                if (!isLoggedIn.value) {
                  val authenticationService = AuthenticationService(
                    SystemInterface.applicationDataDir(), null, null
                  )
                  coroutineScope.launch {
                    authenticationService.configureHomeserver("matrix.org")
                    client.login(
                      authService = authenticationService, userName = userId, password = password
                    )
                  }
                  navController.navigate(route = Routes.RoomList.route)
                }
              }
              LoginScreen(onLogin = onLoginClick)
            }

            composable(Routes.RoomList.route) {
              val rooms =
                client.slidingSyncListener.summaryFlow().collectAsState(initial = emptyList())

              if (rooms.value.isNotEmpty()) {
                RoomList(roomList = rooms.value.toImmutableList(), onClick = { summary ->
                  navController.navigate("roomGraph" + "/${summary.roomId}") {
                    launchSingleTop = true
                    restoreState = true
                  }
                })
              } else {
                LoadingAnimation(size = 168.dp)
              }
            }

            navigation(
              route = "roomGraph" + "/{roomId}",
              startDestination = Routes.Room.route + "/{roomId}"
            ) {
              composable(Routes.Room.route + "/{roomId}") { navBackStackEntry ->
                val roomId = navBackStackEntry.arguments?.getString("roomId")
                val timelineState = remember {
                  client.slidingSyncRoomManager.getTimelineState(roomId!!)
                }

                BackHandler(enabled = true, onBack = {
                  client.slidingSyncRoomManager.disposeOfTimelineState()
                  navController.navigate(Routes.RoomList.route) {
                    popUpTo(Routes.RoomList.route)
                  }
                })
                val isInit = remember { timelineState.isInit() }.collectAsState(initial = false)
                if (isInit.value) {
                  RoomRoute(navController = navController, timelineState = timelineState)
                } else {
                  LoadingAnimation()
                }
                // Destroy the timeline handle and all the resources it's holding on to (on the SDK side)
              }
              composable(Routes.RoomDetails.route + "/{roomId}") {
                val roomId = it.arguments?.getString("roomId")
                Text("Room details for $roomId")
              }
            }
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    println("onDestroy called but nothing was cleaned up.")
  }
}