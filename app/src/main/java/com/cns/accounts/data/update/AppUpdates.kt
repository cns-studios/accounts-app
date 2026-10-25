package com.cns.accounts.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.annotation.StringRes
import com.cns.accounts.BuildConfig
import com.cns.accounts.R
import com.cns.accounts.data.push.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Where the update check is; [AppSettingsScreen][com.cns.accounts.ui.settings.AppSettingsScreen] shows it. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    data class Installing(val release: AppRelease) : UpdateState
    data class Failed(@param:StringRes val message: Int, val release: AppRelease? = null) : UpdateState
}

/**
 * Updates from the GitHub releases of this app (CI publishes a signed APK and its SHA-256 for
 * every version tag). Checked once a day in the background ([UpdateCheckWorker]), when the app
 * comes to the foreground at most every [ForegroundCheckMillis], and from App Settings. An update
 * is downloaded into the app's cache, its SHA-256 compared with the release's, and handed to
 * Android's package installer, which also insists the APK is signed with this app's key.
 * Off in debug and `.local` builds ([BuildConfig.UPDATES_ENABLED]).
 */
class AppUpdates(private val context: Context, private val notifier: Notifier, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("cns_updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    val enabled: Boolean get() = BuildConfig.UPDATES_ENABLED && current != null
    private val current = AppVersion.parse(BuildConfig.VERSION_NAME)

    /** The Check for updates button. */
    fun checkNow() {
        if (!enabled || job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Checking
            _state.value = runCatching { latest() }.fold(
                onSuccess = { release -> release?.let(UpdateState::Available) ?: UpdateState.UpToDate },
                onFailure = { UpdateState.Failed(R.string.update_check_failed) },
            )
        }
    }

    /** The app came to the foreground. */
    fun checkIfDue() {
        if (!enabled || job?.isActive == true) return
        if (System.currentTimeMillis() - prefs.getLong(LastCheckKey, 0) < ForegroundCheckMillis) return
        job = scope.launch { runCatching { latest() }.getOrNull()?.let { _state.value = UpdateState.Available(it) } }
    }

    /** The daily background check: notifies once per new version. */
    suspend fun checkInBackground() {
        if (!enabled) return
        val release = latest() ?: return
        if (_state.value !is UpdateState.Downloading && _state.value !is UpdateState.Installing) _state.value = UpdateState.Available(release)
        if (prefs.getString(NotifiedKey, null) != release.tag) {
            notifier.appUpdate(release.version.toString())
            prefs.edit().putString(NotifiedKey, release.tag).apply()
        }
    }

    /** Install update: download, verify, hand to the system installer. */
    fun install(release: AppRelease) {
        if (!enabled || job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            val apk = try {
                download(release)
            } catch (e: ChecksumMismatchException) {
                _state.value = UpdateState.Failed(R.string.update_checksum_failed, release)
                return@launch
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(R.string.update_download_failed, release)
                return@launch
            }
            _state.value = UpdateState.Installing(release)
            runCatching { commit(apk) }.onFailure { _state.value = UpdateState.Failed(R.string.update_install_failed, release) }
        }
    }

    /** From [InstallResultReceiver]: the installer gave up (cancelled, storage, signature, ...). */
    fun onInstallFailed() {
        val release = (_state.value as? UpdateState.Installing)?.release
        _state.value = UpdateState.Failed(R.string.update_install_failed, release)
    }

    /** The newest release when it is newer than this build, else null. */
    private suspend fun latest(): AppRelease? {
        val json = withContext(Dispatchers.IO) { JSONObject(get(BuildConfig.UPDATE_RELEASES_URL).decodeToString()) }
        prefs.edit().putLong(LastCheckKey, System.currentTimeMillis()).apply()
        val release = AppRelease.fromGitHub(json)?.takeIf { current != null && it.version > current }
        // Up to date (the update was installed): the update notification has served its purpose.
        if (release == null) notifier.dismissAppUpdate()
        return release
    }

    private suspend fun download(release: AppRelease): File = withContext(Dispatchers.IO) {
        val expected = release.sha256
            ?: release.sha256Url?.let { AppRelease.parseChecksumFile(get(it).decodeToString()) }
            ?: throw ChecksumMismatchException()
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "cns-accounts-${release.version}.apk")
        val connection = open(release.apkUrl)
        try {
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            val actual = sha256Hex { digest ->
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            done += read
                            if (total > 0) _state.value = UpdateState.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (actual != expected) {
                file.delete()
                throw ChecksumMismatchException()
            }
            file
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun commit(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
            // Mutable: the installer adds its status extras to it.
            val pending = PendingIntent.getBroadcast(context, sessionId, callback, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    }

    private fun open(url: String): HttpURLConnection {
        require(AppRelease.isTrusted(url) || url == BuildConfig.UPDATE_RELEASES_URL) { "untrusted update URL" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
            setRequestProperty("User-Agent", "CNSAccounts/${BuildConfig.VERSION_NAME}")
        }
    }

    private fun get(url: String): ByteArray {
        val connection = open(url)
        try {
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private class ChecksumMismatchException : IOException("update checksum mismatch")

    private companion object {
        const val LastCheckKey = "last_check"
        const val NotifiedKey = "notified_tag"
        const val ForegroundCheckMillis = 12 * 60 * 60 * 1000L
    }
}
