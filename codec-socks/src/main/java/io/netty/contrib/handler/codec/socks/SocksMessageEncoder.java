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
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.MessageToByteEncoder;

/**
 * Encodes an {@link SocksMessage} into a {@link Buffer}.
 * {@link MessageToByteEncoder} implementation.
 * Use this with {@link SocksInitRequest}, {@link SocksInitResponse}, {@link SocksAuthRequest},
 * {@link SocksAuthResponse}, {@link SocksCmdRequest} and {@link SocksCmdResponse}
 */
public class SocksMessageEncoder extends MessageToByteEncoder<SocksMessage> {

    @Override
    protected Buffer allocateBuffer(ChannelHandlerContext ctx, SocksMessage msg) {
        return ctx.bufferAllocator().allocate(256);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void encode(ChannelHandlerContext ctx, SocksMessage msg, Buffer out) {
        msg.encodeAsBuffer(out);
    }

    @Override
    public boolean isSharable() {
        return true;
    }
}
