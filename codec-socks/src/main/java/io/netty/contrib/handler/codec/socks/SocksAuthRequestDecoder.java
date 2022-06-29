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
import io.netty5.handler.codec.ByteToMessageDecoder;
import io.netty5.util.CharsetUtil;

/**
 * Decodes {@link Buffer}s into {@link SocksAuthRequest}.
 * Before returning SocksRequest decoder removes itself from pipeline.
 */
public class SocksAuthRequestDecoder extends ByteToMessageDecoder {

    private enum State {
        CHECK_PROTOCOL_VERSION,
        READ_USERNAME,
        READ_PASSWORD
    }
    private State state = State.CHECK_PROTOCOL_VERSION;
    private String username;

    @Override
    protected void decode(ChannelHandlerContext ctx, Buffer buffer) throws Exception {
        switch (state) {
            case CHECK_PROTOCOL_VERSION: {
                if (buffer.readableBytes() < 1) {
                    return;
                }
                if (buffer.readByte() != SocksSubnegotiationVersion.AUTH_PASSWORD.byteValue()) {
                    ctx.fireChannelRead(SocksCommonUtils.UNKNOWN_SOCKS_REQUEST);
                    break;
                }
                state = State.READ_USERNAME;
            }
            case READ_USERNAME: {
                if (buffer.readableBytes() < 1) {
                    return;
                }
                int fieldLength = buffer.getByte(buffer.readerOffset());
                if (buffer.readableBytes() < 1 + fieldLength) {
                    return;
                }
                buffer.skipReadableBytes(1);
                username = buffer.readCharSequence(fieldLength, CharsetUtil.US_ASCII).toString();
                state = State.READ_PASSWORD;
            }
            case READ_PASSWORD: {
                if (buffer.readableBytes() < 1) {
                    return;
                }
                int fieldLength = buffer.getByte(buffer.readerOffset());
                if (buffer.readableBytes() < 1 + fieldLength) {
                    return;
                }
                buffer.skipReadableBytes(1);
                String password = buffer.readCharSequence(fieldLength, CharsetUtil.US_ASCII).toString();
                ctx.fireChannelRead(new SocksAuthRequest(username, password));
                break;
            }
            default: {
                throw new Error();
            }
        }
        ctx.pipeline().remove(this);
    }
}
