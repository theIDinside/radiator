package com.app.radiator

fun prepStackTrace(stackTrace: List<StackTraceElement>): String {
    val sb = StringBuilder()
    val longestFile = stackTrace.fold(0) { acc, frame -> Int
        Integer.max(acc, frame.fileName.length + "${frame.lineNumber}".length)
    }
    for(frame in stackTrace) {
        val fileAndLine = "${frame.fileName}:${frame.lineNumber}".padEnd(longestFile, padChar = ' ')
        sb.append("$fileAndLine  --  ${frame.className}::${frame.methodName}\n")
    }
    return sb.toString()
}

fun logError(module: String, ex: Throwable) {
    println("${module}: $ex\n${prepStackTrace(ex.stackTrace.toList())}")
}