package com.app.radiator.matrix.timeline.html

enum class WhiteListedTag {
    HTML, // will never be a <html> tag, but we wrap it in one.
    H1, H2, H3, H4, H5, H6,
    FONT, DEL, BLOCKQUOTE,
    P, A, UL, OL, SUP, SUB,
    LI, B, I, U, STRONG, EM,
    STRIKE, CODE, HR, BR,
    DIV, TABLE, THEAD, TBODY,
    TR, TH, TD, CAPTION,
    PRE, SPAN, IMG, DETAILS, SUMMARY
}

enum class WhiteListedAttributes {
    DataMxBgColor, DataMxColor,        // font
    Color,                             // font + span
    DataMxSpoiler,                     // span
    Name, Target, Href,                // a
    Width, Height, Alt, Title, Src,    // img
    Start,                             // ol
    Class                              // code N.B!: only classes that start with `language-foo` where foo is programming language
}

typealias Attr = WhiteListedAttributes
typealias Tag = WhiteListedTag

sealed class DOMElement

class Element(val tag: Tag, val text: String?, val children: ArrayList<Element> = NoChildren) : DOMElement() {
    companion object NoChildren : ArrayList<Element>()
}