/*
 * SC local patch: generic room widgets support.
 */

package io.element.android.features.call.impl.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.zacsweers.metro.Inject
import io.element.android.compound.colors.SemanticColorsLightDark
import io.element.android.features.call.impl.di.CallBindings
import io.element.android.features.call.impl.utils.WebViewWidgetMessageInterceptor
import io.element.android.features.call.impl.utils.WidgetMessageInterceptor
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.libraries.architecture.bindings
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.designsystem.theme.ElementThemeApp
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.widget.MatrixWidgetDriver
import io.element.android.libraries.matrix.api.widget.MatrixWidgetSettings
import io.element.android.libraries.network.useragent.UserAgentProvider
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID

class WidgetActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
        const val EXTRA_ROOM_ID = "EXTRA_ROOM_ID"
    }

    @Inject lateinit var matrixClientProvider: MatrixClientProvider
    @Inject lateinit var appPreferencesStore: AppPreferencesStore
    @Inject lateinit var featureFlagService: FeatureFlagService
    @Inject lateinit var enterpriseService: EnterpriseService
    @Inject lateinit var buildMeta: BuildMeta
    @Inject lateinit var userAgentProvider: UserAgentProvider
    @Inject lateinit var languageTagProvider: LanguageTagProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindings<CallBindings>().inject(this)
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)?.let(::SessionId)
        val roomId = intent.getStringExtra(EXTRA_ROOM_ID)?.let(::RoomId)
        if (sessionId == null || roomId == null) {
            finish()
            return
        }
        setContent {
            val colors by remember { enterpriseService.semanticColorsFlow(sessionId = sessionId) }
                .collectAsState(SemanticColorsLightDark.default)
            ElementThemeApp(
                appPreferencesStore = appPreferencesStore,
                featureFlagService = featureFlagService,
                compoundLight = colors.light,
                compoundDark = colors.dark,
                buildMeta = buildMeta,
            ) {
                WidgetsScreen(sessionId, roomId)
            }
        }
    }

    @Composable
    private fun WidgetsScreen(sessionId: SessionId, roomId: RoomId) {
        var room by remember { mutableStateOf<JoinedRoom?>(null) }
        var widgets by remember { mutableStateOf<List<RoomWidget>?>(null) }
        var selected by remember { mutableStateOf<RoomWidget?>(null) }
        val languageTag = languageTagProvider.provideLanguageTag()

        LaunchedEffect(Unit) {
            val joinedRoom = matrixClientProvider.getOrRestore(sessionId).getOrNull()?.getJoinedRoom(roomId)
            room = joinedRoom
            val found = if (joinedRoom == null) emptyList() else loadRoomWidgets(joinedRoom)
            widgets = found
            if (found.size == 1) selected = found.single()
        }

        BackHandler {
            if (selected != null && (widgets?.size ?: 0) > 1) selected = null else finish()
        }

        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            val current = selected
            val joinedRoom = room
            val list = widgets
            when {
                list == null -> Text("...", modifier = Modifier.padding(16.dp))
                current != null && joinedRoom != null -> WidgetViewer(joinedRoom, current, languageTag, userAgentProvider.provide(), Modifier.fillMaxSize())
                list.isEmpty() -> Text("No widgets in this room", modifier = Modifier.padding(16.dp))
                else -> Column {
                    list.forEach { w ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = w }
                                .padding(16.dp)
                        ) {
                            Text(w.name)
                            Text(Uri.parse(w.rawUrl).host.orEmpty())
                        }
                    }
                }
            }
        }
    }
}
