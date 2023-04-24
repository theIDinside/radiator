package com.app.radiator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.radiator.matrix.timeline.displayName

val InReplyToText = AnnotatedString(
    "in reply to: ", spanStyles = listOf(
        AnnotatedString.Range(
            SpanStyle(
                color = Color.Blue,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline
            ), 0, 11
        ),
        AnnotatedString.Range(
            SpanStyle(
                color = Color.Blue,
                fontWeight = FontWeight.Bold,
            ), 12, 13
        )
    )
)

@Preview
@Composable
fun ReplyItem(
    textThatWasRepliedTo: AnnotatedString = AnnotatedString("Foo\nBar"),
    avatarData: AvatarData? = AvatarData(
        id = preview.sender,
        name = preview.senderProfile.displayName(),
        url = null
    ),
    onClickInReplyTo: () -> Unit = {},
    onClickUserRepliedTo: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Color.White)
    ) {
        Row() {
            Column(Modifier.height(IntrinsicSize.Max), horizontalAlignment = Alignment.Start) {
                Column(
                    modifier = Modifier
                        .background(
                            color = Color.White,
                            shape = RoundedCornerShape(4.dp, 4.dp, 0.dp, 4.dp)
                        )
                        .padding(start = 15.dp)
                        .drawBehind {
                            val borderSize = 2.dp.toPx()
                            drawLine(
                                color = Color.Black,
                                start = Offset(-5f, 0f),
                                end = Offset(-5f, size.height),
                                strokeWidth = borderSize
                            )
                        }
                ) {
                    Row(modifier = Modifier.padding(start = 5.dp, top = 3.dp)) {
                        Text(modifier = Modifier.clickable { onClickInReplyTo() }, text = InReplyToText)
                        Card(
                            modifier = Modifier.clickable { onClickUserRepliedTo() },
                            shape = RoundedCornerShape(9.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
                        ) {
                            Row(
                                modifier = Modifier,
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(Modifier.width(2.dp))
                                Avatar(avatarData = avatarData!!, size = 13.dp)
                                Spacer(Modifier.width(5.dp))
                                Text(text = avatarData.name!!)
                                Spacer(Modifier.width(5.dp))
                            }
                        }
                    }
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 5.dp), text = textThatWasRepliedTo
                    )
                }
            }
        }
    }
}

@Composable
fun ReplyItemMessageNode(
    rootMessageNode: ParsedMessageNode,
    avatarData: AvatarData? = AvatarData(
        id = preview.sender,
        name = preview.senderProfile.displayName(),
        url = null
    ),
    onClickInReplyTo: () -> Unit = {},
    onClickUserRepliedTo: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Color.White)
    ) {
        Row() {
            Column(Modifier.height(IntrinsicSize.Max), horizontalAlignment = Alignment.Start) {
                Column(
                    modifier = Modifier
                        .background(
                            color = Color.White,
                            shape = RoundedCornerShape(4.dp, 4.dp, 0.dp, 4.dp)
                        )
                        .padding(start = 15.dp)
                        .drawBehind {
                            val borderSize = 2.dp.toPx()
                            drawLine(
                                color = Color.Black,
                                start = Offset(-5f, 0f),
                                end = Offset(-5f, size.height),
                                strokeWidth = borderSize
                            )
                        }
                ) {
                    Row(modifier = Modifier.padding(start = 5.dp, top = 3.dp)) {
                        Text(modifier = Modifier.clickable { onClickInReplyTo() }, text = InReplyToText)
                        Card(
                            modifier = Modifier.clickable { onClickUserRepliedTo() },
                            shape = RoundedCornerShape(9.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
                        ) {
                            Row(
                                modifier = Modifier,
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(Modifier.width(2.dp))
                                Avatar(avatarData = avatarData!!, size = 13.dp)
                                Spacer(Modifier.width(5.dp))
                                Text(text = avatarData.name!!)
                                Spacer(Modifier.width(5.dp))
                            }
                        }
                    }
                    rootMessageNode.Display(modifier = Modifier, isInline = false, textStyle = null)
                }
            }
        }
    }
}