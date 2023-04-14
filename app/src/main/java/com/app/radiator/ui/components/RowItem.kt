package com.app.radiator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Avatar(avatarData: AvatarData, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val commonModifier = modifier
        .size(size)
        .clip(CircleShape)
    if (avatarData.url == null) {
        InitialsAvatar(
            avatarData = avatarData,
            size = size,
            modifier = commonModifier,
        )
    } else {
        InitialsAvatar(
            avatarData = avatarData,
            size = size,
            modifier = commonModifier,
        )
        /*
        ImageAvatar(
            avatarData = avatarData,
            modifier = commonModifier,
        )
         */
    }
}

@Composable
fun InitialsAvatar(
    avatarData: AvatarData,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val initialsGradient = Brush.linearGradient(
        listOf(
            AvatarGradientStart,
            AvatarGradientEnd,
        ),
        start = Offset(0.0f, 50f),
        end = Offset(50f, 0f)
    )
    Box(
        modifier.background(brush = initialsGradient)
    ) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = avatarData.getInitial(),
            fontSize = (size / 2).value.sp,
            color = Color.White,
        )
    }
}