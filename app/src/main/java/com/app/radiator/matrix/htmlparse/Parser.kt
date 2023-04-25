package com.app.radiator.matrix.htmlparse

import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify.PHONE_NUMBERS
import android.text.util.Linkify.WEB_URLS
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.core.text.util.LinkifyCompat
import com.app.radiator.ui.components.ParsedMessageNode
import com.app.radiator.ui.theme.LinkColor

val whiteListedTags = setOf(
  "font",
  "del",
  "h1",
  "h2",
  "h3",
  "h4",
  "h5",
  "h6",
  "blockquote",
  "p",
  "a",
  "ul",
  "ol",
  "sup",
  "sub",
  "li",
  "b",
  "i",
  "u",
  "strong",
  "em",
  "strike",
  "code",
  "hr",
  "br",
  "div",
  "table",
  "thead",
  "tbody",
  "tr",
  "th",
  "td",
  "caption",
  "pre",
  "span",
  "img",
  "details",
  "summary"
)

// N.B: ORDER IS IMPORTANT; at least for H1-H6, as their ordinal is used for knowing what size it is
//  thus, H1-H6 needs to be at index 1 (position 2)
enum class Tag {
  // HTML Elements
  PARAGRAPH, H1, H2, H3, H4, H5, H6, OL, UL, LI, PRE, CODE, A,

  // Our own Pseudo-HTML-elements
  Root, InnerTextNode
}

val TagMap = mapOf(
  "ol" to Tag.OL,
  "ul" to Tag.UL,
  "li" to Tag.LI,
  "pre" to Tag.PRE,
  "code" to Tag.CODE,
  "h1" to Tag.H1,
  "h2" to Tag.H2,
  "h3" to Tag.H3,
  "h4" to Tag.H4,
  "h5" to Tag.H5,
  "h6" to Tag.H6,
  "p" to Tag.PARAGRAPH,
  "a" to Tag.A
)

fun findClose(start: Int, input: String): Int = input.indexOf(startIndex = start + 1, char = '>')

typealias TagRangeEndInclusive = ClosedRange<Int>

fun TagRangeEndInclusive.length(): Int {
  if (start == endInclusive) return 0
  return 1 + endInclusive - start
}

sealed class ParsedTag(val tagSpan: TagRangeEndInclusive, val tag: Tag) {
  class OpenTag(tagSpan: TagRangeEndInclusive, tag: Tag) : ParsedTag(tagSpan = tagSpan, tag = tag) {
    /**
     * Returns position in document past the opening tag (i.e after the '>'). If this tag
     * is one that can hold other child elements, it's not necessary true that the actual inner contents
     * start here; you'd have to query the inner most child element.
     */
    fun innerContentsShouldStart(): Int {
      return docIdxStart() + tagSpan.length()
    }
  }

  class CloseTag(tagSpan: TagRangeEndInclusive, tag: Tag) :
    ParsedTag(tagSpan = tagSpan, tag = tag) {
    /**
     * Returns the position before this tag's ending tag, i.e right before </tag>, where 'tag' is p, li, etc
     */
    fun innerContentsShouldEnd(): Int {
      return docIdxStart()
    }
  }

  /**
   * Returns index into document where this tag begins; i.e the position _at_ <tag> (the first '<')
   */
  fun docIdxStart(): Int = tagSpan.start

  fun docTagIdxEnd(): Int = tagSpan.endInclusive

  /**
   * Returns index into document where this tag ends; i.e the position _after_ </tag>
   */
  fun docIdxEnd(): Int {
    return docIdxStart() + tagSpan.length()
  }
}

