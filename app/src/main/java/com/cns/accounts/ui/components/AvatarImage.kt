package com.cns.accounts.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.cns.accounts.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * The profile avatar: the account's uploaded image (auth-v2 `profile.avatar`, a public storage
 * URL) once it has loaded, the bundled placeholder before that or when there is none.
 */
@Composable
fun AvatarImage(url: String?, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf(url?.let(AvatarCache::peek)) }
    LaunchedEffect(url) {
        if (url != null && bitmap == null) bitmap = AvatarCache.load(url)
    }
    val description = stringResource(R.string.profile_avatar)
    val shown = bitmap
    if (shown != null) {
        Image(shown, contentDescription = description, contentScale = ContentScale.Crop, modifier = modifier.clip(CircleShape))
    } else {
        Image(painterResource(R.drawable.profile_avatar), contentDescription = description, modifier = modifier.clip(CircleShape))
    }
}

/** A tiny in-memory image cache; avatars are the app's only remote images. */
private object AvatarCache {
    private val cache = LruCache<String, ImageBitmap>(4)

    fun peek(url: String): ImageBitmap? = cache.get(url)

    suspend fun load(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            try {
                connection.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            } finally {
                connection.disconnect()
            }
        }.getOrNull()?.also { cache.put(url, it) }
    }
}
