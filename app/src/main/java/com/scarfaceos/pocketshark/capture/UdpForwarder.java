package com.scarfaceos.pocketshark.capture;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class UdpForwarder {
    private final PacketCaptureService service;
    private final ConcurrentHashMap<FlowKey, Session> sessions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean closed;

    UdpForwarder(PacketCaptureService service) { this.service = service; }

    void handle(Ipv4Packet packet) {
        if (closed || packet.fragmented || packet.payloadLength < 0) return;
        FlowKey key = packet.flowKey();
        Session session = sessions.get(key);
        if (session == null) {
            try {
                Session candidate = new Session(key);
                Session existing = sessions.putIfAbsent(key, candidate);
                if (existing == null) {
                    session = candidate;
                    executor.execute(candidate::receiveLoop);
                } else {
                    candidate.close();
                    session = existing;
                }
            } catch (Exception error) {
                service.reportForwardingError("UDP forward failed: " + error.getMessage());
                return;
            }
        }
        session.send(packet.payload());
    }

    void close() {
        closed = true;
        for (Session session : sessions.values()) session.close();
        sessions.clear();
        executor.shutdownNow();
    }

    private final class Session {
        private final FlowKey key;
        private final DatagramSocket socket;
        private volatile long lastUsed = System.currentTimeMillis();

        Session(FlowKey key) throws Exception {
            this.key = key;
            socket = new DatagramSocket(null);
            if (!service.protectAndBind(socket)) {
                throw new IllegalStateException("Could not protect UDP socket");
            }
            socket.bind(new InetSocketAddress(0));
            socket.connect(Ipv4Packet.inetAddress(key.destinationAddress), key.destinationPort);
            socket.setSoTimeout(1000);
        }

        synchronized void send(byte[] payload) {
            try {
                DatagramPacket datagram = new DatagramPacket(payload, payload.length);
                socket.send(datagram);
                lastUsed = System.currentTimeMillis();
            } catch (Exception error) {
                service.reportForwardingError("UDP send failed: " + error.getMessage());
                close();
            }
        }

        void receiveLoop() {
            byte[] buffer = new byte[65507];
            while (!closed && !socket.isClosed()) {
                try {
                    DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
                    socket.receive(datagram);
                    byte[] payload = new byte[datagram.getLength()];
                    System.arraycopy(datagram.getData(), datagram.getOffset(), payload, 0, datagram.getLength());
                    byte[] response = PacketBuilder.udp(key.destinationAddress, key.sourceAddress,
                            key.destinationPort, key.sourcePort, payload);
                    service.injectIncoming(response);
                    lastUsed = System.currentTimeMillis();
                } catch (SocketTimeoutException timeout) {
                    if (System.currentTimeMillis() - lastUsed > 60_000L) break;
                } catch (Exception error) {
                    break;
                }
            }
            close();
        }

        void close() {
            socket.close();
            sessions.remove(key, this);
        }
    }
}
