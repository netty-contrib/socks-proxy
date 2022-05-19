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
import io.netty5.util.CharsetUtil;
import io.netty5.util.NetUtil;

/**
 * Decodes {@link Buffer}s into {@link SocksCmdResponse}.
 * Before returning SocksResponse decoder removes itself from pipeline.
 */
public class SocksCmdResponseDecoder extends ByteToMessageDecoderForBuffer {

    private enum State {
        CHECK_PROTOCOL_VERSION,
        READ_CMD_HEADER,
        READ_CMD_ADDRESS
    }
    private State state = State.CHECK_PROTOCOL_VERSION;
    private SocksCmdStatus cmdStatus;
    private SocksAddressType addressType;

    @Override
    protected void decode(ChannelHandlerContext ctx, Buffer buffer) throws Exception {
        switch (state) {
            case CHECK_PROTOCOL_VERSION: {
                if (buffer.readableBytes() < 1) {
                    return;
                }
                if (buffer.readByte() != SocksProtocolVersion.SOCKS5.byteValue()) {
                    ctx.fireChannelRead(SocksCommonUtils.UNKNOWN_SOCKS_RESPONSE);
                    break;
                }
                state = State.READ_CMD_HEADER;
            }
            case READ_CMD_HEADER: {
                if (buffer.readableBytes() < 3) {
                    return;
                }
                cmdStatus = SocksCmdStatus.valueOf(buffer.readByte());
                buffer.skipReadableBytes(1); // reserved
                addressType = SocksAddressType.valueOf(buffer.readByte());
                state = State.READ_CMD_ADDRESS;
            }
            case READ_CMD_ADDRESS: {
                switch (addressType) {
                    case IPv4: {
                        if (buffer.readableBytes() < 6) {
                            return;
                        }
                        String host = NetUtil.intToIpAddress(buffer.readInt());
                        int port = buffer.readUnsignedShort();
                        ctx.fireChannelRead(new SocksCmdResponse(cmdStatus, addressType, host, port));
                        break;
                    }
                    case DOMAIN: {
                        if (buffer.readableBytes() < 1) {
                            return;
                        }
                        int fieldLength = buffer.getByte(buffer.readerOffset());
                        if (buffer.readableBytes() < 3 + fieldLength) {
                            return;
                        }
                        buffer.skipReadableBytes(1);
                        String host = buffer.readCharSequence(fieldLength, CharsetUtil.US_ASCII).toString();
                        int port = buffer.readUnsignedShort();
                        ctx.fireChannelRead(new SocksCmdResponse(cmdStatus, addressType, host, port));
                        break;
                    }
                    case IPv6: {
                        if (buffer.readableBytes() < 18) {
                            return;
                        }
                        byte[] bytes = new byte[16];
                        buffer.readBytes(bytes, 0, bytes.length);
                        String host = SocksCommonUtils.ipv6toStr(bytes);
                        int port = buffer.readUnsignedShort();
                        ctx.fireChannelRead(new SocksCmdResponse(cmdStatus, addressType, host, port));
                        break;
                    }
                    case UNKNOWN: {
                        ctx.fireChannelRead(SocksCommonUtils.UNKNOWN_SOCKS_RESPONSE);
                        break;
                    }
                    default: {
                        throw new Error();
                    }
                }
                break;
            }
            default: {
                throw new Error();
            }
        }
        ctx.pipeline().remove(this);
    }
}
