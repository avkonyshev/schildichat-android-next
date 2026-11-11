/*
 * SC local patch: generic room widgets support.
 */

package io.element.android.features.call.impl.ui

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.call.impl.utils.WebViewWidgetMessageInterceptor
import io.element.android.features.call.impl.utils.WidgetMessageInterceptor
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.widget.MatrixWidgetDriver
import io.element.android.libraries.matrix.api.widget.MatrixWidgetSettings
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID

internal data class RoomWidget(val id: String, val name: String, val rawUrl: String)

private val WIDGET_EVENT_TYPES = listOf("im.vector.modular.widgets", "m.widget")

internal suspend fun loadRoomWidgets(room: JoinedRoom): List<RoomWidget> {
    val result = mutableListOf<RoomWidget>()
    for (type in WIDGET_EVENT_TYPES) {
        val rawResult = room.getRawStateEvents(type)
        val raws = rawResult.getOrNull().orEmpty()
        Timber.d("Widgets: type=$type events=${raws.size} error=${rawResult.exceptionOrNull()}")
        for (raw in raws) {
            runCatching {
                val event = JSONObject(raw)
                val content = event.optJSONObject("content") ?: return@runCatching
                val id = event.optString("state_key").ifEmpty { return@runCatching }
                val rawUrl = buildWidgetRawUrl(content) ?: return@runCatching
                val name = content.optString("name").ifEmpty { id }
                if (result.none { it.id == id }) result += RoomWidget(id, name, rawUrl)
            }.onFailure { Timber.w(it, "Can't parse widget event") }
        }
    }
    return result
}

/** Applies the widget `data` template variables and makes sure the widget gets its `widgetId`. */
private fun buildWidgetRawUrl(content: JSONObject): String? {
    var url = content.optString("url")
    if (!(url.startsWith("https://") || url.startsWith("http://"))) return null
    content.optJSONObject("data")?.let { data ->
        data.keys().forEach { key ->
            url = url.replace("\$$key", Uri.encode(data.optString(key)))
        }
    }
    if (!url.contains("widgetId=")) {
        val hash = url.indexOf('#')
        val base = if (hash >= 0) url.substring(0, hash) else url
        val fragment = if (hash >= 0) url.substring(hash) else ""
        url = base + (if (base.contains('?')) "&" else "?") + "widgetId=\$matrix_widget_id" + fragment
    }
    return url
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WidgetViewer(
    room: JoinedRoom,
    widget: RoomWidget,
    languageTag: String?,
    userAgent: String,
    modifier: Modifier = Modifier,
) {
    var url by remember(widget.id) { mutableStateOf<String?>(null) }
    var driver by remember(widget.id) { mutableStateOf<MatrixWidgetDriver?>(null) }
    var interceptor by remember(widget.id) { mutableStateOf<WidgetMessageInterceptor?>(null) }
    var error by remember(widget.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(widget.id) {
        val settings = MatrixWidgetSettings(id = widget.id, initAfterContentLoad = true, rawUrl = widget.rawUrl)
        room.generateWidgetWebViewUrl(settings, UUID.randomUUID().toString(), languageTag, null)
            .onSuccess { url = it }
            .onFailure { error = it.message ?: "Can't build widget url" }
        room.getCustomWidgetDriver(settings)
            .onSuccess { driver = it }
            .onFailure { error = it.message ?: "Can't create widget driver" }
    }
    val currentDriver = driver
    DisposableEffect(currentDriver) { onDispose { currentDriver?.close() } }

    val currentInterceptor = interceptor
    if (currentDriver != null && currentInterceptor != null) {
        LaunchedEffect(currentDriver, currentInterceptor) {
            launch(start = CoroutineStart.UNDISPATCHED) {
                currentDriver.incomingMessages.collect { currentInterceptor.sendMessage(it) }
            }
            launch(start = CoroutineStart.UNDISPATCHED) {
                currentInterceptor.interceptedMessages.collect { currentDriver.send(it) }
            }
            currentDriver.run()
        }
    }

    Column(modifier) {
        error?.let { Text(it, modifier = Modifier.padding(8.dp)) }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    with(settings) {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        userAgentString = userAgent
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            Timber.d("WidgetJS: ${consoleMessage.message()}")
                            return true
                        }
                    }
                    interceptor = WebViewWidgetMessageInterceptor(
                        webView = this,
                        onUrlLoaded = { Timber.d("Widget page loaded") },
                        onError = { error = it ?: "Load error" },
                    )
                }
            },
            update = { webView ->
                val target = url
                if (target != null && webView.tag != target) {
                    webView.tag = target
                    webView.loadUrl(target)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}

private const val PREFS = "sc_widget_panel"
private const val DEFAULT_HEIGHT_DP = 160f
private const val MIN_HEIGHT_DP = 80f
private const val MAX_HEIGHT_DP = 600f

/** Widget pinned above the timeline, with a drag handle to change its height and a close button. */
@Composable
internal fun WidgetPanel(
    room: JoinedRoom,
    userAgent: String,
    languageTag: String?,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var heightDp by remember(room.roomId) { mutableFloatStateOf(prefs.getFloat(room.roomId.value, DEFAULT_HEIGHT_DP)) }
    var widget by remember(room.roomId) { mutableStateOf<RoomWidget?>(null) }
    var loaded by remember(room.roomId) { mutableStateOf(false) }

    LaunchedEffect(room.roomId) {
        widget = loadRoomWidgets(room).firstOrNull()
        loaded = true
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = widget?.name ?: if (loaded) "Нет виджетов" else "…",
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = CompoundIcons.Close(), contentDescription = "Закрыть")
            }
        }
        widget?.let {
            WidgetViewer(
                room = room,
                widget = it,
                languageTag = languageTag,
                userAgent = userAgent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heightDp.dp),
            )
        }
        // Drag handle
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(room.roomId) {
                    detectVerticalDragGestures(
                        onDragEnd = { prefs.edit().putFloat(room.roomId.value, heightDp).apply() },
                    ) { change, dragAmount ->
                        change.consume()
                        heightDp = (heightDp + dragAmount / density.density).coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(48.dp)
                    .height(4.dp)
                    .background(Color.Gray, RoundedCornerShape(2.dp))
            )
        }
    }
}
