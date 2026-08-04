package com.dorosoft.erp.identity.application.ratelimit;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Objects;

/** A canonical binary client address; its source must be the trusted proxy/container boundary. */
public final class ClientIpAddress {

    private final byte[] address;

    private ClientIpAddress(byte[] address) {
        this.address = address.clone();
    }

    public static ClientIpAddress of(InetAddress address) {
        Objects.requireNonNull(address, "address must not be null");
        return new ClientIpAddress(address.getAddress());
    }

    public byte[] bytes() {
        return address.clone();
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof ClientIpAddress other && Arrays.equals(address, other.address);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(address);
    }

    @Override
    public String toString() {
        return "ClientIpAddress[redacted]";
    }
}
