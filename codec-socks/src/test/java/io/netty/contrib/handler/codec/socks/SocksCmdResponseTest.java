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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocksCmdResponseTest {
    @Test
    public void testConstructorParamsAreNotNull() {
        assertThrows(NullPointerException.class, () -> new SocksCmdResponse(null, SocksAddressType.UNKNOWN));
        assertThrows(NullPointerException.class, () -> new SocksCmdResponse(SocksCmdStatus.UNASSIGNED, null));
    }

    /**
     * Verifies content of the response when domain is not specified.
     */
    @Test
    public void testEmptyDomain() {
        SocksCmdResponse socksCmdResponse = new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.DOMAIN);
        assertNull(socksCmdResponse.host());
        assertEquals(0, socksCmdResponse.port());
        try (Buffer buffer = preferredAllocator().allocate(20)) {
            socksCmdResponse.encodeAsBuffer(buffer);
            byte[] expected = {
                    0x05, // version
                    0x00, // success reply
                    0x00, // reserved
                    0x03, // address type domain
                    0x01, // length of domain
                    0x00, // domain value
                    0x00, // port value
                    0x00
            };
            assertByteBufEquals(expected, buffer);
        }
    }

    /**
     * Verifies content of the response when IPv4 address is specified.
     */
    @Test
    public void testIPv4Host() {
        SocksCmdResponse socksCmdResponse = new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.IPv4,
                "127.0.0.1", 80);
        assertEquals("127.0.0.1", socksCmdResponse.host());
        assertEquals(80, socksCmdResponse.port());
        try (Buffer buffer = preferredAllocator().allocate(20)) {
            socksCmdResponse.encodeAsBuffer(buffer);
            byte[] expected = {
                    0x05, // version
                    0x00, // success reply
                    0x00, // reserved
                    0x01, // address type IPv4
                    0x7F, // address 127.0.0.1
                    0x00,
                    0x00,
                    0x01,
                    0x00, // port
                    0x50
            };
            assertByteBufEquals(expected, buffer);
        }
    }

    /**
     * Verifies that empty domain is allowed Response.
     */
    @Test
    public void testEmptyBoundAddress() {
        SocksCmdResponse socksCmdResponse = new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.DOMAIN,
                "", 80);
        assertEquals("", socksCmdResponse.host());
        assertEquals(80, socksCmdResponse.port());
        try (Buffer buffer = preferredAllocator().allocate(20)) {
            socksCmdResponse.encodeAsBuffer(buffer);
            byte[] expected = {
                    0x05, // version
                    0x00, // success reply
                    0x00, // reserved
                    0x03, // address type domain
                    0x00, // domain length
                    0x00, // port
                    0x50
            };
            assertByteBufEquals(expected, buffer);
        }
    }

    @Test
    public void testHostNotEncodedForUnknown() {
        String asciiHost = "xn--e1aybc.xn--p1ai";
        short port = 10000;

        SocksCmdResponse rs = new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.UNKNOWN, asciiHost, port);
        assertEquals(asciiHost, rs.host());

        try (Buffer buffer = preferredAllocator().allocate(16)) {
            rs.encodeAsBuffer(buffer);

            buffer.readerOffset(0);
            assertEquals(SocksProtocolVersion.SOCKS5.byteValue(), buffer.readByte());
            assertEquals(SocksCmdStatus.SUCCESS.byteValue(), buffer.readByte());
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

        SocksCmdResponse rs = new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.DOMAIN, host, port);
        assertEquals(host, rs.host());

        try (Buffer buffer = preferredAllocator().allocate(32)) {
            rs.encodeAsBuffer(buffer);

            buffer.readerOffset(0);
            assertEquals(SocksProtocolVersion.SOCKS5.byteValue(), buffer.readByte());
            assertEquals(SocksCmdStatus.SUCCESS.byteValue(), buffer.readByte());
            assertEquals((byte) 0x00, buffer.readByte());
            assertEquals(SocksAddressType.DOMAIN.byteValue(), buffer.readByte());
            assertEquals((byte) asciiHost.length(), buffer.readUnsignedByte());
            assertEquals(asciiHost,
                    CharBuffer.wrap(buffer.readCharSequence(asciiHost.length(), CharsetUtil.US_ASCII)));
            assertEquals(port, buffer.readUnsignedShort());
        }
    }

    /**
     * Verifies that Response cannot be constructed with invalid IP.
     */
    @Test
    public void testInvalidBoundAddress() {
        assertThrows(IllegalArgumentException.class,
            () -> new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.IPv4, "127.0.0", 1000));
    }

    private static void assertByteBufEquals(byte[] expected, Buffer actual) {
        byte[] actualBytes = new byte[actual.readableBytes()];
        actual.readBytes(actualBytes, 0, actualBytes.length);
        assertEquals(expected.length, actualBytes.length, "Generated response has incorrect length");
        assertArrayEquals(expected, actualBytes, "Generated response differs from expected");
    }

    @Test
    public void testValidPortRange() {
        try {
            new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.IPv4, "127.0.0", 0);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }

        try {
            new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.IPv4, "127.0.0", 65536);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }
}
