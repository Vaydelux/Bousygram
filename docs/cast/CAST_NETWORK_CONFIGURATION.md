# Cast network configuration

## Android permissions

The app targets Android 17 / API 37. Android's current local-network guidance requires `ACCESS_LOCAL_NETWORK` for apps targeting API 37+ that accept incoming TCP connections. The permission is declared and requested at the mobile player Cast control before receiver selection. Denial prevents the LAN server from starting; the app does not turn on global cleartext traffic.

Google's Output Switcher and Cast picker guidance is still evolving. The Cast framework's in-app route button is used here; it does not remove the permission requirement for the phone's incoming media server.

## Interface and port

The server inspects active/available network link properties and selects a private IPv4 address from Wi-Fi or Ethernet. Ktor binds only to that address and an ephemeral port. The published URL is sent only to the selected Cast receiver through the Cast media load command. No port forwarding or external proxy is configured.

Guest-network client isolation, multiple VLANs, IPv6-only LANs, VPN routing, and interface changes can prevent receiver access. These conditions produce a failed load and need device feedback/retry coverage. Automatic rebinding after an IP change is not implemented.

## Cleartext policy

The Android app's existing `network_security_config` still permits cleartext only for localhost and loopback. The Cast receiver fetches the phone URL as a separate device. Whether the selected receiver accepts this HTTP progressive URL has not been tested on hardware. Do not broaden app-wide cleartext settings to work around a receiver issue.
