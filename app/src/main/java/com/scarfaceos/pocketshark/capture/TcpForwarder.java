package com.scarfaceos.pocketshark.capture;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class TcpForwarder {
    private enum State { CONNECTING, SYN_ACK_SENT, ESTABLISHED, CLOSED }

    private final PacketCaptureService service;
    private final ConcurrentHashMap<FlowKey, Session> sessions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final SecureRandom random = new SecureRandom();
    private volatile boolean closed;

    TcpForwarder(PacketCaptureService service) { this.service = service; }

    void handle(Ipv4Packet packet) {
        if (closed || packet.fragmented) return;
        FlowKey key = packet.flowKey();
        Session session = sessions.get(key);
        boolean syn = (packet.tcpFlags & PacketBuilder.TCP_SYN) != 0;
        boolean ack = (packet.tcpFlags & PacketBuilder.TCP_ACK) != 0;

        if (session == null && syn && !ack) {
            Session candidate = new Session(key, addSequence(packet.sequence, 1));
            Session existing = sessions.putIfAbsent(key, candidate);
            if (existing == null) {
                executor.execute(candidate::connect);
                return;
            }
            session = existing;
        }
        if (session != null) session.onPacket(packet);
    }

    void close() {
        closed = true;
        for (Session session : sessions.values()) session.close();
        sessions.clear();
        executor.shutdownNow();
    }

    private static long addSequence(long value, int amount) {
        return (value + amount) & 0xffffffffL;
    }

    private final class Session {
        private final FlowKey key;
        private volatile State state = State.CONNECTING;
        private Socket socket;
        private OutputStream remoteOutput;
        private long clientNext;
        private final long serverInitial;
        private long serverNext;
        private boolean clientFinished;

        Session(FlowKey key, long clientNext) {
            this.key = key;
            this.clientNext = clientNext;
            this.serverInitial = random.nextInt() & 0xffffffffL;
            this.serverNext = serverInitial;
        }

        void connect() {
            Socket candidate = new Socket();
            try {
                if (!service.protectAndBind(candidate)) {
                    throw new IllegalStateException("Could not protect TCP socket");
                }
                candidate.setTcpNoDelay(true);
                candidate.setKeepAlive(true);
                candidate.connect(new InetSocketAddress(Ipv4Packet.inetAddress(key.destinationAddress),
                        key.destinationPort), 8000);
                synchronized (this) {
                    if (state == State.CLOSED || closed) {
                        candidate.close();
                        return;
                    }
                    socket = candidate;
                    remoteOutput = candidate.getOutputStream();
                    long synSequence = serverNext;
                    serverNext = addSequence(serverNext, 1);
                    state = State.SYN_ACK_SENT;
                    send(PacketBuilder.TCP_SYN | PacketBuilder.TCP_ACK, synSequence, clientNext, null);
                }
                executor.execute(this::readRemote);
            } catch (Exception error) {
                try {
                    candidate.close();
                    send(PacketBuilder.TCP_RST | PacketBuilder.TCP_ACK, 0, clientNext, null);
                } catch (Exception ignored) {}
                service.reportForwardingError("TCP connect failed: " + error.getMessage());
                close();
            }
        }

        synchronized void onPacket(Ipv4Packet packet) {
            if (state == State.CLOSED) return;
            if ((packet.tcpFlags & PacketBuilder.TCP_RST) != 0) {
                close();
                return;
            }
            if (state == State.CONNECTING) return;
            boolean syn = (packet.tcpFlags & PacketBuilder.TCP_SYN) != 0;
            boolean ack = (packet.tcpFlags & PacketBuilder.TCP_ACK) != 0;
            if (syn && !ack && state == State.SYN_ACK_SENT) {
                // Recover cleanly if Android retransmits a SYN before seeing our reply.
                send(PacketBuilder.TCP_SYN | PacketBuilder.TCP_ACK,
                        serverInitial, clientNext, null);
                return;
            }
            if ((packet.tcpFlags & PacketBuilder.TCP_ACK) != 0) {
                if (state == State.SYN_ACK_SENT && packet.acknowledgement == serverNext) {
                    state = State.ESTABLISHED;
                }
            }
            if (state != State.ESTABLISHED && state != State.SYN_ACK_SENT) return;

            if (packet.payloadLength > 0) {
                if (packet.sequence == clientNext) {
                    try {
                        remoteOutput.write(packet.raw, packet.payloadOffset, packet.payloadLength);
                        remoteOutput.flush();
                        clientNext = addSequence(clientNext, packet.payloadLength);
                    } catch (Exception error) {
                        send(PacketBuilder.TCP_RST | PacketBuilder.TCP_ACK, serverNext, clientNext, null);
                        close();
                        return;
                    }
                }
                send(PacketBuilder.TCP_ACK, serverNext, clientNext, null);
            }

            if ((packet.tcpFlags & PacketBuilder.TCP_FIN) != 0 && !clientFinished) {
                long expectedFin = addSequence(packet.sequence, packet.payloadLength);
                if (expectedFin == clientNext) clientNext = addSequence(clientNext, 1);
                clientFinished = true;
                try { if (socket != null) socket.shutdownOutput(); } catch (Exception ignored) {}
                send(PacketBuilder.TCP_ACK, serverNext, clientNext, null);
            }
        }

        void readRemote() {
            byte[] buffer = new byte[1200];
            try {
                InputStream input = socket.getInputStream();
                while (!closed && state != State.CLOSED) {
                    int count = input.read(buffer);
                    if (count < 0) break;
                    if (count == 0) continue;
                    byte[] payload = new byte[count];
                    System.arraycopy(buffer, 0, payload, 0, count);
                    long sequence;
                    long acknowledgement;
                    synchronized (this) {
                        if (state == State.CLOSED) return;
                        sequence = serverNext;
                        acknowledgement = clientNext;
                        serverNext = addSequence(serverNext, count);
                    }
                    send(PacketBuilder.TCP_PSH | PacketBuilder.TCP_ACK,
                            sequence, acknowledgement, payload);
                }
                synchronized (this) {
                    if (state != State.CLOSED) {
                        long finSequence = serverNext;
                        serverNext = addSequence(serverNext, 1);
                        send(PacketBuilder.TCP_FIN | PacketBuilder.TCP_ACK,
                                finSequence, clientNext, null);
                    }
                }
            } catch (Exception error) {
                if (state != State.CLOSED && !closed) {
                    send(PacketBuilder.TCP_RST | PacketBuilder.TCP_ACK, serverNext, clientNext, null);
                }
            } finally {
                close();
            }
        }

        private void send(int flags, long sequence, long acknowledgement, byte[] payload) {
            byte[] response = PacketBuilder.tcp(key.destinationAddress, key.sourceAddress,
                    key.destinationPort, key.sourcePort, sequence, acknowledgement,
                    flags, 65535, payload);
            service.injectIncoming(response);
        }

        synchronized void close() {
            if (state == State.CLOSED) return;
            state = State.CLOSED;
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            sessions.remove(key, this);
        }
    }
}
