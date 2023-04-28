package com.app.radiator.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Our own pseudo tree-node structure, we convert it from HTML because HTML isn't great for
// radiator's purposes. These types then signal to to the Jetpack Compose engine how they are supposed
// to be laid out.

@Composable
fun headingStyle(size: Int): TextStyle {
  return when (size) {
    1 -> MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp)
    2 -> MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp)
    3 -> MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp)
    4 -> MaterialTheme.typography.headlineMedium.copy(fontSize = 18.sp)
    5 -> MaterialTheme.typography.headlineSmall.copy(fontSize = 14.sp)
    6 -> MaterialTheme.typography.headlineSmall.copy(fontSize = 12.sp)
    else -> MaterialTheme.typography.bodySmall.copy()
  }
}
// TODO(simon): We really can boil these down to two different types; inline or block nodes,
//  this would also map nicely to Jetpack Compose's Column() / Row() interface

// TODO: we need to account for newlines after tags. Currently, the parser sees them as their own
//  individual freestanding (Text) nodes, which they shouldn't be.
sealed interface ParsedMessageNode {

  @Composable
  fun Display(
    modifier: Modifier,
    textStyle: TextStyle?,
    onClickedEvent: (ParsedMessageNode) -> Unit,
  ) {
    Display(modifier, textStyle, onClickedEvent, onLongClick = {})
  }

  @Composable
  fun Display(
    modifier: Modifier,
    textStyle: TextStyle?,
    onClickedEvent: (ParsedMessageNode) -> Unit,
    onLongClick: () -> Unit,
  )

  // Container types; holds no text themselves, any text they hold lives in a MessageItem.Text node
  data class Root(val children: List<ParsedMessageNode>) : ParsedMessageNode {
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Display(
      modifier: Modifier,
      textStyle: TextStyle?,
      onClickedEvent: (ParsedMessageNode) -> Unit,
      onLongClick: () -> Unit,
    ) {
      @Composable
      fun doRow(node: ParsedMessageNode) {
        node.Display(modifier = modifier, textStyle, onClickedEvent, onLongClick = onLongClick)
      }
      Column() {
        FlowRow {
          for (child in children) {
            doRow(child)
          }
        }
      }
    }
  }

  data class HrefNode(val linkText: AnnotatedString, val url: String) : ParsedMessageNode {
    @Composable
    override fun Display(
      modifier: Modifier,
      textStyle: TextStyle?,
      onClickedEvent: (ParsedMessageNode) -> Unit,
      onLongClick: () -> Unit,
    ) {
      Text(
        modifier = Modifier.clickable {
          onClickedEvent(this)
        },
        text = linkText,
        textDecoration = TextDecoration.Underline,
        fontWeight = FontWeight.Bold,
        overflow = TextOverflow.Ellipsis,
      )
    }

  }

  data class ListNode(val items: List<ParsedMessageNode>) : ParsedMessageNode {
    @Composable
    override fun Display(
      modifier: Modifier,
      textStyle: TextStyle?,
      onClickedEvent: (ParsedMessageNode) -> Unit,
      onLongClick: () -> Unit,
    ) {
      Row(verticalAlignment = Alignment.Bottom) {
        for (item in items) {
          item.Display(modifier, textStyle, onClickedEvent, onLongClick = onLongClick)
        }
      }
    }
  }

  data class Paragraph(val items: List<ParsedMessageNode>) : ParsedMessageNode {
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Display(
      modifier: Modifier,
      textStyle: TextStyle?,
      onClickedEvent: (ParsedMessageNode) -> Unit,
      onLongClick: () -> Unit,
    ) {
      FlowRow {
        for (item in items) {
          item.Display(modifier, textStyle, onClickedEvent, onLongClick = onLongClick)
        }
      }
    }
  }

