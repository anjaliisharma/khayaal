# Phase 4 notification operations

- Mailpit inbox: `http://localhost:8025`
- SMTP inside Compose: `mailpit:1025`
- Planner cadence: 30 seconds
- Dispatcher cadence: 5 seconds
- Failed delivery backoff starts at 30 seconds and doubles up to one hour.
- A row becomes `DEAD` after five failed attempts by default.

Notification preferences are managed with `GET/PUT /api/notification-preferences`. Use `POST /api/notifications/test?channel=EMAIL` to enqueue a test message. Telegram linking starts at `POST /api/telegram/link-code`; the bot-side callback completes it through `POST /api/telegram/complete-link` with the code and chat ID.

Dose and critical stock notifications bypass quiet hours. Routine low-stock and expiry messages are deferred until quiet hours end. Discreet mode substitutes recipient-level wording and omits medicine names.

Khayaal is a reminder and inventory tool, not medical advice. Notification text never recommends or validates a dose.
