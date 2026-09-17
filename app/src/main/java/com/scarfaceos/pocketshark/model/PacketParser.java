package com.scarfaceos.pocketshark.model;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Locale;

public final class PacketParser {
    private PacketParser() {}

    public static PacketEvent parse(long number, long timestampMicros,
                                    PacketEvent.Direction direction, byte[] sourceBytes) {
        byte[] raw = Arrays.copyOf(sourceBytes, sourceBytes.length);
        if (raw.length < 1) return unknown(number, timestampMicros, direction, raw, "Empty frame");
        int version = (raw[0] >>> 4) & 0x0f;
        try {
            if (version == 4) return parseIpv4(number, timestampMicros, direction, raw);
            if (version == 6) return parseIpv6(number, timestampMicros, direction, raw);
        } catch (Exception ignored) {
            return unknown(number, timestampMicros, direction, raw, "Malformed IP packet");
        }
        return unknown(number, timestampMicros, direction, raw, "Unknown network layer");
    }

    private static PacketEvent parseIpv4(long number, long time, PacketEvent.Direction direction,
                                         byte[] raw) throws Exception {
        if (raw.length < 20) return unknown(number, time, direction, raw, "Short IPv4 packet");
        int ihl = (raw[0] & 0x0f) * 4;
        if (ihl < 20 || ihl > raw.length) return unknown(number, time, direction, raw, "Invalid IPv4 header");
        int declaredLength = u16(raw, 2);
        int length = declaredLength >= ihl ? Math.min(declaredLength, raw.length) : raw.length;
        if (length != raw.length) raw = Arrays.copyOf(raw, length);
        int protocolNumber = raw[9] & 0xff;
        String src = InetAddress.getByAddress(Arrays.copyOfRange(raw, 12, 16)).getHostAddress();
        String dst = InetAddress.getByAddress(Arrays.copyOfRange(raw, 16, 20)).getHostAddress();
        return finish(number, time, direction, raw, 4, ihl, protocolNumber, src, dst);
    }

    private static PacketEvent parseIpv6(long number, long time, PacketEvent.Direction direction,
                                         byte[] raw) throws Exception {
        if (raw.length < 40) return unknown(number, time, direction, raw, "Short IPv6 packet");
        int declaredLength = 40 + u16(raw, 4);
        int length = Math.min(declaredLength, raw.length);
        if (length != raw.length) raw = Arrays.copyOf(raw, length);
        int nextHeader = raw[6] & 0xff;
        String src = InetAddress.getByAddress(Arrays.copyOfRange(raw, 8, 24)).getHostAddress();
        String dst = InetAddress.getByAddress(Arrays.copyOfRange(raw, 24, 40)).getHostAddress();
        return finish(number, time, direction, raw, 6, 40, nextHeader, src, dst);
    }

    private static PacketEvent finish(long number, long time, PacketEvent.Direction direction,
                                      byte[] raw, int version, int ipHeaderLength, int protocolNumber,
                                      String src, String dst) {
        int srcPort = -1;
        int dstPort = -1;
        int payloadOffset = ipHeaderLength;
        String protocol;
        String info;

        if (protocolNumber == 6 && raw.length >= ipHeaderLength + 20) {
            protocol = "TCP";
            srcPort = u16(raw, ipHeaderLength);
            dstPort = u16(raw, ipHeaderLength + 2);
            int tcpHeaderLength = ((raw[ipHeaderLength + 12] >>> 4) & 0x0f) * 4;
            if (tcpHeaderLength < 20) tcpHeaderLength = 20;
            payloadOffset = Math.min(raw.length, ipHeaderLength + tcpHeaderLength);
            int flags = raw[ipHeaderLength + 13] & 0xff;
            info = tcpFlags(flags) + " Seq=" + u32(raw, ipHeaderLength + 4)
                    + " Ack=" + u32(raw, ipHeaderLength + 8)
                    + appHint(raw, payloadOffset, srcPort, dstPort);
        } else if (protocolNumber == 17 && raw.length >= ipHeaderLength + 8) {
            srcPort = u16(raw, ipHeaderLength);
            dstPort = u16(raw, ipHeaderLength + 2);
            payloadOffset = ipHeaderLength + 8;
            if (srcPort == 53 || dstPort == 53) {
                protocol = "DNS";
                info = dnsInfo(raw, payloadOffset);
            } else if (srcPort == 443 || dstPort == 443) {
                protocol = "QUIC";
                info = "Encrypted QUIC/HTTP3 datagram";
            } else {
                protocol = "UDP";
                info = "Datagram, payload " + Math.max(0, raw.length - payloadOffset) + " bytes";
            }
        } else if (protocolNumber == 1 || protocolNumber == 58) {
            protocol = protocolNumber == 1 ? "ICMP" : "ICMPv6";
            int type = raw.length > ipHeaderLength ? raw[ipHeaderLength] & 0xff : -1;
            info = "Type " + type;
        } else {
            protocol = "IP:" + protocolNumber;
            info = "IP protocol " + protocolNumber;
        }

        return new PacketEvent(number, time, direction, raw, version, protocol, src, dst,
                srcPort, dstPort, raw.length, info, ipHeaderLength, ipHeaderLength, payloadOffset);
    }

