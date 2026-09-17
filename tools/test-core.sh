#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TASK_CLASSES="$(mktemp -d)"
trap 'rm -rf "$TASK_CLASSES"' EXIT

if command -v javac >/dev/null 2>&1; then
  TASK_JAVAC=(javac)
else
  TASK_JAVAC=(java -m jdk.compiler/com.sun.tools.javac.Main)
fi

"${TASK_JAVAC[@]}" -d "$TASK_CLASSES" \
  "$PROJECT_DIR/app/src/main/java/com/scarfaceos/pocketshark/model/PacketEvent.java" \
  "$PROJECT_DIR/app/src/main/java/com/scarfaceos/pocketshark/model/PacketParser.java" \
  "$PROJECT_DIR/app/src/main/java/com/scarfaceos/pocketshark/pcap/PcapIo.java" \
  "$PROJECT_DIR/app/src/main/java/com/scarfaceos/pocketshark/capture/FlowKey.java" \
  "$PROJECT_DIR/app/src/main/java/com/scarfaceos/pocketshark/capture/Ipv4Packet.java" \
  "$PROJECT_DIR/app/src/main/java/com/scarfaceos/pocketshark/capture/PacketBuilder.java" \
  "$PROJECT_DIR/tools/CoreSmokeTest.java"

java -cp "$TASK_CLASSES" com.scarfaceos.pocketshark.capture.CoreSmokeTest
