# Release Configuration

Required `production-signing` environment secrets:

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`
- `RELEASE_CERT_SHA256`

Required `release-qualification` environment variables:

- `RELEASE_DEVICE_QUALIFICATION_STATUS=passed`
- `RELEASE_DEVICE_QUALIFICATION_URL=https://...`

Optional Firebase variables are documented in `FIREBASE_TEST_LAB.md`. Configure protected GitHub environments and required reviewers before enabling release use.
