# Bousygram Fast-Link Proxy

This optional server enables Bousygram's hybrid playback mode:

1. Android forwards a selected Telegram message to your own bot.
2. The bot calls Telegram Bot API `get_file`.
3. The server returns a signed `/stream/{token}` URL.
4. ExoPlayer reads that HTTPS URL with normal `Range` requests for seeking.

The server does not store media. It only stores short-lived signed tokens that map to Telegram file paths.

## Required Setup

1. Deploy `server.py` on a small VPS.
2. Configure `.env` from `.env.template`.
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
