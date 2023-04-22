package com.app.radiator.matrix.store

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.app.radiator.matrix.store.AsyncImageStorage.Cache.cacheMutex
import com.app.radiator.matrix.store.AsyncImageStorage.Cache.cachedBytes
import com.app.radiator.matrix.store.AsyncImageStorage.Cache.loadedImages
import com.app.radiator.ui.components.LoadingAnimation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.system.measureNanoTime

// we default home server to matrix.org as it's the sliding sync capable home server
// we are testing against
data class MediaMxcURI(val url: String, val homeServer: String = "matrix.org")

const val THUMBNAIL_QUERY = "?width=64&height=64&method=scale"

fun MediaMxcURI.toUrl(): URL {
    val urlString = this.url.replace("mxc://", "https://$homeServer/_matrix/media/r0/thumbnail/")
    val withQuery = "$urlString$THUMBNAIL_QUERY"
    return URL(withQuery)
}

class AsyncLoadedImage(img: ImageBitmap? = null) {
    var img = mutableStateOf(img)
}

/// N.B! We're basically assuming that a read, foo = loadedImages[id], can fail safely, if operations on map is being performed inside mutex
/// or put in another way; that all read operations on loadedImages is safe, even with write operations going on. If not,
/// we should probably pull in a 3rd party depedency for a concurrent hashmap - these kinds of deps I'm A OK with, as
/// they solve a very constrained problem.
object AsyncImageStorage {

    object Cache {
        val cacheMutex = Mutex()
        val loadedImages = HashMap<MediaMxcURI, AsyncLoadedImage>()
        var cachedBytes = 0
    }

    private fun loadImage(coroutineScope: CoroutineScope, matrixUri: MediaMxcURI): AsyncLoadedImage {
        if (loadedImages.containsKey(matrixUri)) {
            return loadedImages[matrixUri]!!
        }
        val res = loadedImages.getOrPut(matrixUri, defaultValue = { AsyncLoadedImage() })
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val url = matrixUri.toUrl()
                val bytes = url.readBytes()
                cachedBytes += bytes.size
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val asImageBitmap = bitmap.asImageBitmap()
                cacheMutex.withLock {
                    val elapsed = measureNanoTime {
                        loadedImages[matrixUri]!!.img.value = asImageBitmap
                    }
                    Log.i("Async Image Storage", "Cached bytes: $cachedBytes. HashMap insertion took $elapsed ns")
                }
            }
        }
        return res
    }

    @Composable
    fun AsyncCachedImage(
        coroutineScope: CoroutineScope,
        modifier: Modifier = Modifier,
        url: MediaMxcURI,
    ) {
        val bitmap = remember { loadImage(coroutineScope = coroutineScope, matrixUri = url) }
        if(bitmap.img.value != null) {
            Image(modifier = modifier, bitmap = bitmap.img.value!!, contentDescription = null)
        }
    }

    @Composable
    fun AsyncImageWithLoadingAnimation(modifier: Modifier, coroutineScope: CoroutineScope, url: MediaMxcURI) {
        val bitmap = remember { loadImage(coroutineScope = coroutineScope, matrixUri = url) }
        if(bitmap.img.value != null) {
            Image(modifier = modifier, bitmap = bitmap.img.value!!, contentDescription = null)
        } else {
            LoadingAnimation(size = 32.dp)
        }
    }
}