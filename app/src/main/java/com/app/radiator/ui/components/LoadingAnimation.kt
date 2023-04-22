package com.app.radiator.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun LoadingAnimation(
    circleColor: Color = Color.Magenta,
    animationDelay: Int = 1000,
    size: Dp = 128.dp
) {

    // circle's scale state
    val circleScale = remember { mutableStateOf(0f) }
    // animation
    val circleScaleAnimate = animateFloatAsState(
        targetValue = circleScale.value,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = animationDelay
            )
        )
    )
    // This is called when the app is launched
    LaunchedEffect(Unit) {
        circleScale.value = 1f
    }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        // animating circle
        Box(
            modifier = Modifier
                .size(size = size)
                .scale(scale = circleScaleAnimate.value)
                .border(
                    width = 4.dp,
                    color = circleColor.copy(alpha = 1 - circleScaleAnimate.value),
                    shape = CircleShape
                )
        ) {

        }
    }
}