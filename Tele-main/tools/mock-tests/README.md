# Bousygram offline mock tests

These checks do not authenticate with Telegram and do not start a real Fast-Link bot.

## Fast-Link proxy tests (Python)

From any working directory:

```bash
python server/fastlink-proxy/run_mock_tests.py
```

They exercise range parsing, token lifecycle, and the real FastAPI `/stream/{token}` route against a mocked Telegram CDN response. If `python-telegram-bot` is not installed, the route test stubs only that package's import-time symbols; no bot is started.

## Android stream helper tests (Kotlin CLI)

If a standalone `kotlinc` compiler is available:

```bash
kotlinc \
  app/src/main/java/com/vaydelux/bousygram/core/tdlib/server/HttpRangeParser.kt \
  app/src/main/java/com/vaydelux/bousygram/core/tdlib/server/StreamAccessRegistry.kt \
  app/src/main/java/com/vaydelux/bousygram/core/tdlib/fastlink/FastLinkUrlValidator.kt \
  app/src/main/java/com/vaydelux/bousygram/core/tdlib/auth/TdlibDatabaseKeyPolicy.kt \
  tools/mock-tests/BousygramCoreMockTest.kt \
  -include-runtime -d bousygram-core-mock-tests.jar
java -jar bousygram-core-mock-tests.jar
```

These exercise the same pure Kotlin parser, token-registry, URL-validation, and database-key-policy sources used by the app. They are not a substitute for the full Android Gradle build, instrumentation tests, or real-device playback validation.

## Run all offline-safe checks

From the repository root:

```bash
bash tools/mock-tests/run_all.sh
```

The combined runner includes the Python mock tests, Python bytecode compilation, the standalone Kotlin utility checks (when `kotlinc` is installed), and selected static source/XML checks. It does not replace Gradle compilation or device tests.
