# PocketShark changelog

## v0.2.0

- Binds TCP and UDP forwarding sockets to the active non-VPN Wi-Fi or mobile network.
- Tracks underlying-network changes and restores pass-through routing automatically.
- Adds an App Compatibility picker so selected apps can bypass inspection and remain online.
- Resends TCP SYN/ACK replies when Android retransmits a connection request.
- Adds clearer UDP forwarding errors and stronger packet/checksum smoke tests.
- Adds an explicit local-VPN data disclosure before capture begins.
- Fixes header and packet-detail content overlapping Android 15/16 system bars.
- Improves typography, packet-detail navigation and on-screen connection explanations.
- Updates the Android package version to `0.2.0` (`versionCode 2`).

## v0.1.0

- Initial IPv4 TCP/UDP local-VPN capture engine.
- Packet list, filters, statistics, packet details and classic PCAP import/export.
