/*
 * SC local patch: generic room widgets support.
 */

package io.element.android.features.call.impl

import android.content.Context
import android.content.Intent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.call.api.RoomWidgetsEntryPoint
import io.element.android.features.call.impl.ui.WidgetActivity
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId

@ContributesBinding(AppScope::class)
class DefaultRoomWidgetsEntryPoint(
    @ApplicationContext private val context: Context,
    private val userAgentProvider: io.element.android.libraries.network.useragent.UserAgentProvider,
    private val languageTagProvider: io.element.android.features.call.impl.ui.LanguageTagProvider,
) : RoomWidgetsEntryPoint {
    override fun openWidgets(sessionId: SessionId, roomId: RoomId) {
        context.startActivity(
            Intent(context, WidgetActivity::class.java)
                .putExtra(WidgetActivity.EXTRA_SESSION_ID, sessionId.value)
                .putExtra(WidgetActivity.EXTRA_ROOM_ID, roomId.value)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    @androidx.compose.runtime.Composable
    override fun WidgetPanel(
        room: io.element.android.libraries.matrix.api.room.JoinedRoom,
        modifier: androidx.compose.ui.Modifier,
        onClose: () -> Unit,
    ) {
        io.element.android.features.call.impl.ui.WidgetPanel(
            room = room,
            userAgent = userAgentProvider.provide(),
            languageTag = languageTagProvider.provideLanguageTag(),
            modifier = modifier,
            onClose = onClose,
        )
    }
}