  data class UnorderedList(val list: List<ParsedMessageNode>) : ParsedMessageNode {
    @Composable
    override fun Display(
      modifier: Modifier,
      textStyle: TextStyle?,
      onClickedEvent: (ParsedMessageNode) -> Unit,
      onLongClick: () -> Unit,
    ) {
      Column() {
        for (item in list) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
          ) {
            Text(text = AnnotatedString("•"))
            item.Display(modifier, textStyle, onClickedEvent, onLongClick = onLongClick)
          }
        }
      }
    }
  }

  data class OrderedList(val list: List<ParsedMessageNode>) : ParsedMessageNode {
    @Composable
    override fun Display(
      modifier: Modifier,
      textStyle: TextStyle?,
      onClickedEvent: (ParsedMessageNode) -> Unit,
      onLongClick: () -> Unit,
    ) {
      var idx = 1
      Column {
        for (item in list) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Top
          ) {
            Text(text = remember { AnnotatedString("${idx}. ") })
            item.Display(modifier, textStyle, onClickedEvent, onLongClick = onLongClick)
          }
          idx++
        }
      }
    }
  }

  data class Heading(val size: Int, val items: List<ParsedMessageNode>) : ParsedMessageNode {
    @Composable
    override fun Display(
      modifier: Modifier,
      textStyle: TextStyle?,
      onClickedEvent: (ParsedMessageNode) -> Unit,
      onLongClick: () -> Unit,
    ) {
      val style = headingStyle(size = size)
      Row() {
        for (item in items) {
          item.Display(modifier, style, onClickedEvent, onLongClick = onLongClick)
        }
      }
    }
  }

  // Actual text containers; what in the Javascript DOM sense would be an element's "innerText" property
  data class CodeBlock(val text: AnnotatedString, val isInline: Boolean = true) :
    ParsedMessageNode {

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun InlineDisplay(
      textStyle: TextStyle?,
      onClickedEvent: (ParsedMessageNode) -> Unit,
      onLongClick: () -> Unit,
    ) {
      val inlineShape = RoundedCornerShape(7.dp)
      Box(
        modifier = Modifier
          .border(width = 1.dp, color = Color(141, 151, 165), shape = inlineShape)
          .background(color = Color(244, 246, 250))
          .padding(start = 5.dp, end = 5.dp)
          .clip(inlineShape)
          .combinedClickable(
            enabled = true,
            onLongClick = { onLongClick() },
            onClick = { onClickedEvent(this) })

      ) {
        if (textStyle != null) {
          Text(modifier = Modifier, text = text, style = textStyle)
        } else {
          Text(modifier = Modifier, text = text)
        }
      }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun BlockDisplay(
      textStyle: TextStyle?,
      onClickedEvent: (ParsedMessageNode) -> Unit,
      onLongClick: () -> Unit,
    ) {
      val blockShape = RoundedCornerShape(10.dp)
      Box(
        modifier = Modifier
          .border(width = 2.dp, color = Color(141, 151, 165), shape = blockShape)
          .background(color = Color(244, 246, 250))
          .fillMaxWidth()
          .padding(start = 5.dp, top = 5.dp)
          .clip(blockShape)
          .heightIn(10.dp, 400.dp)
          .horizontalScroll(rememberScrollState())
          .verticalScroll(rememberScrollState())
          .combinedClickable(
            enabled = true,
            onLongClick = { onLongClick() },
            onClick = { onClickedEvent(this) })
      ) {
        if (textStyle != null) {
          Text(modifier = Modifier, text = text, style = textStyle)
        } else {
          Text(modifier = Modifier, text = text)
        }
      }
    }

    @Composable
    override fun Display(
      modifier: Modifier,
      textStyle: TextStyle?,
      onClickedEvent: (ParsedMessageNode) -> Unit,
      onLongClick: () -> Unit,
    ) {
      if (!this.isInline || !isInline) {
        BlockDisplay(textStyle = textStyle, onClickedEvent, onLongClick = onLongClick)
      } else {
        InlineDisplay(textStyle = textStyle, onClickedEvent, onLongClick = onLongClick)
      }
    }
  }

  // Normal regular good old text
  data class TextNode(val text: AnnotatedString) : ParsedMessageNode {
    @Composable
    override fun Display(
      modifier: Modifier,
      textStyle: TextStyle?,
      onClickedEvent: (ParsedMessageNode) -> Unit,
      onLongClick: () -> Unit,
    ) {
      if (textStyle != null) {
        Text(modifier = Modifier, text = text, style = textStyle)
      } else {
        Text(modifier = Modifier, text = text)
      }
    }
  }

  // Items we can't or just flat out will not parse
  data class Unhandled(val text: String) : ParsedMessageNode {
    @Composable
    override fun Display(
      modifier: Modifier,
      textStyle: TextStyle?,
      onClickedEvent: (ParsedMessageNode) -> Unit,
      onLongClick: () -> Unit,
    ) {
      TODO("Not yet implemented")
    }
  }
}