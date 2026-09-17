# PocketShark privacy policy

**Effective date:** 26 August 2026

PocketShark is an on-device network inspection and troubleshooting tool. It uses Android's local `VpnService` interface only after the user starts a capture and approves Android's VPN permission.

## Data the app accesses

While capture is active, PocketShark accesses IPv4 packets routed by Android through its local VPN interface. Packet information can include IP addresses, ports, protocol headers and payload bytes. Encrypted TLS and QUIC payloads remain encrypted.

PocketShark also stores the Android package names of apps the user selects in App Compatibility. This preference remains on the device.

## How data is used

Captured packets are processed locally to display packet lists, protocol details, statistics and hexadecimal views. PocketShark does not use captured traffic for advertising, profiling or analytics.

## Sharing and transmission

PocketShark has no developer-operated server and does not upload captured packets, app-bypass preferences or analytics. Traffic is forwarded only to the original destination requested by the user's app. Selected bypass apps use Android's normal direct connection and are not captured.

PocketShark exports a PCAP file only when the user chooses **Export PCAP** and selects a destination through Android's file picker. The user controls any later sharing of that exported file.

## Retention and deletion

The live packet list is kept in app memory, up to the app's packet limit, and is cleared when a new capture or import begins or when the app process is removed. Exported PCAP files remain wherever the user saved them and can be deleted with the device's file manager.

App Compatibility selections can be removed inside PocketShark by choosing **Manage App Bypass → Clear all**. Uninstalling PocketShark removes its locally stored preferences.

## Security and user control

PocketShark requires affirmative user consent before capture. Android displays its VPN indicator while the service is active, and capture can be stopped from the app or its persistent notification. PocketShark does not decrypt encrypted application traffic and does not provide packet injection, spoofing or traffic-modification features.

## Contact

Before publishing this policy, replace this paragraph with the verified developer support email and, if applicable, a postal address shown on the Google Play listing.

## Changes

Material changes to this policy will be reflected by updating the effective date and the policy distributed with the app or linked from its store listing.
