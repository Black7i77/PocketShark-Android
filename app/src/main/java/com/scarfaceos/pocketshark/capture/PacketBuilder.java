package com.scarfaceos.pocketshark.capture;

import java.util.concurrent.atomic.AtomicInteger;

final class PacketBuilder {
    static final int TCP_FIN = 0x01;
    static final int TCP_SYN = 0x02;
    static final int TCP_RST = 0x04;
    static final int TCP_PSH = 0x08;
    static final int TCP_ACK = 0x10;

    private static final AtomicInteger IDENTIFICATION = new AtomicInteger(1);

    private PacketBuilder() {}

    static byte[] tcp(int sourceAddress, int destinationAddress, int sourcePort, int destinationPort,
                      long sequence, long acknowledgement, int flags, int window, byte[] payload) {
        int payloadLength = payload == null ? 0 : payload.length;
        byte[] packet = new byte[20 + 20 + payloadLength];
        ipv4Header(packet, packet.length, 6, sourceAddress, destinationAddress);
        int offset = 20;
        put16(packet, offset, sourcePort);
        put16(packet, offset + 2, destinationPort);
        put32(packet, offset + 4, sequence);
        put32(packet, offset + 8, acknowledgement);
        packet[offset + 12] = (byte) (5 << 4);
        packet[offset + 13] = (byte) flags;
        put16(packet, offset + 14, window);
        put16(packet, offset + 16, 0);
        put16(packet, offset + 18, 0);
        if (payloadLength > 0) System.arraycopy(payload, 0, packet, 40, payloadLength);
        put16(packet, offset + 16, transportChecksum(packet, 20, 20 + payloadLength,
                sourceAddress, destinationAddress, 6));
        return packet;
    }

    static byte[] udp(int sourceAddress, int destinationAddress, int sourcePort, int destinationPort,
                      byte[] payload) {
        int payloadLength = payload == null ? 0 : payload.length;
        int udpLength = 8 + payloadLength;
        byte[] packet = new byte[20 + udpLength];
        ipv4Header(packet, packet.length, 17, sourceAddress, destinationAddress);
        int offset = 20;
        put16(packet, offset, sourcePort);
        put16(packet, offset + 2, destinationPort);
        put16(packet, offset + 4, udpLength);
        put16(packet, offset + 6, 0);
        if (payloadLength > 0) System.arraycopy(payload, 0, packet, 28, payloadLength);
        int checksum = transportChecksum(packet, 20, udpLength, sourceAddress, destinationAddress, 17);
        put16(packet, offset + 6, checksum == 0 ? 0xffff : checksum);
        return packet;
    }

    private static void ipv4Header(byte[] packet, int totalLength, int protocol,
                                   int sourceAddress, int destinationAddress) {
        packet[0] = 0x45;
        packet[1] = 0;
        put16(packet, 2, totalLength);
        put16(packet, 4, IDENTIFICATION.getAndIncrement() & 0xffff);
        put16(packet, 6, 0x4000);
        packet[8] = 64;
        packet[9] = (byte) protocol;
        put16(packet, 10, 0);
        put32(packet, 12, sourceAddress & 0xffffffffL);
        put32(packet, 16, destinationAddress & 0xffffffffL);
        put16(packet, 10, checksum(packet, 0, 20));
    }

    private static int transportChecksum(byte[] packet, int offset, int length,
                                         int sourceAddress, int destinationAddress, int protocol) {
        long sum = 0;
        sum += (sourceAddress >>> 16) & 0xffff;
        sum += sourceAddress & 0xffff;
        sum += (destinationAddress >>> 16) & 0xffff;
        sum += destinationAddress & 0xffff;
        sum += protocol & 0xff;
        sum += length & 0xffff;
        for (int i = 0; i + 1 < length; i += 2) {
            sum += ((packet[offset + i] & 0xff) << 8) | (packet[offset + i + 1] & 0xff);
            sum = (sum & 0xffff) + (sum >>> 16);
        }
        if ((length & 1) != 0) sum += (packet[offset + length - 1] & 0xff) << 8;
        while ((sum >>> 16) != 0) sum = (sum & 0xffff) + (sum >>> 16);
        return (int) (~sum) & 0xffff;
    }

    private static int checksum(byte[] bytes, int offset, int length) {
        long sum = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            sum += ((bytes[offset + i] & 0xff) << 8) | (bytes[offset + i + 1] & 0xff);
            sum = (sum & 0xffff) + (sum >>> 16);
        }
        if ((length & 1) != 0) sum += (bytes[offset + length - 1] & 0xff) << 8;
        while ((sum >>> 16) != 0) sum = (sum & 0xffff) + (sum >>> 16);
        return (int) (~sum) & 0xffff;
    }

    private static void put16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 8);
        data[offset + 1] = (byte) value;
    }

    private static void put32(byte[] data, int offset, long value) {
        data[offset] = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }
}
