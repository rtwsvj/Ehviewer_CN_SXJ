/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.client;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import okhttp3.Dns;

/** DNS policy for WebView bridge requests: every resolved address must be globally routable. */
public final class PublicAddressPolicy implements Dns {

    private final Dns delegate;

    public PublicAddressPolicy(Dns delegate) {
        if (delegate == null) {
            throw new NullPointerException("delegate == null");
        }
        this.delegate = delegate;
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        List<InetAddress> addresses = delegate.lookup(hostname);
        if (addresses == null || addresses.isEmpty()) {
            throw new UnknownHostException("No addresses for " + hostname);
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new UnknownHostException("Refusing non-public address for " + hostname);
            }
        }
        return addresses;
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            int third = bytes[2] & 0xff;
            if (first == 0 || first == 10 || first == 127 || first >= 224
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)
                    || (first == 192 && second == 0 && (third == 0 || third == 2))
                    || (first == 198 && (second == 18 || second == 19))
                    || (first == 198 && second == 51 && third == 100)
                    || (first == 203 && second == 0 && third == 113)) {
                return false;
            }
            return true;
        }

        if (bytes.length == 16) {
            int first = bytes[0] & 0xff;
            // fc00::/7 unique-local addresses.
            if ((first & 0xfe) == 0xfc) {
                return false;
            }
            // 2001:db8::/32 documentation range.
            if (first == 0x20 && (bytes[1] & 0xff) == 0x01
                    && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8) {
                return false;
            }
            return true;
        }
        return false;
    }
}
