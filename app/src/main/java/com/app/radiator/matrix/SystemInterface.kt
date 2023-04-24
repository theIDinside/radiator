package com.app.radiator.matrix

import android.app.Application
import android.util.Log
import com.app.radiator.matrix.SDKLogging.setupLogging
import kotlinx.collections.immutable.toPersistentList
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
    if (!initialized) {
      throw SystemInterfaceException.NotInitialized()
    }
    return basePathFile.absolutePath
  }

  fun getApplicationFilePath(fileOrSubPath: String): Path {
    val p = Path(applicationDataDir())
    val cleanedInput = if (fileOrSubPath[0] == '/') {
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

sealed class SDKLogLevels(val string: String) {
  object Off : SDKLogLevels("")
  object Error : SDKLogLevels("error")
  object Warn : SDKLogLevels("warn")
  object Info : SDKLogLevels("info")
  object Debug : SDKLogLevels("debug")
  object Trace : SDKLogLevels("trace")
}

sealed class SDKModule(val moduleName: String) {
  object SDK : SDKModule("matrix_sdk")
  object SlidingSync : SDKModule("matrix_sdk::sliding_sync")
  object BaseSlidingSync : SDKModule("matrix_sdk_base::sliding_sync")
  object FFI : SDKModule("matrix_sdk_ffi")
  object UniFFIAPI : SDKModule("matrix_sdk_ffi::uniffi_api")
  object HTTPClient : SDKModule("matrix_sdk::http_client")
  object Crypto : SDKModule("matrix_sdk_crypto")
  object Sled : SDKModule("matrix_sdk_sled")

  fun level(level: SDKLogLevels): Pair<SDKModule, SDKLogLevels> = Pair(this, level)
}

fun toFilter(mod: SDKModule, level: SDKLogLevels): String? {
  return when (level) {
    SDKLogLevels.Off -> null
    else -> "${mod.moduleName}=${level.string}"
  }
}

object SDKLogging {
  private var configured = false

  private fun allModules(): List<SDKModule> =
    SDKModule::class.sealedSubclasses.map { it.objectInstance!! }.toPersistentList()
  // fun allModules(): List<Module> = persistentListOf(Module.SDK,Module.SlidingSync,Module.BaseSlidingSync,Module.FFI,Module.UniFFIAPI,Module.HTTPClient,Module.Crypto,Module.Sled)

  private var settings: MutableMap<SDKModule, SDKLogLevels> =
    allModules().associateWith { SDKLogLevels.Off }.toMutableMap()

  private fun setLoggingFor(mod: SDKModule, level: SDKLogLevels) {
    settings[mod] = level
  }

  fun setLoggingFor(settings: List<Pair<SDKModule, SDKLogLevels>>) {
    for ((module, level) in settings) {
      setLoggingFor(module, level)
    }
  }

  fun setupLogging() {
    if (configured) {
      Log.d("Logging Configuration", "Tracing already configured")
    }
    val filter = settings.map { (module, level) -> toFilter(module, level) }.filterNotNull()
      .joinToString(separator = ",")
    Log.d("Logging Configuration", "Setup tracing filter $filter")
    org.matrix.rustcomponents.sdk.setupTracing(filter)
    configured = true
  }
}

fun applicationSetup(app: Application) {

  SystemInterface.basePathFile = File(app.applicationContext.filesDir, "session")
  SDKLogging.setLoggingFor(
    listOf(
      SDKModule.SlidingSync.level(SDKLogLevels.Trace),
      SDKModule.BaseSlidingSync.level(SDKLogLevels.Trace)
    )
  )
  setupLogging()
  SystemInterface.initialized = true
}