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

import io.netty.buffer.ByteBuf;
import io.netty5.handler.codec.DecoderException;
import io.netty5.util.CharsetUtil;
import io.netty5.util.NetUtil;

/**
 * Decodes a SOCKS5 address field into its string representation.
 *
 * @see Socks5CommandRequestDecoder
 * @see Socks5CommandResponseDecoder
 */
public interface Socks5AddressDecoder {

    Socks5AddressDecoder DEFAULT = new Socks5AddressDecoder() {

        private static final int IPv6_LEN = 16;

        @Override
        public String decodeAddress(Socks5AddressType addrType, ByteBuf in) throws Exception {
            int readableBytes = in.readableBytes();
            if (addrType == Socks5AddressType.IPv4) {
                if (readableBytes < 4) {
                    return null;
                }
                return NetUtil.intToIpAddress(in.readInt());
            }
            if (addrType == Socks5AddressType.DOMAIN) {
                if (readableBytes < 1) {
                    return null;
                }
                final int length = in.getUnsignedByte(in.readerIndex());
                if (readableBytes - 1 < length) {
                    return null;
                }
                in.skipBytes(1);
                final String domain = in.toString(in.readerIndex(), length, CharsetUtil.US_ASCII);
                in.skipBytes(length);
                return domain;
            }
            if (addrType == Socks5AddressType.IPv6) {
                if (readableBytes < IPv6_LEN) {
                    return null;
                }
                if (in.hasArray()) {
                    final int readerIdx = in.readerIndex();
                    in.readerIndex(readerIdx + IPv6_LEN);
                    return NetUtil.bytesToIpAddress(in.array(), in.arrayOffset() + readerIdx, IPv6_LEN);
                } else {
                    byte[] tmp = new byte[IPv6_LEN];
                    in.readBytes(tmp);
                    return NetUtil.bytesToIpAddress(tmp);
                }
            } else {
                throw new DecoderException("unsupported address type: " + (addrType.byteValue() & 0xFF));
            }
        }
    };

    /**
     * Decodes a SOCKS5 address field into its string representation.
     *
     * @param addrType the type of the address
     * @param in the input buffer which contains the SOCKS5 address field at its reader index
     * @return the address or {@code null} if not enough bytes are readable yet.
     */
    String decodeAddress(Socks5AddressType addrType, ByteBuf in) throws Exception;
}
