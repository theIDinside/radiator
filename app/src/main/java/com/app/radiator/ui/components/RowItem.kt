package com.app.radiator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.radiator.matrix.store.AsyncImageStorage
import com.app.radiator.matrix.store.MediaMxcURI

@Composable
fun Avatar(modifier: Modifier = Modifier, avatarData: AvatarData, size: Dp = 40.dp, avatarUrl: MediaMxcURI?) {
    val commonModifier = modifier
        .size(size)
        .clip(CircleShape)
    if (avatarData.url == null && avatarUrl == null) {
        InitialsAvatar(
            avatarData = avatarData,
            size = size,
            modifier = commonModifier,
        )
    } else {
        ImageAvatar(
            modifier = commonModifier,
            avatarData = avatarData,
            avatarUrl = avatarUrl
        )
    }
}

@Composable
fun ImageAvatar(
    modifier: Modifier = Modifier,
    avatarData: AvatarData,
    avatarUrl: MediaMxcURI?
) {
    val coroutineScope = rememberCoroutineScope()
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
        if(avatarUrl != null) {
            AsyncImageStorage.AsyncImage(coroutineScope = coroutineScope, url = avatarUrl)
        } else if(avatarData.url != null) {
            AsyncImageStorage.AsyncImage(coroutineScope = coroutineScope, url = MediaMxcURI(avatarData.url, "matrix.org"))
        }
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