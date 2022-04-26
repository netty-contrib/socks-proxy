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
package io.netty.contrib.handler.codec.socksx.v4;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandler.Sharable;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.MessageToByteEncoderForBuffer;
import io.netty5.util.CharsetUtil;
import io.netty5.util.NetUtil;

/**
 * Encodes a {@link Socks4CommandRequest} into a {@link Buffer}.
 */
@Sharable
public final class Socks4ClientEncoder extends MessageToByteEncoderForBuffer<Socks4CommandRequest> {

    /**
     * The singleton instance of {@link Socks4ClientEncoder}
     */
    public static final Socks4ClientEncoder INSTANCE = new Socks4ClientEncoder();

    private static final byte[] IPv4_DOMAIN_MARKER = {0x00, 0x00, 0x00, 0x01};

    private Socks4ClientEncoder() { }

    @Override
    protected Buffer allocateBuffer(ChannelHandlerContext ctx, Socks4CommandRequest msg) {
        return ctx.bufferAllocator().allocate(256);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Socks4CommandRequest msg, Buffer out) {
        out.writeByte(msg.version().byteValue());
        out.writeByte(msg.type().byteValue());
        out.writeShort((short) msg.dstPort());
        if (NetUtil.isValidIpV4Address(msg.dstAddr())) {
            out.writeBytes(NetUtil.createByteArrayFromIpAddressString(msg.dstAddr()));
            out.writeCharSequence(msg.userId(), CharsetUtil.US_ASCII);
            out.writeByte((byte) 0);
        } else {
            out.writeBytes(IPv4_DOMAIN_MARKER);
            out.writeCharSequence(msg.userId(), CharsetUtil.US_ASCII);
            out.writeByte((byte) 0);
            out.writeCharSequence(msg.dstAddr(), CharsetUtil.US_ASCII);
            out.writeByte((byte) 0);
        }
    }
}
