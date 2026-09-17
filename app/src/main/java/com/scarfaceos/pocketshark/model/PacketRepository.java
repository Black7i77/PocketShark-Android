package com.scarfaceos.pocketshark.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class PacketRepository {
    public interface Listener { void onPacketsChanged(); }

    private static final PacketRepository INSTANCE = new PacketRepository();
    private static final int MAX_PACKETS = 5000;

    private final ArrayList<PacketEvent> packets = new ArrayList<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    private PacketRepository() {}

    public static PacketRepository get() { return INSTANCE; }

    public PacketEvent add(long timestampMicros, PacketEvent.Direction direction, byte[] raw) {
        PacketEvent event = PacketParser.parse(sequence.incrementAndGet(), timestampMicros, direction, raw);
        synchronized (packets) {
            packets.add(event);
            if (packets.size() > MAX_PACKETS) packets.remove(0);
        }
        for (Listener listener : listeners) listener.onPacketsChanged();
        return event;
    }

    public void addImported(List<byte[]> frames, List<Long> timestamps) {
        for (int i = 0; i < frames.size(); i++) {
            long time = i < timestamps.size() ? timestamps.get(i) : System.currentTimeMillis() * 1000L;
            add(time, PacketEvent.Direction.IMPORTED, frames.get(i));
        }
    }

    public List<PacketEvent> snapshot() {
        synchronized (packets) { return Collections.unmodifiableList(new ArrayList<>(packets)); }
    }

    public PacketEvent find(long number) {
        synchronized (packets) {
            for (PacketEvent event : packets) if (event.number == number) return event;
        }
        return null;
    }

    public void clear() {
        synchronized (packets) { packets.clear(); }
        sequence.set(0);
        for (Listener listener : listeners) listener.onPacketsChanged();
    }

    public void addListener(Listener listener) { listeners.addIfAbsent(listener); }
    public void removeListener(Listener listener) { listeners.remove(listener); }
}
