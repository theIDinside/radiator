package com.app.radiator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
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

// TODO: we need to account for newlines after tags. Currently, the parser sees them as their own
//  individual freestanding (Text) nodes, which they shouldn't be.
sealed interface ParsedMessageNode {
  // Container types; holds no text themselves, any text they hold lives in a MessageItem.Text node
  data class Root(val children: List<ParsedMessageNode>) : ParsedMessageNode {
    @Composable
    override fun Display(modifier: Modifier, isInline: Boolean, textStyle: TextStyle?) {
      @Composable
      fun doRow(node: ParsedMessageNode) {
        node.Display(modifier = modifier, false, textStyle)
      }

      Column() {
        for (child in children) {
          doRow(child)
        }
      }

    }
  }

  data class ListNode(val items: List<ParsedMessageNode>) : ParsedMessageNode {
    @Composable
    override fun Display(modifier: Modifier, isInline: Boolean, textStyle: TextStyle?) {
      Row(verticalAlignment = Alignment.Bottom) {
        for (item in items) {
          item.Display(modifier, isInline = true, textStyle)
        }
      }
    }
  }

  data class UnorderedList(val list: List<ParsedMessageNode>) : ParsedMessageNode {
    @Composable
    override fun Display(modifier: Modifier, isInline: Boolean, textStyle: TextStyle?) {
      Column() {
        for (item in list) {
          Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            androidx.compose.material3.Text(text = AnnotatedString("•"))
            item.Display(modifier, isInline, textStyle)
          }
        }
      }
    }
  }

  data class OrderedList(val list: List<ParsedMessageNode>) : ParsedMessageNode {
    @Composable
    override fun Display(modifier: Modifier, isInline: Boolean, textStyle: TextStyle?) {
      var idx = 1
      Column {
        for (item in list) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Top
          ) {
            androidx.compose.material3.Text(text = AnnotatedString("${idx}. "))
            item.Display(modifier, isInline, textStyle)
          }
          idx++
        }
      }
    }
  }

  data class Heading(val size: Int, val items: List<ParsedMessageNode>) : ParsedMessageNode {
    @Composable
    override fun Display(modifier: Modifier, isInline: Boolean, textStyle: TextStyle?) {
      val style = headingStyle(size = size)
      Row() {
        for (item in items) {
          item.Display(modifier, isInline = true, style)
        }
      }
    }
  }

  // Actual text containers; what in the Javascript DOM sense would be an element's "innerText" property
  data class CodeBlock(val text: AnnotatedString) : ParsedMessageNode {

    @Composable
    fun InlineDisplay(textStyle: TextStyle?) {
      val inlineShape = RoundedCornerShape(7.dp)
      Box(
        modifier = Modifier
          .border(width = 1.dp, color = Color(141, 151, 165), shape = inlineShape)
          .background(color = Color(244, 246, 250))
          .padding(start = 5.dp, end = 5.dp)
          .clip(inlineShape)

      ) {
        if (textStyle != null) {
          Text(modifier = Modifier, text = text, style = textStyle)
        } else {
          Text(modifier = Modifier, text = text)
        }
      }
    }

    @Composable
    fun BlockDisplay(textStyle: TextStyle?) {
      val blockShape = RoundedCornerShape(10.dp)
      Box(
        modifier = Modifier
          .border(width = 2.dp, color = Color(141, 151, 165), shape = blockShape)
          .background(color = Color(244, 246, 250))
          .fillMaxWidth()
          .padding(start = 5.dp, top = 5.dp)
          .clip(blockShape)
          .horizontalScroll(rememberScrollState())
          .clickable {  }
      ) {
        if (textStyle != null) {
          Text(modifier = Modifier, text = text, style = textStyle)
        } else {
          Text(modifier = Modifier, text = text)
        }
      }
    }

    @Composable
    override fun Display(modifier: Modifier, isInline: Boolean, textStyle: TextStyle?) {
      if (!isInline) {
        BlockDisplay(textStyle = textStyle)
      } else {
        InlineDisplay(textStyle = textStyle)
      }
    }
  }

  data class Paragraph(val text: AnnotatedString) : ParsedMessageNode {
    @Composable
    override fun Display(modifier: Modifier, isInline: Boolean, textStyle: TextStyle?) {
      if (textStyle != null) {
        Text(modifier = Modifier, text = text, style = textStyle)
      } else {
        Text(modifier = Modifier, text = text)
      }
    }
  }

  // Normal regular good old text
  data class Text(val text: AnnotatedString) : ParsedMessageNode {
    @Composable
    override fun Display(modifier: Modifier, isInline: Boolean, textStyle: TextStyle?) {
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
    override fun Display(modifier: Modifier, isInline: Boolean, textStyle: TextStyle?) {
      TODO("Not yet implemented")
    }
  }

  @Composable
  fun Display(modifier: Modifier, isInline: Boolean, textStyle: TextStyle?)
}