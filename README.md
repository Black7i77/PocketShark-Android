# PocketShark Android v0.2.0

PocketShark is a Wireshark-style packet inspector designed for an Android phone. It uses Android's approved `VpnService` interface, so live capture does not require root. All inspection stays on the device.

## What works in v0.2.0

- Live IPv4 capture from apps on the phone
- Improved user-space TCP and UDP forwarding, bound to the active Wi-Fi or mobile-data network
- Per-app compatibility bypass for apps that reject or do not work through local VPN inspection
- Automatic recovery when the underlying Wi-Fi/mobile network changes
- TCP, UDP, DNS, QUIC and ICMP identification
- Wireshark-style packet list with direction, endpoints, protocol, length and summary
- Filters such as `tcp`, `dns`, `port:443`, `src:10.88`, `dst:8.8.8.8`, `in` and `out`
- Packet-layer detail, payload preview, and hex/ASCII view
- Protocol and endpoint statistics
- Import classic PCAP files using RAW-IP or Ethernet link types
- Export DLT_RAW PCAP files that desktop Wireshark can open
- Foreground capture notification with a Stop action
- Android 15/16 edge-to-edge layout handling so headers remain below the status bar
- Clear local-VPN consent disclosure and a Google Play privacy-policy draft
- No advertising, analytics, cloud upload, packet injection, spoofing or traffic modification

## Important Android boundaries

PocketShark follows the same analysis workflow as Wireshark, but Android does not expose a laptop-style promiscuous Wi-Fi interface to ordinary apps. Live mode sees traffic from this phone through a local VPN interface. It does not see every other device on the Wi-Fi network.

In v0.2.0:

- live capture is IPv4; IPv6 bypasses PocketShark and continues normally
- TCP/UDP forwarding is a compatibility-focused user-space implementation; unusual protocols may still need app bypass
- ICMP can be decoded from PCAP files, but live ICMP is not forwarded
- encrypted TLS/QUIC traffic stays encrypted
- PCAPNG, hundreds of desktop Wireshark dissectors, reassembly and TLS key import are planned work
- starting PocketShark disconnects any other active VPN because Android permits one VPN service per user/profile

If a particular app loses its connection during capture, open **App Compatibility**, select that app, save, then stop and restart capture. The app will use its normal direct connection and will not appear in PocketShark's capture.

## Build on Kali Linux

The build uses Android API 36 and Java 17 or newer. The script detects the Android SDK at `~/.local/share/android-sdk`, `~/Android/Sdk`, `ANDROID_SDK_ROOT`, or `ANDROID_HOME`.

```bash
cd ~/Downloads/PocketShark-Android-v0.2.0
chmod +x build.sh install-phone.sh tools/test-core.sh
./build.sh
```

The APK is created at:

```text
PocketShark-Android-v0.2.0-debug.apk
```

## Install on the phone

Unlock the phone, enable USB debugging, connect a USB data cable, accept the Android debugging prompt, then run:

```bash
./install-phone.sh
```

Or install directly:

```bash
adb install -r PocketShark-Android-v0.2.0-debug.apk
```

On first capture Android shows its standard VPN permission screen. This is expected. A key icon remains visible while capture is active.

## Safe-use scope

Use PocketShark only on your own phone and networks or captures you are authorised to inspect. This project is an observation and troubleshooting tool. It deliberately contains no packet-injection or attack features.

## Before a Google Play submission

Replace the contact placeholder in `PRIVACY_POLICY.md`, publish that policy at a public URL, complete Google's Data Safety and VpnService declarations, record the required short VpnService demonstration, create a release signing key and upload a signed Android App Bundle rather than the debug APK.
