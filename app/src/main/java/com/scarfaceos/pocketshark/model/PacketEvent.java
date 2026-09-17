package com.scarfaceos.pocketshark.model;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class PacketEvent {
    public enum Direction { OUT, IN, IMPORTED }

    public final long number;
    public final long timestampMicros;
    public final Direction direction;
    public final byte[] raw;
    public final int ipVersion;
    public final String protocol;
    public final String source;
    public final String destination;
    public final int sourcePort;
    public final int destinationPort;
    public final int length;
    public final String info;
    public final int ipHeaderLength;
    public final int transportOffset;
    public final int payloadOffset;

    public PacketEvent(long number, long timestampMicros, Direction direction, byte[] raw,
                       int ipVersion, String protocol, String source, String destination,
                       int sourcePort, int destinationPort, int length, String info,
                       int ipHeaderLength, int transportOffset, int payloadOffset) {
        this.number = number;
        this.timestampMicros = timestampMicros;
        this.direction = direction;
        this.raw = raw;
        this.ipVersion = ipVersion;
        this.protocol = protocol;
        this.source = source;
        this.destination = destination;
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.length = length;
        this.info = info;
        this.ipHeaderLength = ipHeaderLength;
        this.transportOffset = transportOffset;
        this.payloadOffset = payloadOffset;
    }

    public String timeLabel() {
        long millis = timestampMicros / 1000L;
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date(millis));
    }

    public String endpointSource() {
        return sourcePort >= 0 ? source + ":" + sourcePort : source;
    }

    public String endpointDestination() {
        return destinationPort >= 0 ? destination + ":" + destinationPort : destination;
    }

    public boolean matches(String rawFilter) {
        if (rawFilter == null || rawFilter.trim().isEmpty()) return true;
        String[] terms = rawFilter.toLowerCase(Locale.US).trim().split("\\s+");
        for (String term : terms) {
            boolean matched;
            if (term.startsWith("src:")) {
                matched = endpointSource().toLowerCase(Locale.US).contains(term.substring(4));
            } else if (term.startsWith("dst:")) {
                matched = endpointDestination().toLowerCase(Locale.US).contains(term.substring(4));
            } else if (term.startsWith("port:")) {
                String p = term.substring(5);
                matched = String.valueOf(sourcePort).equals(p) || String.valueOf(destinationPort).equals(p);
            } else if (term.startsWith("ip:")) {
                String ip = term.substring(3);
                matched = source.contains(ip) || destination.contains(ip);
            } else if (term.equals("in") || term.equals("out") || term.equals("imported")) {
                matched = direction.name().toLowerCase(Locale.US).equals(term);
            } else {
                String haystack = (protocol + " " + source + " " + destination + " "
                        + sourcePort + " " + destinationPort + " " + info + " " + direction)
                        .toLowerCase(Locale.US);
                matched = haystack.contains(term);
            }
            if (!matched) return false;
        }
        return true;
    }

    public String layerDescription() {
        StringBuilder out = new StringBuilder();
        out.append("Frame ").append(number).append('\n');
        out.append("  Arrival: ").append(timeLabel()).append('\n');
        out.append("  Captured length: ").append(length).append(" bytes\n");
        out.append("  Direction: ").append(direction).append("\n\n");
        out.append("Internet Protocol Version ").append(ipVersion).append('\n');
        out.append("  Source: ").append(source).append('\n');
        out.append("  Destination: ").append(destination).append('\n');
        out.append("  Header length: ").append(ipHeaderLength).append(" bytes\n\n");
        out.append(protocol).append('\n');
        if (sourcePort >= 0) {
            out.append("  Source port: ").append(sourcePort).append('\n');
            out.append("  Destination port: ").append(destinationPort).append('\n');
        }
        out.append("  Summary: ").append(info).append('\n');
        if (payloadOffset >= 0 && payloadOffset < raw.length) {
            out.append("  Payload: ").append(raw.length - payloadOffset).append(" bytes\n");
        }
        return out.toString();
    }

    public String hexDump() {
        StringBuilder out = new StringBuilder();
        for (int offset = 0; offset < raw.length; offset += 16) {
            out.append(String.format(Locale.US, "%04x  ", offset));
            for (int i = 0; i < 16; i++) {
                if (offset + i < raw.length) out.append(String.format(Locale.US, "%02x ", raw[offset + i] & 0xff));
                else out.append("   ");
                if (i == 7) out.append(' ');
            }
            out.append(" | ");
            int end = Math.min(offset + 16, raw.length);
            for (int i = offset; i < end; i++) {
                int value = raw[i] & 0xff;
                out.append(value >= 32 && value <= 126 ? (char) value : '.');
            }
            out.append('\n');
        }
        return out.toString();
    }

    public String asciiPayloadPreview() {
        if (payloadOffset < 0 || payloadOffset >= raw.length) return "";
        int count = Math.min(160, raw.length - payloadOffset);
        String value = new String(raw, payloadOffset, count, StandardCharsets.ISO_8859_1);
        return value.replaceAll("[^\\x20-\\x7E\\r\\n\\t]", ".");
    }
}
