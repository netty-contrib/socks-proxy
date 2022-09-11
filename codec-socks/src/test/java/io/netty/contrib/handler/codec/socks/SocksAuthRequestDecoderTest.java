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

import io.netty5.buffer.Buffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SocksAuthRequestDecoderTest {

    private static final String username = "testUserName";
    private static final String password = "testPassword";

    @Test
    public void testAuthRequestDecoder() {
        SocksAuthRequest msg = new SocksAuthRequest(username, password);
        SocksAuthRequestDecoder decoder = new SocksAuthRequestDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = embedder.readInbound();
        assertEquals(username, msg.username());
        assertEquals(password, msg.password());
        assertNull(embedder.readInbound());
    }

    @Test
    public void testAuthRequestDecoderPartialSend() {
        EmbeddedChannel ch = new EmbeddedChannel(new SocksAuthRequestDecoder());
        Buffer buffer = ch.bufferAllocator().allocate(16);

        // Send username and password size
        buffer.writeByte(SocksSubnegotiationVersion.AUTH_PASSWORD.byteValue());
        buffer.writeByte((byte) username.length());
        buffer.writeBytes(username.getBytes());
        buffer.writeByte((byte) password.length());
        ch.writeInbound(buffer);

        // Check that channel is empty
        assertNull(ch.readInbound());

        // Send password
        Buffer buffer2 = ch.bufferAllocator().allocate(16);
        buffer2.writeBytes(password.getBytes());
        ch.writeInbound(buffer2);

        // Read message from channel
        SocksAuthRequest msg = ch.readInbound();

        // Check message
        assertEquals(username, msg.username());
        assertEquals(password, msg.password());

        assertFalse(ch.finishAndReleaseAll());
    }
}
