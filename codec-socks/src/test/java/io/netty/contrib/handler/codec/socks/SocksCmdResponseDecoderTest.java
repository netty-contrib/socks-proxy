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

import io.netty5.channel.embedded.EmbeddedChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocksCmdResponseDecoderTest {
    private static final Logger logger = LoggerFactory.getLogger(SocksCmdResponseDecoderTest.class);

    private static void testSocksCmdResponseDecoderWithDifferentParams(
            SocksCmdStatus cmdStatus, SocksAddressType addressType, String host, int port) {
        logger.debug("Testing cmdStatus: " + cmdStatus + " addressType: " + addressType);
        SocksResponse msg = new SocksCmdResponse(cmdStatus, addressType, host, port);
        SocksCmdResponseDecoder decoder = new SocksCmdResponseDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        if (addressType == SocksAddressType.UNKNOWN) {
            assertTrue(embedder.readInbound() instanceof UnknownSocksResponse);
        } else {
            msg = embedder.readInbound();
            assertEquals(((SocksCmdResponse) msg).cmdStatus(), cmdStatus);
            if (host != null) {
                assertEquals(((SocksCmdResponse) msg).host(), host);
            }
            assertEquals(((SocksCmdResponse) msg).port(), port);
        }
        assertNull(embedder.readInbound());
    }

    /**
     * Verifies that sent socks messages are decoded correctly.
     */
    @Test
    public void testSocksCmdResponseDecoder() {
        for (SocksCmdStatus cmdStatus : SocksCmdStatus.values()) {
            for (SocksAddressType addressType : SocksAddressType.values()) {
                testSocksCmdResponseDecoderWithDifferentParams(cmdStatus, addressType, null, 0);
            }
        }
    }

    /**
     * Verifies that invalid bound host will fail with IllegalArgumentException during encoding.
     */
    @Test
    public void testInvalidAddress() {
        assertThrows(IllegalArgumentException.class,
            () -> testSocksCmdResponseDecoderWithDifferentParams(
                SocksCmdStatus.SUCCESS, SocksAddressType.IPv4, "1", 80));
    }

    /**
     * Verifies that send socks messages are decoded correctly when bound host and port are set.
     */
    @Test
    public void testSocksCmdResponseDecoderIncludingHost() {
        for (SocksCmdStatus cmdStatus : SocksCmdStatus.values()) {
            testSocksCmdResponseDecoderWithDifferentParams(cmdStatus, SocksAddressType.IPv4,
                    "127.0.0.1", 80);
            testSocksCmdResponseDecoderWithDifferentParams(cmdStatus, SocksAddressType.DOMAIN,
                    "testDomain.com", 80);
            testSocksCmdResponseDecoderWithDifferentParams(cmdStatus, SocksAddressType.IPv6,
                    "2001:db8:85a3:42:1000:8a2e:370:7334", 80);
            testSocksCmdResponseDecoderWithDifferentParams(cmdStatus, SocksAddressType.IPv6,
                    "1111:111:11:1:0:0:0:1", 80);
        }
    }
}
