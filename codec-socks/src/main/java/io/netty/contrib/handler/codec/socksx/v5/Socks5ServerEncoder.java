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

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandler.Sharable;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.EncoderException;
import io.netty5.handler.codec.MessageToByteEncoderForBuffer;
import io.netty5.util.internal.StringUtil;

import static java.util.Objects.requireNonNull;

/**
 * Encodes a server-side {@link Socks5Message} into a {@link Buffer}.
 */
@Sharable
public class Socks5ServerEncoder extends MessageToByteEncoderForBuffer<Socks5Message> {

    public static final Socks5ServerEncoder DEFAULT = new Socks5ServerEncoder(Socks5AddressEncoder.DEFAULT);

    private final Socks5AddressEncoder addressEncoder;

    /**
     * Creates a new instance with the default {@link Socks5AddressEncoder}.
     */
    protected Socks5ServerEncoder() {
        this(Socks5AddressEncoder.DEFAULT);
    }

    @Override
    protected Buffer allocateBuffer(ChannelHandlerContext ctx, Socks5Message msg) {
        return ctx.bufferAllocator().allocate(256);
    }

    /**
     * Creates a new instance with the specified {@link Socks5AddressEncoder}.
     */
    public Socks5ServerEncoder(Socks5AddressEncoder addressEncoder) {
        requireNonNull(addressEncoder, "addressEncoder");

        this.addressEncoder = addressEncoder;
    }

    /**
     * Returns the {@link Socks5AddressEncoder} of this encoder.
     */
    protected final Socks5AddressEncoder addressEncoder() {
        return addressEncoder;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Socks5Message msg, Buffer out) throws Exception {
        if (msg instanceof Socks5InitialResponse) {
            encodeAuthMethodResponse((Socks5InitialResponse) msg, out);
        } else if (msg instanceof Socks5PasswordAuthResponse) {
            encodePasswordAuthResponse((Socks5PasswordAuthResponse) msg, out);
        } else if (msg instanceof Socks5CommandResponse) {
            encodeCommandResponse((Socks5CommandResponse) msg, out);
        } else {
            throw new EncoderException("unsupported message type: " + StringUtil.simpleClassName(msg));
        }
    }

    private static void encodeAuthMethodResponse(Socks5InitialResponse msg, Buffer out) {
        out.writeByte(msg.version().byteValue());
        out.writeByte(msg.authMethod().byteValue());
    }

    private static void encodePasswordAuthResponse(Socks5PasswordAuthResponse msg, Buffer out) {
        out.writeByte((byte) 0x01);
        out.writeByte(msg.status().byteValue());
    }

    private void encodeCommandResponse(Socks5CommandResponse msg, Buffer out) throws Exception {
        out.writeByte(msg.version().byteValue());
        out.writeByte(msg.status().byteValue());
        out.writeByte((byte) 0x00);

        final Socks5AddressType bndAddrType = msg.bndAddrType();
        out.writeByte(bndAddrType.byteValue());
        addressEncoder.encodeAddress(bndAddrType, msg.bndAddr(), out);

        out.writeShort((short) msg.bndPort());
    }
}
