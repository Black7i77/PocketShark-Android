package com.scarfaceos.pocketshark.pcap;

import com.scarfaceos.pocketshark.model.PacketEvent;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PcapIo {
    public static final class ImportResult {
        public final List<byte[]> frames = new ArrayList<>();
        public final List<Long> timestampsMicros = new ArrayList<>();
        public int skipped;
        public int linkType;
    }

    private PcapIo() {}

    public static ImportResult read(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(input));
        int magic = in.readInt();
        boolean little;
        boolean nanos;
        if (magic == 0xa1b2c3d4) { little = false; nanos = false; }
        else if (magic == 0xd4c3b2a1) { little = true; nanos = false; }
        else if (magic == 0xa1b23c4d) { little = false; nanos = true; }
        else if (magic == 0x4d3cb2a1) { little = true; nanos = true; }
        else throw new IOException("Only classic PCAP files are supported in v0.2.0 (not PCAPNG yet).");

        read16(in, little);
        read16(in, little);
        read32(in, little);
        read32(in, little);
        read32(in, little);
        int linkType = (int) read32(in, little);

        ImportResult result = new ImportResult();
        result.linkType = linkType;
        while (true) {
            try {
                long seconds = read32(in, little);
                long fraction = read32(in, little);
                long captured = read32(in, little);
                read32(in, little);
                if (captured < 0 || captured > 10_000_000L) throw new IOException("Invalid PCAP frame length");
                byte[] bytes = new byte[(int) captured];
                in.readFully(bytes);
                byte[] ip = toRawIp(bytes, linkType);
                if (ip == null) {
                    result.skipped++;
                    continue;
                }
                result.frames.add(ip);
                result.timestampsMicros.add(seconds * 1_000_000L + (nanos ? fraction / 1000L : fraction));
            } catch (EOFException end) {
                break;
            }
        }
        return result;
    }

    public static void write(OutputStream output, List<PacketEvent> packets) throws IOException {
        BufferedOutputStream out = new BufferedOutputStream(output);
        write32le(out, 0xa1b2c3d4L);
        write16le(out, 2);
        write16le(out, 4);
        write32le(out, 0);
        write32le(out, 0);
        write32le(out, 65535);
        write32le(out, 101); // DLT_RAW: packets begin with their IP header.
        for (PacketEvent packet : packets) {
            long seconds = packet.timestampMicros / 1_000_000L;
            long micros = packet.timestampMicros % 1_000_000L;
            write32le(out, seconds);
            write32le(out, micros);
            write32le(out, packet.raw.length);
            write32le(out, packet.raw.length);
            out.write(packet.raw);
        }
        out.flush();
    }

    private static byte[] toRawIp(byte[] frame, int linkType) {
        if (linkType == 101 || linkType == 228 || linkType == 229) return frame;
        if (linkType != 1 || frame.length < 14) return null;
        int etherType = ((frame[12] & 0xff) << 8) | (frame[13] & 0xff);
        int offset = 14;
        if (etherType == 0x8100 && frame.length >= 18) {
            etherType = ((frame[16] & 0xff) << 8) | (frame[17] & 0xff);
            offset = 18;
        }
        if (etherType != 0x0800 && etherType != 0x86dd) return null;
        return Arrays.copyOfRange(frame, offset, frame.length);
    }

    private static int read16(DataInputStream in, boolean little) throws IOException {
        int a = in.readUnsignedByte();
        int b = in.readUnsignedByte();
        return little ? a | (b << 8) : (a << 8) | b;
    }

    private static long read32(DataInputStream in, boolean little) throws IOException {
        long a = in.readUnsignedByte();
        long b = in.readUnsignedByte();
        long c = in.readUnsignedByte();
        long d = in.readUnsignedByte();
        return little ? a | (b << 8) | (c << 16) | (d << 24)
                : (a << 24) | (b << 16) | (c << 8) | d;
    }

    private static void write16le(OutputStream out, long value) throws IOException {
        out.write((int) value & 0xff);
        out.write((int) (value >>> 8) & 0xff);
    }

    private static void write32le(OutputStream out, long value) throws IOException {
        out.write((int) value & 0xff);
        out.write((int) (value >>> 8) & 0xff);
        out.write((int) (value >>> 16) & 0xff);
        out.write((int) (value >>> 24) & 0xff);
    }
}
