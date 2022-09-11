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
package io.netty.contrib.handler.proxy;

import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.handler.codec.LineBasedFrameDecoder;
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.DefaultHttpContent;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpHeaderValues;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpObjectAggregator;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http.HttpServerCodec;
import io.netty5.handler.codec.http.HttpVersion;
import io.netty5.util.internal.SocketUtils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static io.netty5.buffer.BufferUtil.writeAscii;
import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static org.assertj.core.api.Assertions.assertThat;

final class HttpProxyServer extends ProxyServer {

    HttpProxyServer(boolean useSsl, TestMode testMode, InetSocketAddress destination) throws InterruptedException {
        super(useSsl, testMode, destination);
    }

    HttpProxyServer(
            boolean useSsl, TestMode testMode, InetSocketAddress destination, String username, String password)
            throws InterruptedException {
        super(useSsl, testMode, destination, username, password);
    }

    @Override
    protected void configure(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        switch (testMode) {
        case INTERMEDIARY:
            p.addLast(new HttpServerCodec());
            p.addLast(new HttpObjectAggregator<DefaultHttpContent>(1));
            p.addLast(new HttpIntermediaryHandler());
            break;
        case TERMINAL:
            p.addLast(new HttpServerCodec());
            p.addLast(new HttpObjectAggregator<DefaultHttpContent>(1));
            p.addLast(new HttpTerminalHandler());
            break;
        case UNRESPONSIVE:
            p.addLast(UnresponsiveHandler.INSTANCE);
            break;
        }
    }

    private boolean authenticate(ChannelHandlerContext ctx, FullHttpRequest req) {
        assertThat(req.method()).isEqualTo(HttpMethod.CONNECT);

        if (testMode != TestMode.INTERMEDIARY) {
            ctx.pipeline().addBefore(ctx.name(), "lineDecoder", new LineBasedFrameDecoder(64, false, true));
        }

        ctx.pipeline().remove(HttpObjectAggregator.class);
        ctx.pipeline().get(HttpServerCodec.class).removeInboundHandler();

        boolean authzSuccess = false;
        if (username != null) {
            CharSequence authz = req.headers().get(HttpHeaderNames.PROXY_AUTHORIZATION);
            if (authz != null) {
                String[] authzParts = authz.toString().split(" ", 2);
                byte[] authzCreds = Base64.getDecoder().decode(authzParts[1]);

                String expectedAuthz = username + ':' + password;
                authzSuccess = "Basic".equals(authzParts[0]) &&
                        expectedAuthz.equals(new String(authzCreds, StandardCharsets.UTF_8));
            }
        } else {
            authzSuccess = true;
        }

        return authzSuccess;
    }

    private final class HttpIntermediaryHandler extends IntermediaryHandler {

        private SocketAddress intermediaryDestination;

        @Override
        protected boolean handleProxyProtocol(ChannelHandlerContext ctx, Object msg) {
            FullHttpRequest req = (FullHttpRequest) msg;
            FullHttpResponse res;
            if (!authenticate(ctx, req)) {
                res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED,
                        preferredAllocator().allocate(0));
                res.headers().set(HttpHeaderNames.CONTENT_LENGTH, HttpHeaderValues.ZERO);
            } else {
                res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                        preferredAllocator().allocate(0));
                String uri = req.uri();
                int lastColonPos = uri.lastIndexOf(':');
                assertThat(lastColonPos).isGreaterThan(0);
                intermediaryDestination = SocketUtils.socketAddress(
                        uri.substring(0, lastColonPos), Integer.parseInt(uri.substring(lastColonPos + 1)));
            }

            ctx.write(res);
            ctx.pipeline().get(HttpServerCodec.class).removeOutboundHandler();
            return true;
        }

        @Override
        protected SocketAddress intermediaryDestination() {
            return intermediaryDestination;
        }
    }

    private final class HttpTerminalHandler extends TerminalHandler {

        @Override
        protected boolean handleProxyProtocol(ChannelHandlerContext ctx, Object msg) {
            FullHttpRequest req = (FullHttpRequest) msg;
            FullHttpResponse res;
            boolean sendGreeting = false;

            if (!authenticate(ctx, req)) {
                res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED,
                        preferredAllocator().allocate(0));
                res.headers().set(HttpHeaderNames.CONTENT_LENGTH, HttpHeaderValues.ZERO);
            } else if (!req.uri().equals(destination.getHostString() + ':' + destination.getPort())) {
                res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN,
                        preferredAllocator().allocate(0));
                res.headers().set(HttpHeaderNames.CONTENT_LENGTH, HttpHeaderValues.ZERO);
            } else {
                res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                        preferredAllocator().allocate(0));
                sendGreeting = true;
            }

            ctx.write(res);
            ctx.pipeline().get(HttpServerCodec.class).removeOutboundHandler();

            if (sendGreeting) {
                ctx.write(writeAscii(ctx.bufferAllocator(), "0\n"));
            }

            return true;
        }
    }
}
