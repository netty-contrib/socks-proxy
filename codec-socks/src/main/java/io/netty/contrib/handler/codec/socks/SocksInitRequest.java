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

import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * An socks init request.
 *
 * @see SocksInitResponse
 * @see SocksInitRequestDecoder
 */
public final class SocksInitRequest extends SocksRequest {
    private final List<SocksAuthScheme> authSchemes;

    public SocksInitRequest(List<SocksAuthScheme> authSchemes) {
        super(SocksRequestType.INIT);
        requireNonNull(authSchemes, "authSchemes");
        this.authSchemes = authSchemes;
    }

    /**
     * Returns the List&lt;{@link SocksAuthScheme}&gt; of this {@link SocksInitRequest}
     *
     * @return The List&lt;{@link SocksAuthScheme}&gt; of this {@link SocksInitRequest}
     */
    public List<SocksAuthScheme> authSchemes() {
        return Collections.unmodifiableList(authSchemes);
    }

    @Override
    public void encodeAsBuffer(Buffer buffer) {
        buffer.writeByte(protocolVersion().byteValue());
        buffer.writeByte((byte) authSchemes.size());
        for (SocksAuthScheme authScheme : authSchemes) {
            buffer.writeByte(authScheme.byteValue());
        }
    }
}
