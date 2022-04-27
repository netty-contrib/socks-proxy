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
package io.netty.contrib.handler.codec.socks;

import io.netty5.buffer.api.Buffer;
import io.netty5.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import java.net.IDN;
import java.nio.CharBuffer;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocksCmdRequestTest {
    @Test
    public void testConstructorParamsAreNotNull() {
        assertThrows(NullPointerException.class, () -> new SocksCmdRequest(null, SocksAddressType.UNKNOWN, "", 1));
        assertThrows(NullPointerException.class, () -> new SocksCmdRequest(SocksCmdType.UNKNOWN, null, "", 1));
        assertThrows(NullPointerException.class,
                () -> new SocksCmdRequest(SocksCmdType.UNKNOWN, SocksAddressType.UNKNOWN, null, 1));
    }

    @Test
    public void testIPv4CorrectAddress() {
        try {
            new SocksCmdRequest(SocksCmdType.BIND, SocksAddressType.IPv4, "54.54.1111.253", 1);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testIPv6CorrectAddress() {
        try {
            new SocksCmdRequest(SocksCmdType.BIND, SocksAddressType.IPv6, "xxx:xxx:xxx", 1);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testIDNNotExceeds255CharsLimit() {
        try {
            new SocksCmdRequest(SocksCmdType.BIND, SocksAddressType.DOMAIN,
                    "παράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμή" +
                    "παράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμή" +
                    "παράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμή" +
                    "παράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμή", 1);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testHostNotEncodedForUnknown() {
        String asciiHost = "xn--e1aybc.xn--p1ai";
        short port = 10000;

        SocksCmdRequest rq = new SocksCmdRequest(SocksCmdType.BIND, SocksAddressType.UNKNOWN, asciiHost, port);
        assertEquals(asciiHost, rq.host());

        try (Buffer buffer = preferredAllocator().allocate(16)) {
            rq.encodeAsBuffer(buffer);

            buffer.readerOffset(0);
            assertEquals(SocksProtocolVersion.SOCKS5.byteValue(), buffer.readByte());
            assertEquals(SocksCmdType.BIND.byteValue(), buffer.readByte());
            assertEquals((byte) 0x00, buffer.readByte());
            assertEquals(SocksAddressType.UNKNOWN.byteValue(), buffer.readByte());
            assertFalse(buffer.readableBytes() > 0);
        }
    }

    @Test
    public void testIDNEncodeToAsciiForDomain() {
        String host = "тест.рф";
        CharBuffer asciiHost = CharBuffer.wrap(IDN.toASCII(host));
        short port = 10000;

        SocksCmdRequest rq = new SocksCmdRequest(SocksCmdType.BIND, SocksAddressType.DOMAIN, host, port);
        assertEquals(host, rq.host());

        try (Buffer buffer = preferredAllocator().allocate(32)) {
            rq.encodeAsBuffer(buffer);

            buffer.readerOffset(0);
            assertEquals(SocksProtocolVersion.SOCKS5.byteValue(), buffer.readByte());
            assertEquals(SocksCmdType.BIND.byteValue(), buffer.readByte());
            assertEquals((byte) 0x00, buffer.readByte());
            assertEquals(SocksAddressType.DOMAIN.byteValue(), buffer.readByte());
            assertEquals((byte) asciiHost.length(), buffer.readUnsignedByte());
            assertEquals(asciiHost,
                    CharBuffer.wrap(buffer.readCharSequence(asciiHost.length(), CharsetUtil.US_ASCII)));
            assertEquals(port, buffer.readUnsignedShort());
        }
    }

    @Test
    public void testValidPortRange() {
        try {
            new SocksCmdRequest(SocksCmdType.BIND, SocksAddressType.DOMAIN,
                    "παράδειγμα.δοκιμήπαράδει", 0);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }

        try {
            new SocksCmdRequest(SocksCmdType.BIND, SocksAddressType.DOMAIN,
                    "παράδειγμα.δοκιμήπαράδει", 65536);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }
}
