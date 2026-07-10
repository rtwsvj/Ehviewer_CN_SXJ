/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class PublicAddressPolicyTest {

    @Test
    public void rejectsLocalPrivateAndNonRoutableAddresses() throws Exception {
        for (String address : new String[] {
                "0.0.0.0", "127.0.0.1", "10.0.0.1", "100.64.0.1", "169.254.1.1",
                "172.16.0.1", "192.168.1.1", "198.18.0.1", "198.51.100.1",
                "203.0.113.1", "::1", "fc00::1", "fe80::1", "2001:db8::1"
        }) {
            assertFalse(address, PublicAddressPolicy.isPublicAddress(InetAddress.getByName(address)));
        }
    }

    @Test
    public void acceptsRepresentativePublicAddresses() throws Exception {
        assertTrue(PublicAddressPolicy.isPublicAddress(InetAddress.getByName("8.8.8.8")));
        assertTrue(PublicAddressPolicy.isPublicAddress(
                InetAddress.getByName("2606:4700:4700::1111")));
    }

    @Test
    public void dnsLookupRejectsEntireResultWhenOneAddressIsPrivate() throws Exception {
        PublicAddressPolicy policy = new PublicAddressPolicy(hostname -> Arrays.asList(
                InetAddress.getByName("8.8.8.8"), InetAddress.getByName("127.0.0.1")));

        assertThrows(UnknownHostException.class, () -> policy.lookup("trusted.example"));
    }

    @Test
    public void dnsLookupRejectsEmptyResults() {
        PublicAddressPolicy policy =
                new PublicAddressPolicy(hostname -> Collections.emptyList());

        assertThrows(UnknownHostException.class, () -> policy.lookup("trusted.example"));
    }
}
