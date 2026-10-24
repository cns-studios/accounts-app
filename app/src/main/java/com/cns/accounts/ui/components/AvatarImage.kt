package com.cns.accounts.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.cns.accounts.R
import com.cns.accounts.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * The profile avatar: the account's uploaded image (auth-v2 `profile.avatar`, a public storage
 * URL) once it has loaded, [placeholder] before that or when there is none. The default
 * placeholder is a neutral user glyph: the design export's sample picture used to stand in here,
 * which looked like a stale avatar on accounts that never had one (or had theirs removed).
 */
@Composable
fun AvatarImage(
    url: String?,
    modifier: Modifier = Modifier,
    placeholder: @Composable (Modifier) -> Unit = { placeholderModifier -> AvatarPlaceholder(placeholderModifier) },
) {
    var bitmap by remember(url) { mutableStateOf(url?.let(AvatarCache::peek)) }
    LaunchedEffect(url) {
        if (url != null && bitmap == null) bitmap = AvatarCache.load(url)
    }
    val description = stringResource(R.string.profile_avatar)
    val shown = bitmap
    if (shown != null) {
        Image(shown, contentDescription = description, contentScale = ContentScale.Crop, modifier = modifier.clip(CircleShape))
    } else {
        placeholder(modifier.semantics { contentDescription = description })
    }
}

@Composable
private fun AvatarPlaceholder(modifier: Modifier) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.elevatedSurface),
        contentAlignment = Alignment.Center,
    ) {
        TokenIcon(TokenIconKind.User, tint = colors.mutedSurface, modifier = Modifier.fillMaxSize(0.42f))
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
