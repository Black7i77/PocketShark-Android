package com.scarfaceos.pocketshark.capture;

import com.scarfaceos.pocketshark.model.PacketEvent;
import com.scarfaceos.pocketshark.model.PacketParser;
import com.scarfaceos.pocketshark.pcap.PcapIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;

public final class CoreSmokeTest {
    public static void main(String[] args) throws Exception {
        byte[] dnsQuery = new byte[]{
                0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x07, 'e', 'x', 'a', 'm', 'p', 'l', 'e',
                0x03, 'c', 'o', 'm', 0x00, 0x00, 0x01, 0x00, 0x01
        };
        byte[] packet = PacketBuilder.udp(0x0a580001, 0x08080808, 51000, 53, dnsQuery);
        PacketEvent event = PacketParser.parse(1, 1_700_000_000_123_000L,
                PacketEvent.Direction.OUT, packet);
        require("DNS".equals(event.protocol), "DNS protocol decode");
        require(event.info.contains("example.com"), "DNS query-name decode");
        require("10.88.0.1".equals(event.source), "source address decode");
        Ipv4Packet parsedUdp = Ipv4Packet.parse(packet);
        require(parsedUdp != null && parsedUdp.protocol == 17, "forwarder UDP parse");
        require(parsedUdp.sourcePort == 51000 && parsedUdp.destinationPort == 53,
                "forwarder UDP ports");
        require(java.util.Arrays.equals(dnsQuery, parsedUdp.payload()), "forwarder UDP payload");
        require(checksumValid(packet, 0, 20), "IPv4 header checksum");
        require(transportChecksumValid(packet, 17), "UDP checksum");

        byte[] tcpPayload = "GET / HTTP/1.1\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] tcp = PacketBuilder.tcp(0x9df0cd38, 0x0a580001, 443, 51001,
                0xf1234567L, 0x81234567L,
                PacketBuilder.TCP_PSH | PacketBuilder.TCP_ACK, 65535, tcpPayload);
        Ipv4Packet parsedTcp = Ipv4Packet.parse(tcp);
        require(parsedTcp != null && parsedTcp.protocol == 6, "forwarder TCP parse");
        require(parsedTcp.sequence == 0xf1234567L && parsedTcp.acknowledgement == 0x81234567L,
                "unsigned TCP sequence parse");
        require((parsedTcp.tcpFlags & PacketBuilder.TCP_PSH) != 0, "TCP flags");
        require(checksumValid(tcp, 0, 20), "TCP IPv4 header checksum");
        require(transportChecksumValid(tcp, 6), "TCP checksum");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PcapIo.write(output, Collections.singletonList(event));
        PcapIo.ImportResult imported = PcapIo.read(new ByteArrayInputStream(output.toByteArray()));
        require(imported.frames.size() == 1, "PCAP frame count");
        require(java.util.Arrays.equals(packet, imported.frames.get(0)), "PCAP round trip");
        System.out.println("PocketShark parser + PCAP round-trip OK");
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError("Failed: " + label);
    }

    private static boolean checksumValid(byte[] packet, int offset, int length) {
        long sum = 0;
        for (int index = 0; index < length; index += 2) {
            int high = packet[offset + index] & 0xff;
            int low = index + 1 < length ? packet[offset + index + 1] & 0xff : 0;
            sum += (high << 8) | low;
            sum = (sum & 0xffff) + (sum >>> 16);
        }
        while ((sum >>> 16) != 0) sum = (sum & 0xffff) + (sum >>> 16);
        return (sum & 0xffff) == 0xffff;
    }

    private static boolean transportChecksumValid(byte[] packet, int protocol) {
        int transportOffset = (packet[0] & 0x0f) * 4;
        int transportLength = packet.length - transportOffset;
        long sum = 0;
        for (int index = 12; index < 20; index += 2) {
            sum += ((packet[index] & 0xff) << 8) | (packet[index + 1] & 0xff);
        }
        sum += protocol;
        sum += transportLength;
        for (int index = 0; index < transportLength; index += 2) {
            int high = packet[transportOffset + index] & 0xff;
            int low = index + 1 < transportLength ? packet[transportOffset + index + 1] & 0xff : 0;
            sum += (high << 8) | low;
            sum = (sum & 0xffff) + (sum >>> 16);
        }
        while ((sum >>> 16) != 0) sum = (sum & 0xffff) + (sum >>> 16);
        return (sum & 0xffff) == 0xffff;
    }
}
