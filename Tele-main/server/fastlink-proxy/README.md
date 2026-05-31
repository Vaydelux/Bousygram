# Bousygram Fast-Link Proxy

This optional server enables one of Bousygram's two playback paths.

## Option 1: Fast-Link Bot/VPS Proxy

Use this when you want a lightweight VPS tunnel and normal HTTPS range streaming:

1. Android forwards a selected Telegram message to your own bot.
2. The bot calls Telegram Bot API `get_file`.
3. The server returns a signed `/stream/{token}` URL.
4. ExoPlayer reads that HTTPS URL with normal `Range` requests for seeking.

The server does not store media. It only stores short-lived signed tokens that map to Telegram file paths.
Android revokes the active token when playback exits/backgrounds, and the bot deletes the forwarded message plus its status/link replies after `BOT_MESSAGE_CLEANUP_SECONDS`.

## Option 2: TDLib Local HTTP Proxy

Use this when you want the pure private path: no bot, no VPS, no forwarded messages, and everything runs inside Bousygram on the Android TV device.

This is Bousygram's `TDLib local` playback mode. The app:

- Starts warming Telegram bytes when a video card receives focus or when playback is requested.
- Uses the local TDLib-backed loopback HTTP proxy at `127.0.0.1`.
- Preflights the requested range before serving it to ExoPlayer.
- Returns a retryable "range not ready" response if Telegram has not cached the requested bytes yet.
- Uses cache-ahead while playback is active, then stops TDLib upstream work when playback exits/backgrounds.

This path is the most private and self-contained option. It is also harder to make feel instant because Telegram/TDLib controls when a requested byte range becomes locally available. The long-term fix is smarter pre-buffering and seek gating, not larger blind timeouts.

Recommended default:

- Use `Auto` for normal users: start with TDLib local, fall back to Fast-Link only when local range playback fails.
- Use `TDLib local` for maximum privacy.
- Use `Fast link` when you prefer VPS-backed HTTPS streaming.

## Required Setup

These steps are only required for Option 1. Option 2 does not need this server.

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
pip install -r requirements.txt
python server.py
```

Keep `SIGNING_KEY` stable. Changing it invalidates active links.

## VPS Build Steps

```bash
sudo useradd --system --home /opt/bousygram-proxy --shell /usr/sbin/nologin bousygram
sudo mkdir -p /opt/bousygram-proxy
sudo cp server.py requirements.txt .env.template /opt/bousygram-proxy/
sudo cp bousygram-proxy.service /etc/systemd/system/
sudo chown -R bousygram:bousygram /opt/bousygram-proxy
cd /opt/bousygram-proxy
sudo -u bousygram python3 -m venv venv
sudo -u bousygram ./venv/bin/pip install -r requirements.txt
sudo cp .env.template .env
sudo chmod 600 .env
sudo nano .env
sudo systemctl daemon-reload
sudo systemctl enable --now bousygram-proxy
curl http://127.0.0.1:8080/health
```

After Nginx and SSL are configured, test:

```bash
curl https://your-domain.example/health
```

## Disposal Behavior

- No video bytes are saved on the VPS.
- `/stream/{token}` is only a temporary tunnel to Telegram's CDN.
- `/revoke/{token}` removes the in-memory token when Android leaves playback.
- Bot chat messages are deleted shortly after link creation.
- If the user plays the same media again, Android forwards it again and gets a new token.

For many users, keep `ALLOWED_USER_IDS` as a comma-separated list of trusted Telegram user IDs. Leaving it blank allows any Telegram user who can message the bot to request a stream link.
