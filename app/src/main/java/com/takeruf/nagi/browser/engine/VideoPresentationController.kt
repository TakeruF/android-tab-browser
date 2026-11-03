package com.takeruf.nagi.browser.engine

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import android.view.View
import androidx.core.content.ContextCompat
import com.takeruf.nagi.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VideoPlayback(val playing: Boolean = false, val width: Int = 16, val height: Int = 9,
    val hasVideo: Boolean = false, val currentTime: Double = 0.0, val duration: Double = 0.0) {
    val aspectRatio: Float get() = (width.toFloat() / height.coerceAtLeast(1)).coerceIn(1f / 2.39f, 2.39f)
}

data class VideoPresentation(val view: View, val tabId: String?, val playback: VideoPlayback,
    val popup: Boolean, val control: (String) -> Unit, val exit: () -> Unit)

/** Keeps the original WebView custom view alive across fullscreen, popup and system PiP. */
class VideoPresentationController(private val activity: Activity) {
    private val mutablePresentation = MutableStateFlow<VideoPresentation?>(null)
    val presentation = mutablePresentation.asStateFlow()
    private val mutableSystemPip = MutableStateFlow(false)
    val systemPip = mutableSystemPip.asStateFlow()
    private var openTabIds = emptySet<String>()
    private var visibleTabIds = emptySet<String>()
    private var popupSourceWasHidden = false
    private data class InlineVideo(val view: View, val tabId: String, val playback: VideoPlayback,
        val prepare: () -> Unit, val restore: () -> Unit, val control: (String) -> Unit)
    private val inlineVideos = linkedMapOf<String, InlineVideo>()
    fun updateInline(view: View, tabId: String, playback: VideoPlayback, prepare: () -> Unit,
        restore: () -> Unit, control: (String) -> Unit) {
        inlineVideos[tabId] = InlineVideo(view, tabId, playback, prepare, restore, control)
        update(tabId, playback)
        refreshPip()
    }
    fun removeInline(tabId: String) {
        inlineVideos.remove(tabId)
        close(tabId)
        refreshPip()
    }
    private fun visibleInline() = visibleTabIds.mapNotNull { inlineVideos[it] }
        .firstOrNull { it.playback.playing && it.playback.hasVideo }
    private fun presentInline(video: InlineVideo) {
        if (presentation.value != null || !video.playback.playing || !video.playback.hasVideo) return
        video.prepare()
        popupSourceWasHidden = video.tabId !in visibleTabIds
        mutablePresentation.value = VideoPresentation(video.view, video.tabId, video.playback,
            popup = true, video.control, video.restore)
        refreshPip()
    }
    val popupAvailable get() = popupEnabled && openTabIds.size > 1
    fun setTabContext(openIds: Set<String>, visibleIds: Set<String>) {
        val leaving = visibleTabIds - visibleIds
        openTabIds = openIds
        visibleTabIds = visibleIds
        val current = presentation.value
        if (current == null) {
            // A tab switch needs a destination; opening Settings is not a tab switch.
            if (popupEnabled && visibleIds.isNotEmpty() && !systemPip.value) {
                leaving.mapNotNull { inlineVideos[it] }.firstOrNull {
                    it.tabId in openIds && it.playback.playing && it.playback.hasVideo
                }?.let { presentInline(it) }
            }
            refreshPip()
            return
        }
        if (!current.popup && popupEnabled && leaving.contains(current.tabId) && visibleIds.isNotEmpty()) {
            minimize(current.tabId)
        }
        val presented = presentation.value ?: return
        if (!presented.popup || systemPip.value) return
        if (presented.tabId !in visibleIds) popupSourceWasHidden = true
        if (!popupAvailable || (popupSourceWasHidden && presented.tabId in visibleIds)) {
            // Return the same video to its page without interrupting playback.
            close()
        }
    }
    var autoPipEnabled = true
        set(value) { if (field != value) { field = value; refreshPip() } }
    var popupEnabled = true
        set(value) {
            if (field == value) return
            field = value
            if (!value && presentation.value?.popup == true) close()
        }
    private val actionName = "${activity.packageName}.VIDEO_PLAYBACK"
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == actionName) presentation.value?.let { it.control(if (it.playback.playing) "pause" else "play") }
        }
    }
    init {
        ContextCompat.registerReceiver(activity, receiver, IntentFilter(actionName), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun show(view: View, tabId: String?, playback: VideoPlayback = VideoPlayback(), popup: Boolean = false,
        control: (String) -> Unit = {}, exit: () -> Unit) {
        close(pause = true)
        if (popup && !popupAvailable) { exit(); return }
        popupSourceWasHidden = tabId !in visibleTabIds
        mutablePresentation.value = VideoPresentation(view, tabId, playback, popup, control, exit)
        refreshPip()
    }
    fun update(tabId: String, playback: VideoPlayback) {
        val current = presentation.value ?: return
        if (current.tabId == tabId && current.playback != playback) {
            mutablePresentation.value = current.copy(playback = playback)
            refreshPip()
        }
    }
    fun minimize(tabId: String? = presentation.value?.tabId) {
        val current = presentation.value ?: return
        if (popupAvailable && current.tabId != null && current.tabId == tabId && current.playback.hasVideo) {
            popupSourceWasHidden = tabId !in visibleTabIds
            mutablePresentation.value = current.copy(popup = true)
        }
    }
    fun expand() { presentation.value?.let { mutablePresentation.value = it.copy(popup = false) } }
    fun close(tabId: String? = null, pause: Boolean = false) {
        val current = presentation.value ?: return
        if (tabId != null && current.tabId != tabId) return
        // Clear first: onCustomViewHidden can synchronously call onHideCustomView again.
        mutablePresentation.value = null
        if (pause) current.control("pause")
        current.exit()
        refreshPip()
    }
    // A playing inline video may just be an autoplaying page decoration. Only an
    // existing fullscreen or pop-out presentation can continue into system PiP.
    val canAutoEnter get() = autoPipEnabled && presentation.value?.let {
        it.tabId != null && it.playback.hasVideo && it.playback.playing
    } == true
    fun onUserLeave() {
        if (!supported() || !canAutoEnter) return
        if (Build.VERSION.SDK_INT < 31 && canAutoEnter && supported()) {
            runCatching { activity.enterPictureInPictureMode(params()) }
        }
    }
    fun onPipTransition() { mutableSystemPip.value = true }
    fun onPipChanged(inPip: Boolean) {
        mutableSystemPip.value = inPip
    }
    fun onStopped() {
        (presentation.value?.control ?: visibleInline()?.control)?.invoke("pause")
        if (activity.isInPictureInPictureMode) close(pause = true)
    }
    fun refreshPip() {
        if (supported()) runCatching { activity.setPictureInPictureParams(params()) }
    }
    private fun supported() = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    private fun params(): PictureInPictureParams {
        val current = presentation.value
        val builder = PictureInPictureParams.Builder()
        if (Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(canAutoEnter).setSeamlessResizeEnabled(true)
        if (current != null && current.playback.hasVideo) {
            builder.setAspectRatio(Rational((current.playback.aspectRatio * 10000).toInt(), 10000))
            val rect = Rect()
            if (current.view.getGlobalVisibleRect(rect) && !rect.isEmpty) {
                // Crop letterboxing out of the transition snapshot, including portrait fullscreen players.
                val ratio = current.playback.aspectRatio
                if (rect.width().toFloat() / rect.height() < ratio) {
                    val height = (rect.width() / ratio).toInt().coerceAtLeast(1)
                    val top = rect.centerY() - height / 2
                    rect.set(rect.left, top, rect.right, top + height)
                } else {
                    val width = (rect.height() * ratio).toInt().coerceAtLeast(1)
                    val left = rect.centerX() - width / 2
                    rect.set(left, rect.top, left + width, rect.bottom)
                }
                builder.setSourceRectHint(rect)
            }
            val playing = current.playback.playing
            val label = activity.getString(if (playing) R.string.ui_video_pause else R.string.ui_video_play)
            val pending = PendingIntent.getBroadcast(activity, 0,
                Intent(actionName).setPackage(activity.packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.setActions(listOf(RemoteAction(Icon.createWithResource(activity,
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play), label, label, pending)))
        } else builder.setActions(emptyList())
        return builder.build()
    }
    fun dispose() { close(pause = true); inlineVideos.clear(); activity.unregisterReceiver(receiver) }
}
