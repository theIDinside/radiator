package com.app.radiator.matrix.timeline.html

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentHashMap

val PermittedAttributes: PersistentMap<WhiteListedTag, ImmutableList<Attr>> = mapOf(
    Pair(Tag.FONT, arrayListOf(Attr.DataMxColor, Attr.DataMxBgColor, Attr.Color).toImmutableList()),
    Pair(Tag.SPAN, arrayListOf(Attr.Color, Attr.DataMxSpoiler).toImmutableList()),
    Pair(Tag.A, arrayListOf(Attr.Name, Attr.Target, Attr.Href).toImmutableList()),
    Pair(Tag.IMG, arrayListOf(Attr.Width, Attr.Height, Attr.Alt, Attr.Title, Attr.Src).toImmutableList()),
    Pair(Tag.OL, arrayListOf(Attr.Start).toImmutableList()),
    Pair(Tag.CODE, arrayListOf(Attr.Class).toImmutableList())
).toPersistentHashMap()

fun parseTag(input: String): Tag {
    // when(input) {}
    return Tag.OL
}

sealed class DOMTreeElement

sealed class TreeElement : DOMTreeElement() {
    companion object NoChildren : ArrayList<DOMTreeElement>()
    var children: ArrayList<DOMTreeElement> = NoChildren
    var text: String? = null
}

class RootNode : TreeElement() {}

class DOMTreeBuilder(val input: String) {
    var openStack: ArrayList<WhiteListedTag> = ArrayList(32)
    var currentPosition = 0
    var rootNode = RootNode()
    fun parse(input: String) {

    }
}

fun parse(input: String): Document {
    return Document(ArrayList())
}