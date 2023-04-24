package com.app.radiator.matrix.htmlparse

import androidx.compose.ui.text.AnnotatedString
import com.app.radiator.ui.components.ParsedMessageNode

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
  PARAGRAPH,
  H1,
  H2,
  H3,
  H4,
  H5,
  H6,
  OL,
  UL,
  LI,
  PRE,
  CODE,
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
  "p" to Tag.PARAGRAPH
)

fun findClose(start: Int, input: String): Int = input.indexOf(startIndex = start + 1, char = '>')

typealias TagRangeEndInclusive = ClosedRange<Int>

fun TagRangeEndInclusive.length(): Int {
  if (start == endInclusive) return 0
  return 1 + endInclusive - start
}

sealed class ParsedTag(val tagSpan: TagRangeEndInclusive, val tag: Tag) {
  class OpenTag(tagSpan: TagRangeEndInclusive, tag: Tag) :
    ParsedTag(tagSpan = tagSpan, tag = tag) {
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
  return try {
    val tag = sub.toTag()
      ?: throw Exception("Parsing tag failed: '$sub' could not be recognized as a tag")
    val tagSpan = start..end
    if (tagNameStarts == start) ParsedTag.OpenTag(tagSpan = tagSpan, tag = tag)
    else ParsedTag.CloseTag(tagSpan = tagSpan, tag = tag)
  } catch (ex: Exception) {
    // if the tag hade classes etc - that's pretty rare though
    val space = sub.indexOf(' ')
    if (space == -1) {
      throw Exception("Could not find space inside tag that has attributes. Contents: '$sub' in document: \n$input")
    }
    val tag = sub.subSequence(0, space).toTag()!!
    val tagSpan = start..end
    ParsedTag.OpenTag(tagSpan = tagSpan, tag = tag)
  }
}


fun CharSequence.toTag(): Tag? = TagMap[this]

// Abstract type that also aims to represent itself as the root node
abstract class DomNode(val openTag: ParsedTag.OpenTag, val parentNode: DomNode?) {
  lateinit var closeTag: ParsedTag.CloseTag
  abstract fun build(doc: String): ParsedMessageNode?
  abstract fun addNode(node: DomNode)

  fun closeTag(tag: ParsedTag.CloseTag) {
    closeTag = tag
  }
}

class DomRootNode(openTag: ParsedTag.OpenTag, parentNode: DomNode?) : DomNode(openTag, parentNode) {
  var children: ArrayList<DomNode> = ArrayList()
  override fun build(doc: String): ParsedMessageNode {
    return ParsedMessageNode.Root(children.mapNotNull { it.build(doc) })
  }

  override fun addNode(node: DomNode) {
    children.add(node)
  }
}

class DomListItem(openTag: ParsedTag.OpenTag, parentNode: DomNode?) : DomNode(openTag, parentNode) {
  var children: ArrayList<DomNode> = ArrayList()
  override fun build(doc: String): ParsedMessageNode {
    return ParsedMessageNode.ListNode(children.mapNotNull { it.build(doc) })
  }

  override fun addNode(node: DomNode) {
    children.add(node)
  }
}

class DomPreNode(openTag: ParsedTag.OpenTag, parentNode: DomNode?) : DomNode(openTag, parentNode) {
  lateinit var codeblockNode: DomCodeNode
  override fun build(doc: String): ParsedMessageNode {
    return codeblockNode.build(doc)
  }

  override fun addNode(node: DomNode) {
    if (::codeblockNode.isInitialized) throw Exception("Node already added to this <PRE> node")
    if (node !is DomCodeNode) throw Exception("The only child node supported for <PRE> nodes right now is a single <CODE> node")
    codeblockNode = node
  }
}

class DomCodeNode(openTag: ParsedTag.OpenTag, parentNode: DomNode?) :
  DomNode(openTag, parentNode) {
  lateinit var innerTextNode: DomTextNode
  override fun build(doc: String): ParsedMessageNode {
    val start = openTag.innerContentsShouldStart()
    val end = closeTag.innerContentsShouldEnd()
    if (end > doc.length) {
      throw Exception("(start of tag: $start Index $end out of bounds of document: ${doc.length}. Document contents: \n$doc")
    }
    val text = doc.substring(start, end).replace("&quot;", "\"")
    return ParsedMessageNode.CodeBlock(text = AnnotatedString(text))
  }

  override fun addNode(node: DomNode) {
    if (::innerTextNode.isInitialized) throw Exception("Inner Text Node already added to this <CODE> node")
    if (node !is DomTextNode) throw Exception("The only child node supported for <CODE> nodes right now is a single pseudo html TextNode but was $node")
    innerTextNode = node
  }
}

class DomParagraphNode(openTag: ParsedTag.OpenTag, parentNode: DomNode?) :
  DomNode(openTag, parentNode) {
  lateinit var innerTextNode: DomTextNode
  override fun build(doc: String): ParsedMessageNode {
    val start = openTag.innerContentsShouldStart()
    val end = closeTag.innerContentsShouldEnd()
    val text = doc.substring(start, end)
    return ParsedMessageNode.Paragraph(text = AnnotatedString(text))
  }

  override fun addNode(node: DomNode) {
    if (::innerTextNode.isInitialized) throw Exception("Inner Text Node already added to this <P> node")
    if (node !is DomTextNode) throw Exception("The only child node supported for <P> nodes right now is a single pseudo html TextNode but was $node")
    innerTextNode = node
  }
}

class DomOrderedList(openTag: ParsedTag.OpenTag, parentNode: DomNode?) : DomNode(openTag, parentNode) {
  private var children: ArrayList<DomNode> = ArrayList()
  override fun build(doc: String): ParsedMessageNode {
    return ParsedMessageNode.OrderedList(children.filter { it is DomListItem }
      .mapNotNull { it.build(doc = doc) }
      .toList())
  }

