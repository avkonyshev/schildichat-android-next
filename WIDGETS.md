# Matrix room widgets on Android: mini apps and business buttons in chat

This fork adds support for Matrix [room widgets](https://spec.matrix.org/latest/client-server-api/#widget-api)
(the `im.vector.modular.widgets` state events, as used by Element Web/Desktop) to SchildiChat Android Next.
Upstream does not show or open generic room widgets.

## What is a widget

A widget is a web page embedded in a Matrix room, similar to a **Telegram Mini App (WebApp)**. You host a page,
add it to a room, and it opens inside the chat on Android. It talks to the room through the Matrix Widget API,
so it can show data and trigger actions without any change to the client.

## Use cases

- **Business buttons in chat:** approving orders, purchase requests and **expense / payment requests**
  ("Approve" / "Reject"), leave requests, incident acknowledgement.
- **Buttons that call your backend** (CRM, ERP, 1C, home automation) as the logged-in Matrix user.
  The user is verified with Matrix OpenID, so the backend does not need its own login.
- **Dashboards, forms, polls, checklists** shown next to the conversation.

## What it does

- **Widget badge.** If the room has widgets, an icon appears in the room top bar, to the left of the call button.
  A red badge shows the number of widgets. The count is refreshed every 30 seconds.
- **Inline widget panel.** Tapping the icon opens a panel above the timeline showing the room's first widget.
  The panel height can be changed with the drag handle (80–600 dp) and is remembered per room.
  A close button hides the panel.
- **Full-screen widget.** The room details screen has a "Widgets" entry which opens a widget in a full-screen activity.
- **Readable timeline events.** Widget state events are shown in the timeline as
  "Widget X was added / removed / changed by user" instead of being hidden or shown as an unknown event.

## Notes for widget authors

- Widgets run in a WebView and talk to the client through the Matrix Widget API (`postMessage`).
- On Android WebView `window.parent === window`, so a widget must ignore echoes of its own messages
  and should repeat `content_loaded` if it does not receive a response.
- Widget URLs are expected to use `https`. Cleartext HTTP is subject to the app's normal network security config.

## Building

Build as any other SchildiChat Next variant, see [docs/_developer_onboarding.md](docs/_developer_onboarding.md).
For example, a debug APK:

```bash
./gradlew assembleFdroidScDefaultDebug
```

The APK is written to `app/build/outputs/apk/fdroidScDefault/debug/`.

## Where the code is

| Area | File |
| --- | --- |
| Widget loading, WebView, inline panel | `features/call/impl/.../ui/WidgetPanel.kt` |
| Full-screen widget | `features/call/impl/.../ui/WidgetActivity.kt` |
| Entry point from room details | `features/call/api/.../RoomWidgetsEntryPoint.kt`, `features/call/impl/.../DefaultRoomWidgetsEntryPoint.kt` |
| Badge and panel in the room screen | `features/messages/impl/.../MessagesNode.kt`, `MessagesView.kt` |
| Readable widget events | `features/messages/impl/.../factories/event/TimelineItemContentFactory.kt` |
| Widget state access | `libraries/matrix/api/.../room/JoinedRoom.kt`, `libraries/matrix/impl/.../room/JoinedRustRoom.kt` |

## License

Same as upstream: AGPL-3.0.
