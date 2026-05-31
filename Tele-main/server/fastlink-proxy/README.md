# Bousygram Fast-Link Proxy

This optional server enables Bousygram's hybrid playback mode:

1. Android forwards a selected Telegram message to your own bot.
2. The bot calls Telegram Bot API `get_file`.
3. The server returns a signed `/stream/{token}` URL.
4. ExoPlayer reads that HTTPS URL with normal `Range` requests for seeking.

The server does not store media. It only stores short-lived signed tokens that map to Telegram file paths.
Android revokes the active token when playback exits/backgrounds, and the bot deletes the forwarded message plus its status/link replies after `BOT_MESSAGE_CLEANUP_SECONDS`.

## Required Setup

1. Deploy `server.py` on a small VPS.
2. Configure `.env` from `env.template`.
3. Put Nginx in front of it using `nginx.conf`.
4. Install `bousygram-proxy.service` as a systemd service.
5. Confirm `https://your-domain/health` returns `{"status":"ok"}`.
6. In Bousygram Settings, set Playback Mode to `Auto` or `Fast link` and enter the bot username without `@`.

## Privacy Note

Fast-link playback forwards the selected message to your bot. Use only a bot and VPS you control.

## Python Setup

```bash
python -m venv venv
source venv/bin/activate
pip install fastapi uvicorn httpx python-telegram-bot
python server.py
```

Keep `SIGNING_KEY` stable. Changing it invalidates active links.

## Disposal Behavior

- No video bytes are saved on the VPS.
- `/stream/{token}` is only a temporary tunnel to Telegram's CDN.
- `/revoke/{token}` removes the in-memory token when Android leaves playback.
- Bot chat messages are deleted shortly after link creation.
- If the user plays the same media again, Android forwards it again and gets a new token.
