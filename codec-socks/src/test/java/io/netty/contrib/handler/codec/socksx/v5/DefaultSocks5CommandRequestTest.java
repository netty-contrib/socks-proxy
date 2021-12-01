/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.contrib.handler.codec.socksx.v5;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class DefaultSocks5CommandRequestTest {
    @Test
    public void testConstructorParamsAreNotNull() {
        assertThrows(NullPointerException.class,
                () -> new DefaultSocks5CommandRequest(null, Socks5AddressType.DOMAIN, "", 1));
        assertThrows(NullPointerException.class,
                () -> new DefaultSocks5CommandRequest(Socks5CommandType.CONNECT, null, "", 1));
        assertThrows(NullPointerException.class,
                () -> new DefaultSocks5CommandRequest(Socks5CommandType.CONNECT, Socks5AddressType.DOMAIN, null, 1));
    }

    @Test
    public void testIPv4CorrectAddress() {
        assertThrows(IllegalArgumentException.class, () -> new DefaultSocks5CommandRequest(
                Socks5CommandType.BIND, Socks5AddressType.IPv4, "54.54.1111.253", 1));
    }

    @Test
    public void testIPv6CorrectAddress() {
        assertThrows(IllegalArgumentException.class, () -> new DefaultSocks5CommandRequest(
                Socks5CommandType.BIND, Socks5AddressType.IPv6, "xxx:xxx:xxx", 1));
    }

    @Test
    public void testIDNNotExceeds255CharsLimit() {
        assertThrows(IllegalArgumentException.class, () -> new DefaultSocks5CommandRequest(
                Socks5CommandType.BIND, Socks5AddressType.DOMAIN,
                "παράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμή" +
                        "παράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμή" +
                        "παράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμή" +
                        "παράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμή", 1));
    }

    @Test
    public void testValidPortRange() {
        assertThrows(IllegalArgumentException.class, () -> new DefaultSocks5CommandRequest(
                Socks5CommandType.BIND, Socks5AddressType.DOMAIN, "παράδειγμα.δοκιμήπαράδει", -1));
        assertThrows(IllegalArgumentException.class, () -> new DefaultSocks5CommandRequest(
                Socks5CommandType.BIND, Socks5AddressType.DOMAIN, "παράδειγμα.δοκιμήπαράδει", 65536));

        new DefaultSocks5CommandRequest(Socks5CommandType.BIND, Socks5AddressType.DOMAIN,
                "παράδειγμα.δοκιμήπαράδει", 0);
        new DefaultSocks5CommandRequest(Socks5CommandType.BIND, Socks5AddressType.DOMAIN,
                "παράδειγμα.δοκιμήπαράδει", 65535);
    }
}
