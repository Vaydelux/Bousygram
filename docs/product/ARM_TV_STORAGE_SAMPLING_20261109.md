# ARM32/ARM64 physical soak — device storage measurements

The existing `Capture-BousygramPhysicalSoak.ps1` already records process
PSS and native heap PSS without collecting media, Telegram credentials,
tokens, URLs or logcat. This addition samples **device-wide /data filesystem
free space** via read-only `adb shell df -k /data` at the same interval.

Each sample contains optional total/used/free KiB. The report includes first
and last observed free storage, delta and the number of available readings.
Malformed or OEM-unsupported `df` output remains `null` and does **not**
invalidate the memory measurement or fabricate a zero. The tool never reads
user-private directories or uninstalls data.

**Critical limitation:** `df /data` is the entire data filesystem, not
Bousygram's app data or its 200 MiB disposable-media budget. Other apps and
the OS can change free space. Results are **observations only**, never
production/device qualification.

Use the original PowerShell soak command with `-Cycles 60 -SampleSeconds 60`
for a 60-minute watch, on a physical 8 GB ARM32 TV while playing and seeking.
Compare exact APK hashes and the same content/network/device configuration.
Privacy-minimized evidence stays local in `qualification-evidence`.

Automated CI checks parser fixtures and PowerShell syntax. CI does not
connect to a real Android TV, so hardware storage growth remains unknown.
