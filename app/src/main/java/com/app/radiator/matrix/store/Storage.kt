package com.app.radiator.matrix.store

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Deflater
import java.util.zip.Inflater

internal fun compressData(bytes: ByteArray): Pair<ByteArray, Int> {
  val compressor = Deflater()
  compressor.setLevel(Deflater.BEST_COMPRESSION)
  compressor.setInput(bytes)
  compressor.finish()
  val writeStream = ByteArrayOutputStream()
  val tmp = ByteArray(4 * 1024)
  writeStream.use {
    while (!compressor.finished()) {
      val size = compressor.deflate(tmp)
      writeStream.write(tmp, 0, size)
    }
  }
  return Pair(writeStream.toByteArray(), bytes.size)
}


data class AvatarDataKey(val userId: String, val avatarUrl: String)
data class CompressedAvatarData(
  val avatarUrl: String,
  val data: ByteArray,
  val uncompressedSize: Int,
) {

  fun decompress(): ImageBitmap {
    val inflater = Inflater()
    inflater.setInput(data, 0, data.size)
    val output = ByteArray(uncompressedSize)
    inflater.inflate(output)
    inflater.end()
    return BitmapFactory.decodeByteArray(output, 0, output.size).asImageBitmap()
  }
}

class AvatarDataStorage(val filePath: String) {
  private val data: HashMap<String, HashMap<String, CompressedAvatarData>> = HashMap()
  fun getRoom(roomId: String): HashMap<String, CompressedAvatarData>? {
    return data[roomId]
  }

  fun serialize(roomId: String, map: HashMap<String, CompressedAvatarData>) {
    data[roomId] = map
  }
}

class AvatarBitmapManager(
  val storage: AvatarDataStorage,
) {
  val keepAliveCount = 5
  private val liveData: HashMap<String, HashMap<String, ImageBitmap>> = HashMap()
  fun getRoom(roomId: String): HashMap<String, ImageBitmap>? {
    if (liveData.containsKey(roomId)) return liveData[roomId]
    storage.getRoom(roomId)?.let {
      if (liveData.size >= keepAliveCount) {
        var anyRoomId: String? = null
        for ((k, v) in liveData) {
          anyRoomId = k
          break
        }
        liveData.remove(anyRoomId!!)
      }
      val map = HashMap<String, ImageBitmap>()
      for ((userId, rawData) in it) {
        map[userId] = rawData.decompress()
      }
      liveData[roomId] = map
      return map
    }
    return null
  }

  fun saveRoomAvatarData(roomId: String, data: HashMap<AvatarDataKey, ImageBitmap>) {
    val map = HashMap<String, CompressedAvatarData>()
    for ((avatarDataKey, bitmap) in data) {
      val (userId, url) = avatarDataKey
      val bitmap = bitmap.asAndroidBitmap()
      val byteArray = ByteBuffer.allocate(bitmap.byteCount)
      bitmap.copyPixelsToBuffer(byteArray)
      val (bytes, len) = compressData(byteArray.array())
      val compressed =
        CompressedAvatarData(avatarUrl = url, data = bytes, uncompressedSize = len)
      map[userId] = compressed
    }
    storage.serialize(roomId, map)
  }
}