package com.app.radiator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
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
import kotlin.system.measureNanoTime

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
                    val coroutineScope = rememberCoroutineScope()
                    NavHost(navController = navController, startDestination = Routes.Login.route) {
                        composable(Routes.Login.route) {
                            val isLoggedIn =
                                matrixClient.isLoggedIn().collectAsState(initial = false)

                            val onLoginClick = { userId: String, password: String ->
                                if (!isLoggedIn.value) {
                                    val authenticationService = AuthenticationService(
                                        SystemInterface.applicationDataDir(),
                                        null,
                                        null
                                    )
                                    if (SystemInterface.getApplicationFilePath(
                                            convertUserIdToFileName(userId)
                                        ).exists()
                                    ) {
                                        val session = deserializeSession(userId)
                                        if (session != null) {
                                            coroutineScope.launch {
                                                try {
                                                    authenticationService.configureHomeserver(
                                                        homeServer
                                                    )
                                                } catch (e: AuthenticationException) {
                                                    authenticationService.configureHomeserver(
                                                        session.slidingSyncProxy!!
                                                    )
                                                }
                                                matrixClient.restoreSession(
                                                    authService = authenticationService,
                                                    session = session
                                                )
                                            }
                                        }
                                        navController.navigate(route = Routes.RoomList.route)
                                    } else {
                                        coroutineScope.launch {
                                            authenticationService.configureHomeserver("matrix.org")
                                            matrixClient.login(
                                                authService = authenticationService,
                                                userName = userId,
                                                password = password
                                            )
                                        }
                                        navController.navigate(route = Routes.RoomList.route)
                                    }
                                }
                            }
                            LoginScreen(onLogin = onLoginClick)
                        }

                        composable(Routes.RoomList.route) {
                            val currentRoomList = matrixClient.rememberGetRoomSummaries()
                            val updates = matrixClient.slidingSyncListener.summaryFlow()
                                .collectAsState(initial = listOf())
                            val elapsed = measureNanoTime {
                                for (item in updates.value) {
                                    val idx = currentRoomList.indexOfFirst { it.id == item.id }
                                    if (idx != -1) {
                                        currentRoomList.set(idx, item)
                                    } else {
                                        currentRoomList.add(item)
                                    }
                                }
                            }

                            println("applied diff in $elapsed")

                            if (currentRoomList.isNotEmpty())
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
            Spacer(
                modifier = Modifier
                    .height(5.dp)
                    .border(BorderStroke(width = 1.dp, Color.Green))
            )
        }
    }
}

@Preview
@Composable
fun LoginField(
    input: String = "",
    label: String = "",
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    onValueChange: (String) -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = input,
        onValueChange = onValueChange,
        placeholder = placeholder,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation
    )
}

@Preview
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onLogin: (userName: String, password: String) -> Unit = { un, pw -> }
) {
    var username by remember { mutableStateOf<String>("") }
    var password by remember { mutableStateOf<String>("") }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.3f)
                .padding(16.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 32.dp,
                        topEnd = 32.dp,
                        bottomStart = 32.dp,
                        bottomEnd = 32.dp
                    )
                )
                .background(Color.Blue)

        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(5.dp)
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LoginField(
                input = username,
                label = "Username",
                onValueChange = { username = it },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Username") })
            LoginField(
                input = password,
                label = "Password",
                onValueChange = { password = it },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                visualTransformation = PasswordVisualTransformation()
            )
            Button(
                modifier = Modifier.padding(top = 16.dp),
                onClick = { onLogin(username, password) }) {
                Text("Log in")
            }
        }
    }
}

@Composable
fun StartScreen(isLoggedIn: Boolean, modifier: Modifier = Modifier) {
    if (!isLoggedIn) {
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