    private static String tcpFlags(int flags) {
        StringBuilder out = new StringBuilder();
        if ((flags & 0x02) != 0) out.append("SYN ");
        if ((flags & 0x10) != 0) out.append("ACK ");
        if ((flags & 0x01) != 0) out.append("FIN ");
        if ((flags & 0x04) != 0) out.append("RST ");
        if ((flags & 0x08) != 0) out.append("PSH ");
        return out.length() == 0 ? "TCP" : out.toString().trim();
    }

    private static String appHint(byte[] raw, int offset, int srcPort, int dstPort) {
        if (offset >= raw.length) return "";
        int length = raw.length - offset;
        if ((srcPort == 443 || dstPort == 443) && length >= 6 && (raw[offset] & 0xff) == 22) {
            int handshake = raw[offset + 5] & 0xff;
            return handshake == 1 ? "  TLS Client Hello" : handshake == 2 ? "  TLS Server Hello" : "  TLS handshake";
        }
        if (srcPort == 80 || dstPort == 80) {
            int end = offset;
            while (end < raw.length && end - offset < 100 && raw[end] != '\r' && raw[end] != '\n') end++;
            if (end > offset) {
                String line = new String(raw, offset, end - offset).replaceAll("[^\\x20-\\x7E]", ".");
                return "  HTTP " + line;
            }
        }
        return "  Payload " + length + " bytes";
    }

    private static String dnsInfo(byte[] raw, int offset) {
        if (raw.length < offset + 12) return "Short DNS message";
        boolean response = (raw[offset + 2] & 0x80) != 0;
        String name = readDnsName(raw, offset + 12);
        return (response ? "Response" : "Query") + (name.isEmpty() ? "" : " " + name);
    }

    private static String readDnsName(byte[] raw, int position) {
        StringBuilder out = new StringBuilder();
        int labels = 0;
        while (position < raw.length && labels++ < 20) {
            int count = raw[position++] & 0xff;
            if (count == 0) break;
            if ((count & 0xc0) != 0 || position + count > raw.length) break;
            if (out.length() > 0) out.append('.');
            for (int i = 0; i < count; i++) {
                int c = raw[position++] & 0xff;
                out.append(c >= 32 && c <= 126 ? (char) c : '?');
            }
        }
        return out.toString();
    }

    private static PacketEvent unknown(long number, long time, PacketEvent.Direction direction,
                                       byte[] raw, String info) {
        return new PacketEvent(number, time, direction, raw, 0, "UNKNOWN", "—", "—",
                -1, -1, raw.length, info, 0, 0, 0);
    }

    public static int u16(byte[] data, int offset) {
        if (offset < 0 || offset + 1 >= data.length) return 0;
        return ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
    }

    public static long u32(byte[] data, int offset) {
        if (offset < 0 || offset + 3 >= data.length) return 0;
        return ((long) (data[offset] & 0xff) << 24)
                | ((long) (data[offset + 1] & 0xff) << 16)
                | ((long) (data[offset + 2] & 0xff) << 8)
                | (long) (data[offset + 3] & 0xff);
    }
}
