package com.app.radiator.ui.routes

import com.app.radiator.R
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Preview
@Composable
fun LoginField(
    input: String = "",
    label: String = "",
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    onValueChange: (String) -> Unit = {},
    hideInput: Boolean = false
) {
    OutlinedTextField(
        value = input,
        onValueChange = onValueChange,
        placeholder = placeholder,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        visualTransformation = if(!hideInput) VisualTransformation.None else { PasswordVisualTransformation() }
    )
}

@OptIn(ExperimentalTextApi::class)
@Preview
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onLogin: (userName: String, password: String) -> Unit = { _, _ -> }
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = "Login Background", modifier = Modifier
                .fillMaxSize()
                .blur(1.dp),
            contentScale = ContentScale.FillBounds
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.55f)
                .padding(16.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 32.dp,
                        topEnd = 32.dp,
                        bottomStart = 32.dp,
                        bottomEnd = 32.dp
                    )
                )
                .background(Color.White)

        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(5.dp)
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Radiator", fontSize = 48.sp, style = TextStyle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF7b4397), Color(0xFFdc2430)
                        )
                    )
                )
            )
            Spacer(modifier = Modifier.padding(top = 68.dp))
            LoginField(
                input = username,
                label = "Username",
                onValueChange = { username = it },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Username") })
            val hidePasswordTransformation = remember { mutableStateOf(true) }
            LoginField(
                input = password,
                label = "Password",
                onValueChange = { password = it },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                trailingIcon = {
                    Icon(Icons.Default.Info, contentDescription = "Password", Modifier.clickable {
                        hidePasswordTransformation.value = !hidePasswordTransformation.value
                    })
                },
                hideInput = hidePasswordTransformation.value
            )
            Spacer(modifier = Modifier.padding(top = 16.dp))
            Button(
                modifier = Modifier.padding(top = 16.dp),
                onClick = { onLogin(username, password) }) {
                Text("Log in", fontSize = 28.sp)
            }
        }
    }
}