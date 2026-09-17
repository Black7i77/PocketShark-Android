package com.scarfaceos.pocketshark.capture;

import java.util.Objects;

final class FlowKey {
    final int sourceAddress;
    final int destinationAddress;
    final int sourcePort;
    final int destinationPort;
    final int protocol;

    FlowKey(int sourceAddress, int destinationAddress, int sourcePort, int destinationPort, int protocol) {
        this.sourceAddress = sourceAddress;
        this.destinationAddress = destinationAddress;
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.protocol = protocol;
    }

    @Override public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof FlowKey)) return false;
        FlowKey key = (FlowKey) object;
        return sourceAddress == key.sourceAddress && destinationAddress == key.destinationAddress
                && sourcePort == key.sourcePort && destinationPort == key.destinationPort
                && protocol == key.protocol;
    }

    @Override public int hashCode() {
        return Objects.hash(sourceAddress, destinationAddress, sourcePort, destinationPort, protocol);
    }
}
