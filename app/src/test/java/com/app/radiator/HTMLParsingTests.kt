package com.app.radiator

import com.app.radiator.ui.components.message.MessageBuilder
import com.app.radiator.ui.components.message.MessageNode
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class HTMLParsingTests  {

    private fun MessageNode.countTextNodes(): Int {
        return when(this) {
            is MessageNode.Text, is MessageNode.CodeBlock, is MessageNode.Paragraph -> return 1
            is MessageNode.Heading -> items.fold(0) { acc, it -> acc + it.countTextNodes() }
            is MessageNode.ListNode -> items.fold(0) { acc, it -> acc + it.countTextNodes() }
            is MessageNode.OrderedList -> list.fold(0) { acc, it -> acc + it.countTextNodes() }
            is MessageNode.Root -> children.fold(0) { acc, it -> acc + it.countTextNodes() }
            is MessageNode.Unhandled -> return 0
            is MessageNode.UnorderedList -> return list.fold(0) { acc, it -> acc + it.countTextNodes() }
        }
    }

    @Test
    fun shouldReturnSevenTextNodesWhere2AreFreeStanding() {
        val testDocument = "foo<ul><li>First</li><li>Second</li><li>Third</li><li>Fourth</li><li>Fifth</li></ul>secondFreeStanding"
        val builder = MessageBuilder()
        val message = builder.parse(testDocument)
        assertEquals(7, message.countTextNodes())
    }

    @Test
    fun shouldReturnSevenTextNodesWhere2AreFreeStanding1() {
        val testDocument = "foo<ul><li>First</li><li>Second</li><li>Third</li><li>Fourth</li><li>Fifth</li></ul>secondFreeStanding"
        val builder = MessageBuilder()
        val message = builder.parse(testDocument)
        assertEquals(7, message.countTextNodes())
    }

    @Test
    fun shouldReturnSevenTextNodesWhere2AreFreeStanding2() {
        val testDocument = "foo<ul><li>First</li><li>Second</li><li>Third</li><li>Fourth</li><li>Fifth</li></ul>secondFreeStanding"
        val builder = MessageBuilder()
        val message = builder.parse(testDocument)
        assertEquals(7, message.countTextNodes())
    }

    @Test
    fun shouldReturnThreeTextNodes() {
        val testDocument = "foo<ul><li>bar<p>baz</p></li></ul>"
        val builder = MessageBuilder()
        val message = builder.parse(testDocument)
        assertEquals(3, message.countTextNodes())
    }
    @Test
    fun shouldReturn10TextNodes() {
        val testDoc =
            """<h1>This is a heading</h1><p>this is some text under the heading</p><h2>This is a smaller sub heading</h2><p>Some sub paragraphp</p><h3>Yet another sub heading</h3><p>And some more text, with some list:</p><ul><li>Item A</li><li>Item B</li></ul><h4>The last heading</h4><pre><code>void imNotSuperFondOfHtmlBeingIntrinsicToMatrix() {  printf("Pretty stupid\n"); }</code></pre>""".trimIndent()
        val builder = MessageBuilder()
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