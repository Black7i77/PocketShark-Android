package com.scarfaceos.pocketshark.capture;

import java.net.InetAddress;
import java.util.Arrays;

final class Ipv4Packet {
    final byte[] raw;
    final int headerLength;
    final int totalLength;
    final int protocol;
    final int sourceAddress;
    final int destinationAddress;
    final int sourcePort;
    final int destinationPort;
    final int transportOffset;
    final int transportHeaderLength;
    final int payloadOffset;
    final int payloadLength;
    final long sequence;
    final long acknowledgement;
    final int tcpFlags;
    final boolean fragmented;

    private Ipv4Packet(byte[] raw, int headerLength, int totalLength, int protocol,
                       int sourceAddress, int destinationAddress, int sourcePort, int destinationPort,
                       int transportHeaderLength, int payloadOffset, int payloadLength,
                       long sequence, long acknowledgement, int tcpFlags, boolean fragmented) {
        this.raw = raw;
        this.headerLength = headerLength;
        this.totalLength = totalLength;
        this.protocol = protocol;
        this.sourceAddress = sourceAddress;
        this.destinationAddress = destinationAddress;
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.transportOffset = headerLength;
        this.transportHeaderLength = transportHeaderLength;
        this.payloadOffset = payloadOffset;
        this.payloadLength = payloadLength;
        this.sequence = sequence;
        this.acknowledgement = acknowledgement;
        this.tcpFlags = tcpFlags;
        this.fragmented = fragmented;
    }

    static Ipv4Packet parse(byte[] input) {
        if (input == null || input.length < 20 || ((input[0] >>> 4) & 0xf) != 4) return null;
        int headerLength = (input[0] & 0xf) * 4;
        if (headerLength < 20 || headerLength > input.length) return null;
        int declared = u16(input, 2);
        int totalLength = declared == 0 ? input.length : Math.min(input.length, declared);
        int protocol = input[9] & 0xff;
        int sourceAddress = address(input, 12);
        int destinationAddress = address(input, 16);
        int flagsFragment = u16(input, 6);
        boolean fragmented = (flagsFragment & 0x3fff) != 0;

        int sourcePort = -1;
        int destinationPort = -1;
        int transportHeaderLength = 0;
        int payloadOffset = headerLength;
        long sequence = 0;
        long acknowledgement = 0;
        int tcpFlags = 0;

        if (protocol == 6 && totalLength >= headerLength + 20) {
            sourcePort = u16(input, headerLength);
            destinationPort = u16(input, headerLength + 2);
            sequence = u32(input, headerLength + 4);
            acknowledgement = u32(input, headerLength + 8);
            transportHeaderLength = ((input[headerLength + 12] >>> 4) & 0xf) * 4;
            if (transportHeaderLength < 20 || headerLength + transportHeaderLength > totalLength) return null;
            tcpFlags = input[headerLength + 13] & 0xff;
            payloadOffset = headerLength + transportHeaderLength;
        } else if (protocol == 17 && totalLength >= headerLength + 8) {
            sourcePort = u16(input, headerLength);
            destinationPort = u16(input, headerLength + 2);
            transportHeaderLength = 8;
            payloadOffset = headerLength + 8;
        }
        int payloadLength = Math.max(0, totalLength - payloadOffset);
        return new Ipv4Packet(Arrays.copyOf(input, totalLength), headerLength, totalLength, protocol,
                sourceAddress, destinationAddress, sourcePort, destinationPort,
                transportHeaderLength, payloadOffset, payloadLength,
                sequence, acknowledgement, tcpFlags, fragmented);
    }

    FlowKey flowKey() {
        return new FlowKey(sourceAddress, destinationAddress, sourcePort, destinationPort, protocol);
    }

    byte[] payload() {
        return Arrays.copyOfRange(raw, payloadOffset, payloadOffset + payloadLength);
    }

    static byte[] addressBytes(int address) {
        return new byte[]{(byte) (address >>> 24), (byte) (address >>> 16),
                (byte) (address >>> 8), (byte) address};
    }

    static InetAddress inetAddress(int address) throws Exception {
        return InetAddress.getByAddress(addressBytes(address));
    }

    private static int address(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24) | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8) | (bytes[offset + 3] & 0xff);
    }

    private static int u16(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
    }

    private static long u32(byte[] data, int offset) {
        return ((long) (data[offset] & 0xff) << 24) | ((long) (data[offset + 1] & 0xff) << 16)
                | ((long) (data[offset + 2] & 0xff) << 8) | (long) (data[offset + 3] & 0xff);
    }
}