  override fun addNode(node: DomNode) {
    children.add(node)
  }
}

class DomUnorderedList(openTag: ParsedTag.OpenTag, parentNode: DomNode?) :
  DomNode(openTag, parentNode) {
  var children: ArrayList<DomNode> = ArrayList()
  override fun build(doc: String): ParsedMessageNode {
    return ParsedMessageNode.UnorderedList(children.filter { it is DomListItem }
      .mapNotNull { it.build(doc = doc) }
      .toList())
  }

  override fun addNode(node: DomNode) {
    children.add(node)
  }
}

class DomHeadingNode(openTag: ParsedTag.OpenTag, parentNode: DomNode?) : DomNode(openTag, parentNode) {
  var children: ArrayList<DomNode> = ArrayList()
  private val headingVariant = openTag.tag.ordinal
  override fun build(doc: String): ParsedMessageNode {
    return ParsedMessageNode.Heading(headingVariant, children.mapNotNull { it.build(doc) })
  }

  override fun addNode(node: DomNode) {
    children.add(node)
  }
}

class DomTextNode(openTag: ParsedTag.OpenTag, parentNode: DomNode?) : DomNode(openTag, parentNode) {
  override fun build(doc: String): ParsedMessageNode? {
    val start = openTag.innerContentsShouldStart()
    val end = closeTag.innerContentsShouldEnd()
    val text = doc.subSequence(start, end).toString()
    return if(text == "\n") null
    else ParsedMessageNode.Text(text = AnnotatedString(text = text))
  }

  override fun addNode(node: DomNode) {
    throw Exception("Can't add child node to ${this.openTag}")
  }
}

// TODO(simon): implement plugin functionality where we can inject our own parsing plugins.
//  one use case would be, when we parse a code block that has `class=language-foo` that we can pass
//  a plugin that can parse the language foo into an annotated string (i.e. a string that's syntax highlighted)
class HTMLParser {
  fun parse(inputBody: String): ParsedMessageNode {
    val rootNode = DomRootNode(ParsedTag.OpenTag(tagSpan = 0..0, tag = Tag.Root), null)
    var currNode: DomNode? = rootNode
    val body = inputBody.replace("<br>", "\n").trim()
    var pos = 0
    val len = body.length
    var currentInnerTextNode: DomTextNode? = null

    // Helper function that is used to filter out newlines, etc, that should not be in the final result
    fun shouldRecordFreestanding(): Boolean {
      return if (currNode != null) {
        when (currNode) {
          is DomOrderedList, is DomUnorderedList -> false
          else -> true
        }
      } else true
    }
    while (pos < len) {
      if (body[pos] == '<') {
        if (currentInnerTextNode != null) {
          currentInnerTextNode.closeTag(
            ParsedTag.CloseTag(
              tag = Tag.InnerTextNode, tagSpan = pos..pos
            )
          )
          currNode?.addNode(currentInnerTextNode)
          currentInnerTextNode = null
        }
        when (val tag = parseTag(start = pos, body)) {
          is ParsedTag.CloseTag -> {
            currNode?.closeTag(tag = tag)
            currNode = currNode?.parentNode
            pos = tag.docIdxEnd()
          }

          is ParsedTag.OpenTag -> {
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

              Tag.PRE -> DomPreNode(openTag = tag, parentNode = currNode)
              Tag.CODE -> DomCodeNode(openTag = tag, parentNode = currNode)
              Tag.H1, Tag.H2, Tag.H3, Tag.H4, Tag.H5, Tag.H6 -> {
                DomHeadingNode(openTag = tag, parentNode = currNode)
              }

              Tag.Root -> DomRootNode(openTag = tag, parentNode = currNode)
              Tag.InnerTextNode -> throw Exception("Pseudo nodes can't be parsed from document text; we create them after the fact")
              Tag.PARAGRAPH -> DomParagraphNode(openTag = tag, parentNode = currNode)
            }
            currNode?.addNode(newNode)
            currNode = newNode
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