// start must always equal to the position in input that holds a '<'
fun parseTag(start: Int, input: String): ParsedTag {
  if (input[start] != '<') throw Exception("start must always equal to the position in input that holds a '<'")
  val end = findClose(start = start, input)
  // if tag begins with </, it's a closing tag
  val tagNameStarts = if (input[start + 1] == '/') start + 1 else start
  val sub = input.subSequence(tagNameStarts + 1, end)

  // TODO: This will fail strangely when we encounter an unknown element
  return try {
    val tag =
      sub.toTag() ?: throw Exception("Parsing tag failed: '$sub' could not be recognized as a tag")
    val tagSpan = start..end
    if (tagNameStarts == start) ParsedTag.OpenTag(tagSpan = tagSpan, tag = tag)
    else ParsedTag.CloseTag(tagSpan = tagSpan, tag = tag)
  } catch (ex: Exception) {
    // if the tag hade classes etc - that's pretty rare though
    val space = sub.indexOf(' ')
    val tag = try {
      sub.subSequence(0, space).toTag()!!
    } catch (ex: Exception) {
      throw Exception("$ex. Doc contents: $input")
    }
    val tagSpan = start..end
    ParsedTag.OpenTag(tagSpan = tagSpan, tag = tag)
  }
}


fun CharSequence.toTag(): Tag? {
  if (TagMap[this] == null) {
    throw Exception("Failed to tokenize string input $this")
  }
  return TagMap[this]
}

// Abstract type that also aims to represent itself as the root node
abstract class DomNode(val openTag: ParsedTag.OpenTag, val parentNode: DomNode?) {
  lateinit var closeTag: ParsedTag.CloseTag
  abstract fun build(doc: String): ParsedMessageNode?
  abstract fun addNode(node: DomNode)

  fun closeTag(tag: ParsedTag.CloseTag) {
    closeTag = tag
  }

  abstract fun canOpenTag(tag: Tag): Boolean
  abstract fun canCloseTag(tag: Tag): Boolean
}

class DomRootNode(openTag: ParsedTag.OpenTag, parentNode: DomNode?) : DomNode(openTag, parentNode) {
  var children: ArrayList<DomNode> = ArrayList()
  override fun build(doc: String): ParsedMessageNode {
    return ParsedMessageNode.Root(children.mapNotNull { it.build(doc) })
  }

  override fun addNode(node: DomNode) {
    children.add(node)
  }

  override fun canOpenTag(tag: Tag): Boolean = true

  override fun canCloseTag(tag: Tag): Boolean = true
}

class DomListItem(openTag: ParsedTag.OpenTag, parentNode: DomNode?) : DomNode(openTag, parentNode) {
  var children: ArrayList<DomNode> = ArrayList()
  override fun build(doc: String): ParsedMessageNode {
    return ParsedMessageNode.ListNode(children.mapNotNull { it.build(doc) })
  }

  override fun addNode(node: DomNode) {
    children.add(node)
  }

  override fun canOpenTag(tag: Tag): Boolean = true

  override fun canCloseTag(tag: Tag): Boolean = true
}

class DomPreNode(openTag: ParsedTag.OpenTag, parentNode: DomNode?) : DomNode(openTag, parentNode) {
  lateinit var codeblockNode: DomCodeNode
  override fun build(doc: String): ParsedMessageNode {
    return codeblockNode.build(doc)
  }

  override fun addNode(node: DomNode) {
    if (::codeblockNode.isInitialized) throw Exception("Node already added to this <PRE> node")
    if (node !is DomCodeNode) throw Exception("The only child node supported for <PRE> nodes right now is a single <CODE> node")
    node.isInline = false
    codeblockNode = node
  }

  override fun canOpenTag(tag: Tag): Boolean {
    return tag == Tag.CODE
  }

  override fun canCloseTag(tag: Tag): Boolean = tag == Tag.CODE || tag == Tag.PRE
}

