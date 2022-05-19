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
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.ByteToMessageDecoderForBuffer;
import io.netty5.handler.codec.DecoderException;
import io.netty5.handler.codec.DecoderResult;
import io.netty.contrib.handler.codec.socksx.SocksVersion;

import static java.util.Objects.requireNonNull;

/**
 * Decodes a single {@link Socks5CommandResponse} from the inbound {@link Buffer}s.
 * On successful decode, this decoder will forward the received data to the next handler, so that
 * other handler can remove or replace this decoder later.  On failed decode, this decoder will
 * discard the received data, so that other handler closes the connection later.
 */
public class Socks5CommandResponseDecoder extends ByteToMessageDecoderForBuffer {

    private enum State {
        INIT,
        SUCCESS,
        FAILURE
    }

    private final Socks5AddressDecoder addressDecoder;
    private State state = State.INIT;
    public Socks5CommandResponseDecoder() {
        this(Socks5AddressDecoder.DEFAULT);
    }

    public Socks5CommandResponseDecoder(Socks5AddressDecoder addressDecoder) {
        this.addressDecoder = requireNonNull(addressDecoder, "addressDecoder");
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, Buffer in) throws Exception {
        try {
            switch (state) {
            case INIT: {
                if (in.readableBytes() < 6) {
                    return;
                }
                int readerIndex = in.readerOffset();
                final byte version = in.readByte();
                if (version != SocksVersion.SOCKS5.byteValue()) {
                    throw new DecoderException(
                            "unsupported version: " + version + " (expected: " + SocksVersion.SOCKS5.byteValue() + ')');
                }
                final Socks5CommandStatus status = Socks5CommandStatus.valueOf(in.readByte());
                in.skipReadableBytes(1); // Reserved
                final Socks5AddressType addrType = Socks5AddressType.valueOf(in.readByte());
                final String addr = addressDecoder.decodeAddress(addrType, in);
                if (addr == null || in.readableBytes() < 2) {
                    in.readerOffset(readerIndex);
                    return;
                }
                final int port = in.readUnsignedShort();

                ctx.fireChannelRead(new DefaultSocks5CommandResponse(status, addrType, addr, port));
                state = State.SUCCESS;
            }
            case SUCCESS: {
                int readableBytes = actualReadableBytes();
                if (readableBytes > 0) {
                    ctx.fireChannelRead(in.readSplit(readableBytes));
                }
                break;
            }
            case FAILURE: {
                in.skipReadableBytes(actualReadableBytes());
                break;
            }
            }
        } catch (Exception e) {
            fail(ctx, e);
        }
    }

    private void fail(ChannelHandlerContext ctx, Exception cause) {
        if (!(cause instanceof DecoderException)) {
            cause = new DecoderException(cause);
        }

        state = State.FAILURE;

        Socks5Message m = new DefaultSocks5CommandResponse(
                Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4, null, 0);
        m.setDecoderResult(DecoderResult.failure(cause));
        ctx.fireChannelRead(m);
    }
}
