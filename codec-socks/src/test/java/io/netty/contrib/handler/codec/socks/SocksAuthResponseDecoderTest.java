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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SocksAuthResponseDecoderTest {
    private static final Logger logger = LoggerFactory.getLogger(SocksAuthResponseDecoderTest.class);

    private static void testSocksAuthResponseDecoderWithDifferentParams(SocksAuthStatus authStatus) {
        logger.debug("Testing SocksAuthResponseDecoder with authStatus: " + authStatus);
        SocksAuthResponse msg = new SocksAuthResponse(authStatus);
        SocksAuthResponseDecoder decoder = new SocksAuthResponseDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = embedder.readInbound();
        assertSame(msg.authStatus(), authStatus);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testSocksCmdResponseDecoder() {
        for (SocksAuthStatus authStatus: SocksAuthStatus.values()) {
            testSocksAuthResponseDecoderWithDifferentParams(authStatus);
        }
    }
}
