# Protected device qualification record

The `release-qualification` environment must provide the protected secret
`RELEASE_DEVICE_QUALIFICATION_JSON`. It is accepted only when it contains:

```json
{
  "status": "passed",
  "tag": "v1.2.3",
  "commit": "<40-character source SHA>",
  "nativeTdlibRuntime": "passed",
  "reviewerApproval": true,
  "evidenceUrl": "https://…",
  "apkSha256": {
    "arm32": "<64 hex characters>",
    "arm64": "<64 hex characters>",
    "universal": "<64 hex characters>"
  }
}
```

The release workflow compares all three hashes with the signed candidate
artifacts after signing. A status flag or an unbound evidence URL is not
sufficient. Hardware execution remains a required external qualification;
the workflow does not simulate Telegram, JNI, or device responses.