class DomCodeNode(openTag: ParsedTag.OpenTag, parentNode: DomNode?) : DomNode(openTag, parentNode) {
  lateinit var innerTextNode: DomTextNode
  var isInline = true
  override fun build(doc: String): ParsedMessageNode {
    val start = openTag.innerContentsShouldStart()
    val end = closeTag.innerContentsShouldEnd()
    if (end > doc.length) {
      throw Exception("(start of tag: $start Index $end out of bounds of document: ${doc.length}. Document contents: \n$doc")
    }
    val text = doc.substring(start, end).replace("&quot;", "\"")
    return ParsedMessageNode.CodeBlock(text = AnnotatedString(text), isInline = isInline)
  }

  override fun addNode(node: DomNode) {
    if (::innerTextNode.isInitialized) throw Exception("Inner Text Node already added to this <CODE> node")
    if (node !is DomTextNode) throw Exception("The only child node supported for <CODE> nodes right now is a single pseudo html TextNode but was $node")
    innerTextNode = node
  }

  override fun canOpenTag(tag: Tag): Boolean = false
  override fun canCloseTag(tag: Tag): Boolean = tag == Tag.CODE
}

class DomParagraphNode(openTag: ParsedTag.OpenTag, parentNode: DomNode?) :
  DomNode(openTag, parentNode) {
  private var children: ArrayList<DomNode> = ArrayList()
  override fun build(doc: String): ParsedMessageNode {
    return ParsedMessageNode.Paragraph(items = children.mapNotNull { it.build(doc) }.toList())
  }

  override fun addNode(node: DomNode) {
    children.add(node)
  }

  override fun canOpenTag(tag: Tag): Boolean = true

  override fun canCloseTag(tag: Tag): Boolean = true
}

class DomOrderedList(openTag: ParsedTag.OpenTag, parentNode: DomNode?) :
  DomNode(openTag, parentNode) {
  private var children: ArrayList<DomNode> = ArrayList()
  override fun build(doc: String): ParsedMessageNode {
    return ParsedMessageNode.OrderedList(children.filter { it is DomListItem }
      .mapNotNull { it.build(doc = doc) }.toList())
  }

  override fun addNode(node: DomNode) {
    children.add(node)
  }

  override fun canOpenTag(tag: Tag): Boolean = true

  override fun canCloseTag(tag: Tag): Boolean = true
}

class DomUnorderedList(openTag: ParsedTag.OpenTag, parentNode: DomNode?) :
  DomNode(openTag, parentNode) {
  var children: ArrayList<DomNode> = ArrayList()
  override fun build(doc: String): ParsedMessageNode {
    return ParsedMessageNode.UnorderedList(children.filter { it is DomListItem }
      .mapNotNull { it.build(doc = doc) }.toList())
  }

  override fun addNode(node: DomNode) {
    children.add(node)
  }

  override fun canOpenTag(tag: Tag): Boolean = true

  override fun canCloseTag(tag: Tag): Boolean = true
}

class DomHeadingNode(openTag: ParsedTag.OpenTag, parentNode: DomNode?) :
  DomNode(openTag, parentNode) {
  var children: ArrayList<DomNode> = ArrayList()
  private val headingVariant = openTag.tag.ordinal
  override fun build(doc: String): ParsedMessageNode {
    return ParsedMessageNode.Heading(headingVariant, children.mapNotNull { it.build(doc) })
  }

  override fun addNode(node: DomNode) {
    children.add(node)
  }

  override fun canOpenTag(tag: Tag): Boolean = true

  override fun canCloseTag(tag: Tag): Boolean = true
}

