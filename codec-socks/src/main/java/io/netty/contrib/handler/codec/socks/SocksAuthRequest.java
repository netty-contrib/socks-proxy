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
import io.netty5.util.CharsetUtil;

import java.nio.charset.StandardCharsets;
import java.nio.charset.CharsetEncoder;

import static java.util.Objects.requireNonNull;

/**
 * An socks auth request.
 *
 * @see SocksAuthResponse
 * @see SocksAuthRequestDecoder
 */
public final class SocksAuthRequest extends SocksRequest {
    private static final SocksSubnegotiationVersion SUBNEGOTIATION_VERSION = SocksSubnegotiationVersion.AUTH_PASSWORD;
    private final String username;
    private final String password;

    public SocksAuthRequest(String username, String password) {
        super(SocksRequestType.AUTH);
        requireNonNull(username, "username");
        requireNonNull(password, "password");

        final CharsetEncoder asciiEncoder = CharsetUtil.encoder(StandardCharsets.US_ASCII);
        if (!asciiEncoder.canEncode(username) || !asciiEncoder.canEncode(password)) {
            throw new IllegalArgumentException(
                    "username: " + username + " or password: **** values should be in pure ascii");
        }
        if (username.length() > 255) {
            throw new IllegalArgumentException("username: " + username + " exceeds 255 char limit");
        }
        if (password.length() > 255) {
            throw new IllegalArgumentException("password: **** exceeds 255 char limit");
        }
        this.username = username;
        this.password = password;
    }

    /**
     * Returns username that needs to be authenticated
     *
     * @return username that needs to be authenticated
     */
    public String username() {
        return username;
    }

    /**
     * Returns password that needs to be validated
     *
     * @return password that needs to be validated
     */
    public String password() {
        return password;
    }

    @Override
    public void encodeAsBuffer(Buffer buffer) {
        buffer.writeByte(SUBNEGOTIATION_VERSION.byteValue());
        buffer.writeByte((byte) username.length());
        buffer.writeCharSequence(username, StandardCharsets.US_ASCII);
        buffer.writeByte((byte) password.length());
        buffer.writeCharSequence(password, StandardCharsets.US_ASCII);
    }
}
