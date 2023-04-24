package com.app.radiator

import com.app.radiator.matrix.htmlparse.HTMLParser
import com.app.radiator.ui.components.ParsedMessageNode
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class HTMLParsingTests  {

    private fun ParsedMessageNode.countTextNodes(): Int {
        return when(this) {
            is ParsedMessageNode.TextNode, is ParsedMessageNode.CodeBlock, is ParsedMessageNode.Paragraph -> return 1
            is ParsedMessageNode.Heading -> items.fold(0) { acc, it -> acc + it.countTextNodes() }
            is ParsedMessageNode.ListNode -> items.fold(0) { acc, it -> acc + it.countTextNodes() }
            is ParsedMessageNode.OrderedList -> list.fold(0) { acc, it -> acc + it.countTextNodes() }
            is ParsedMessageNode.Root -> children.fold(0) { acc, it -> acc + it.countTextNodes() }
            is ParsedMessageNode.Unhandled -> return 0
            is ParsedMessageNode.UnorderedList -> return list.fold(0) { acc, it -> acc + it.countTextNodes() }
            is ParsedMessageNode.HrefNode -> TODO()
        }
    }

    @Test
    fun shouldReturnSevenTextNodesWhere2AreFreeStanding() {
        val testDocument = "foo<ul><li>First</li><li>Second</li><li>Third</li><li>Fourth</li><li>Fifth</li></ul>secondFreeStanding"
        val builder = HTMLParser()
        val message = builder.parse(testDocument)
        assertEquals(7, message.countTextNodes())
    }

    @Test
    fun shouldReturnSevenTextNodesWhere2AreFreeStanding1() {
        val testDocument = "foo<ul><li>First</li><li>Second</li><li>Third</li><li>Fourth</li><li>Fifth</li></ul>secondFreeStanding"
        val builder = HTMLParser()
        val message = builder.parse(testDocument)
        assertEquals(7, message.countTextNodes())
    }

    @Test
    fun shouldReturnSevenTextNodesWhere2AreFreeStanding2() {
        val testDocument = "foo<ul><li>First</li><li>Second</li><li>Third</li><li>Fourth</li><li>Fifth</li></ul>secondFreeStanding"
        val builder = HTMLParser()
        val message = builder.parse(testDocument)
        assertEquals(7, message.countTextNodes())
    }

    @Test
    fun shouldReturnThreeTextNodes() {
        val testDocument = "foo<ul><li>bar<p>baz</p></li></ul>"
        val builder = HTMLParser()
        val message = builder.parse(testDocument)
        assertEquals(3, message.countTextNodes())
    }

    @Test
    fun crashedDueToOutOfStringIndexException() {
        val testDoc =
"""
* <pre><code>- Bar
- Baz
</code></pre>

""".trimIndent()
        val builder = HTMLParser()
        builder.parse(testDoc)
    }

    @Test
    fun shouldReturn10TextNodes() {
        val testDoc =
            """<h1>This is a heading</h1><p>this is some text under the heading</p><h2>This is a smaller sub heading</h2><p>Some sub paragraphp</p><h3>Yet another sub heading</h3><p>And some more text, with some list:</p><ul><li>Item A</li><li>Item B</li></ul><h4>The last heading</h4><pre><code>void imNotSuperFondOfHtmlBeingIntrinsicToMatrix() {  printf("Pretty stupid\n"); }</code></pre>""".trimIndent()
        val builder = HTMLParser()
        val message = builder.parse(testDoc)
        assertEquals(10, message.countTextNodes())
    }
/*
    @Test
    fun shouldParseTagWithClassOk() {
        val testDoc =
"""foo<pre><code class="language-cpp">int main() {
    printf("hello world\n");
}</code></pre> peep game
""".trimMargin()
        val builder = MessageBuilder()
        val message = builder.parse(testDoc)
    }
 */
}