/*
 * SC local patch: generic room widgets support.
 */

package io.element.android.features.call.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.room.JoinedRoom

interface RoomWidgetsEntryPoint {
    /** Opens the list of widgets of the room (or the widget itself if there is only one). */
    fun openWidgets(sessionId: SessionId, roomId: RoomId)

    /** The first widget of the room pinned above the timeline, with a drag handle and a close button. */
    @Composable
    fun WidgetPanel(room: JoinedRoom, modifier: Modifier, onClose: () -> Unit)
}