class DomTextNode(openTag: ParsedTag.OpenTag, parentNode: DomNode?) : DomNode(openTag, parentNode) {
  override fun build(doc: String): ParsedMessageNode? {
    val contentsStart = openTag.innerContentsShouldStart()
    val contentsEnd = closeTag.innerContentsShouldEnd()
    val text = doc.subSequence(contentsStart, contentsEnd).toString()
    return if (text == "\n") null
    else {
      val annotatedString = buildAnnotatedString {
        append(text)
        val textSpan = SpannableString(text)
        LinkifyCompat.addLinks(textSpan, WEB_URLS or PHONE_NUMBERS)
        for (span in textSpan.getSpans(0, textSpan.length, URLSpan::class.java)) {
          val begin = textSpan.getSpanStart(span)
          val end = textSpan.getSpanEnd(span)
          addStyle(start = begin, end = end, style = SpanStyle(color = LinkColor))
          addStringAnnotation(tag = "URL", annotation = span.url, start = begin, end = end)
        }
      }
      ParsedMessageNode.TextNode(text = annotatedString)
    }
  }

  override fun addNode(node: DomNode) {
    throw Exception("Can't add child node to ${this.openTag}")
  }

  override fun canOpenTag(tag: Tag): Boolean {
    throw Exception("Can't open tag in pseudo element TextNode $tag")
  }

  override fun canCloseTag(tag: Tag): Boolean {
    throw Exception("Can't close tag in pseudo element TextNode $tag")
  }
}

const val HREF = "href=\""

class DomAHrefNode(openTag: ParsedTag.OpenTag, parentNode: DomNode?) :
  DomNode(openTag, parentNode) {
  lateinit var linkTextNode: DomTextNode
  fun parseUrl(subSequence: CharSequence): String {
    val pos = subSequence.indexOf(HREF)
    var lastPosOfQuote = pos
    var idx = pos
    for (ch in subSequence.subSequence(pos + 1, subSequence.length)) {
      if (ch == '"') lastPosOfQuote = idx
      idx++
    }
    val url = subSequence.subSequence(pos + HREF.length, lastPosOfQuote + 1)
    return url.toString()
  }

  override fun build(doc: String): ParsedMessageNode {
    val start = openTag.innerContentsShouldStart()
    val end = closeTag.innerContentsShouldEnd()
    val url = parseUrl(doc.subSequence(openTag.docIdxStart(), openTag.docTagIdxEnd()))
    val linkColor = Color.Blue
    val linkText = buildAnnotatedString {
      withStyle(SpanStyle(color = linkColor)) {
        append(doc.substring(start, end))
      }
      addStringAnnotation(tag = "URL", annotation = url, start = start, end = end)
    }
    return ParsedMessageNode.HrefNode(linkText, url = url)
  }

  override fun addNode(node: DomNode) {
    if (::linkTextNode.isInitialized) throw Exception("Inner Text Node already added to this <A> node")
    if (node !is DomTextNode) throw Exception("The only child node supported for <A> nodes is a single pseudo html TextNode but was $node")
    linkTextNode = node
  }

  override fun canOpenTag(tag: Tag): Boolean = false
  override fun canCloseTag(tag: Tag): Boolean = tag == Tag.A

}

