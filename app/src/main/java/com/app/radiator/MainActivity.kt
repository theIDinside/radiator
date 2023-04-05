package com.app.radiator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.app.radiator.matrix.*
import com.app.radiator.ui.components.RoomListRoomSummary
import com.app.radiator.ui.components.RoomSummaryRow
import com.app.radiator.ui.theme.RadiatorTheme
import kotlinx.coroutines.*
import org.matrix.rustcomponents.sdk.*
import kotlin.io.path.exists

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
    object Settings : Routes("settings")
}

val homeServer = "matrix.org"
val userId = "@simonfarre:matrix.org"
val password = "#idx2003CANCER"


suspend fun Foo() {

}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applicationSetup(app = application)
        val matrixClient = MatrixClient()
        setContent {
            RadiatorTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = Routes.Login.route) {
                        composable(Routes.Login.route) {
                            LoginScreen(matrixClient = matrixClient, navController=navController)
                        }

                        composable(Routes.RoomList.route) {
                            val currentRoomList = matrixClient.rememberGetRoomSummaries()
                            val flow = matrixClient.slidingSyncListener.summaryFlow().collectAsState(initial = listOf())
                            currentRoomList.addAll(flow.value)
                            if(currentRoomList.isNotEmpty())
                                RoomList(roomList = currentRoomList)
                            else
                                Text("We don't have any summaries :(")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoomList(roomList: List<RoomListRoomSummary>) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        roomList.forEach {
            RoomSummaryRow(room = it)
        }
    }
}

@Composable
fun LoginScreen(matrixClient: MatrixClient, navController: NavController, modifier: Modifier = Modifier) {
    var username by remember { mutableStateOf<String>(userId) }
    var password by remember { mutableStateOf<String>(password) }
    val coroutineScope = rememberCoroutineScope()
    val isLoggedIn = matrixClient.isLoggedIn().collectAsState(initial = false)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.Center) {
                Text("Username")
            }
            Column(verticalArrangement = Arrangement.Center, modifier = Modifier.padding(horizontal = 5.dp)) {
                TextField(value = username, onValueChange = { username = it })
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(2.dp)
            , verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.Center) {
                Text("Password")
            }
            Column(verticalArrangement = Arrangement.Center, modifier = Modifier.padding(horizontal = 5.dp)) {
                TextField(value = password, onValueChange = { password = it })
            }
        }
        Row() {
            Button(onClick = {
                if(!isLoggedIn.value) {
                    val authenticationService = AuthenticationService(SystemInterface.applicationDataDir(), null, null)
                    if(SystemInterface.getApplicationFilePath(convertUserIdToFileName(userId)).exists()) {
                        val session = deserializeSession(userId)
                        if(session != null) {
                            coroutineScope.launch {
                                try {
                                    authenticationService.configureHomeserver(homeServer)
                                } catch(e: AuthenticationException) {
                                    println("falling back to sliding sync proxy....")
                                    authenticationService.configureHomeserver(session.slidingSyncProxy!!)
                                }
                                matrixClient.restoreSession(authService = authenticationService, session=session)
                            }
                        }
                    } else {
                        coroutineScope.launch {
                            authenticationService.configureHomeserver("matrix.org")
                            matrixClient.login(authService = authenticationService, userName = userId, password=password)
                        }
                    }
                }
                navController.navigate(route = Routes.RoomList.route)
            }) {
                Text("Click me to log in")
            }
        }
    }
}

@Composable
fun StartScreen(isLoggedIn: Boolean, modifier: Modifier = Modifier) {
    if(!isLoggedIn) {
        Text(text = "Hello fucker. You are Not logged in")
    } else {
        Text(text = "Hello fucker. You ARE logged in. CAN YOU BELIEVE THAT?")
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    RadiatorTheme {
        Greeting("Android")
    }
}