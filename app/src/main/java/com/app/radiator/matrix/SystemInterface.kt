package com.app.radiator.matrix

import android.app.Application
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.plus
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path

sealed class SystemInterfaceException(message: String) : Exception(message) {
    class NotInitialized : SystemInterfaceException("System Interface not initialized")
}

/**
 * Provides an interface for common and shared resources. `SystemInterface` is an API
 * that can be accessed from anywhere and provide things like files, application context data,
 * and is an interface between the application and the underlying system(s) like coroutines,
 * file system, etc.
 */
object SystemInterface {
    lateinit var basePathFile: File
    var initialized = false

    fun applicationDataDir(): String {
        if(!initialized) {
            throw SystemInterfaceException.NotInitialized()
        }
        return basePathFile.absolutePath
    }

    fun getApplicationFilePath(fileOrSubPath: String): Path {
        val p = Path(applicationDataDir())
        val cleanedInput = if(fileOrSubPath[0] == '/') {
            fileOrSubPath.substring(1)
        } else {
            fileOrSubPath
        }
        return p.resolve(cleanedInput)
    }

    fun appCoroutineScope(): CoroutineScope {
        return MainScope() + CoroutineName("radiator")
    }
}


fun applicationSetup(app: Application) {
    SystemInterface.basePathFile = File(app.applicationContext.filesDir, "session")
    SystemInterface.initialized = true
}