// TODO(simon): implement plugin functionality where we can inject our own parsing plugins.
//  one use case would be, when we parse a code block that has `class=language-foo` that we can pass
//  a plugin that can parse the language foo into an annotated string (i.e. a string that's syntax highlighted)
class HTMLParser(val blackListedTags: Set<String> = setOf()) {
  fun parse(inputBody: String): ParsedMessageNode {
    val rootNode = DomRootNode(ParsedTag.OpenTag(tagSpan = 0..0, tag = Tag.Root), null)
    var currNode: DomNode? = rootNode
    var body = inputBody
    for (blackListedTag in blackListedTags) {
      val (open, close) = Pair("<$blackListedTag>", "</$blackListedTag>")
      body = body.replace(open, "").replace(close, "")
    }
    body = body.replace("&quot;", "\"")
    var pos = 0
    val len = body.length
    var currentInnerTextNode: DomTextNode? = null

    // Helper function that is used to filter out newlines, that we should not parse or interpret
    // as well as ignoring to record the innerText of <a> elements, as our <a>-node holds the contents itself (like a normal text node)
    fun shouldRecordFreestanding(): Boolean {
      return if (currNode != null) {
        when (currNode) {
          is DomOrderedList, is DomUnorderedList, is DomAHrefNode -> false
          else -> true
        }
      } else true
    }

    fun closeInnerText(curr: DomNode, innerTextNode: DomNode, pos: Int): DomTextNode? {
      innerTextNode.closeTag(
        ParsedTag.CloseTag(
          tag = Tag.InnerTextNode, tagSpan = pos..pos
        )
      )
      curr.addNode(innerTextNode)
      return null
    }

    while (pos < len) {
      if (body[pos] == '<') {
        when (val tag = parseTag(start = pos, body)) {
          is ParsedTag.CloseTag -> {
            // if this node, accepts child nodes of any kind, then we close this child node
            if (currNode?.canCloseTag(tag.tag) == true) {
              // if we were parsing an innerText, first add it to the current node that's being parsed
              // i.e if we're parsing a <a href>foo bar baz</a>, we close innerText `foo bar baz` first, add it
              // to this <a> node, then close the <a>
              if (currentInnerTextNode != null) {
                currentInnerTextNode = closeInnerText(currNode, currentInnerTextNode, pos)
              }
              currNode.closeTag(tag = tag)
              currNode = currNode.parentNode
            }
            pos = tag.docIdxEnd()
          }

          is ParsedTag.OpenTag -> {
            // if we're seeing a new tag, close any innerText node we're parsing and add it to the current
            // node we're in (not the to be opened node, the current)
            if(currentInnerTextNode != null) {
              currentInnerTextNode = closeInnerText(currNode!!, currentInnerTextNode, pos)
            }
            if (currNode?.canOpenTag(tag.tag) == true) {
              val newNode = when (tag.tag) {
                Tag.OL -> {
                  DomOrderedList(openTag = tag, parentNode = currNode)
                }

                Tag.UL -> {
                  DomUnorderedList(openTag = tag, parentNode = currNode)
                }

                Tag.LI -> {
                  DomListItem(openTag = tag, parentNode = currNode)
                }
                // <pre> makes <code> into a block node
                Tag.PRE -> DomPreNode(openTag = tag, parentNode = currNode)
                // <code> is an inline node, if it isn't preceeded immediately by a <pre> node
                Tag.CODE -> DomCodeNode(openTag = tag, parentNode = currNode)
                Tag.H1, Tag.H2, Tag.H3, Tag.H4, Tag.H5, Tag.H6 -> {
                  DomHeadingNode(openTag = tag, parentNode = currNode)
                }
                Tag.Root -> DomRootNode(openTag = tag, parentNode = currNode)
                Tag.InnerTextNode -> throw Exception("Pseudo nodes can't be parsed from document text; we create them after the fact")
                Tag.PARAGRAPH -> DomParagraphNode(openTag = tag, parentNode = currNode)
                Tag.A -> DomAHrefNode(openTag = tag, parentNode = currNode)
              }
              currNode.addNode(newNode)
              currNode = newNode
            }
            pos = tag.docIdxEnd()
          }
        }
      } else {
        if (shouldRecordFreestanding() && currentInnerTextNode == null) {
          currentInnerTextNode = DomTextNode(
            openTag = ParsedTag.OpenTag(
              tag = Tag.InnerTextNode, tagSpan = pos..pos
            ), parentNode = currNode
          )
        }
        pos++
      }
    }
    if (currentInnerTextNode != null) {
      currentInnerTextNode.closeTag(
        ParsedTag.CloseTag(
          tag = Tag.InnerTextNode, tagSpan = pos..pos
        )
      )
      if (currNode != rootNode) {
        throw Exception("After parsing document current node should be root node, but it's $currNode")
      }
      currNode.addNode(currentInnerTextNode)
    }
    rootNode.closeTag(ParsedTag.CloseTag(tagSpan = body.length..body.length, tag = Tag.Root))
    return rootNode.build(body)
  }
}