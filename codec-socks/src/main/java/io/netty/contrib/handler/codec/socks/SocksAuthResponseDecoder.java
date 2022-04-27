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
import io.netty5.handler.codec.ByteToMessageDecoderForBuffer;

/**
 * Decodes {@link Buffer}s into {@link SocksAuthResponse}.
 * Before returning SocksResponse decoder removes itself from pipeline.
 */
public class SocksAuthResponseDecoder extends ByteToMessageDecoderForBuffer {

    private enum State {
        CHECK_PROTOCOL_VERSION,
        READ_AUTH_RESPONSE
    }

    private State state = State.CHECK_PROTOCOL_VERSION;

    @Override
    protected void decode(ChannelHandlerContext ctx, Buffer buffer)
            throws Exception {
        switch (state) {
            case CHECK_PROTOCOL_VERSION: {
                if (buffer.readableBytes() < 1) {
                    return;
                }
                if (buffer.readByte() != SocksSubnegotiationVersion.AUTH_PASSWORD.byteValue()) {
                    ctx.fireChannelRead(SocksCommonUtils.UNKNOWN_SOCKS_RESPONSE);
                    break;
                }
                state = State.READ_AUTH_RESPONSE;
            }
            case READ_AUTH_RESPONSE: {
                if (buffer.readableBytes() < 1) {
                    return;
                }
                SocksAuthStatus authStatus = SocksAuthStatus.valueOf(buffer.readByte());
                ctx.fireChannelRead(new SocksAuthResponse(authStatus));
                break;
            }
            default: {
                throw new Error();
            }
        }
        ctx.pipeline().remove(this);
    }
}
