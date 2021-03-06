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

import io.netty.contrib.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.contrib.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.contrib.handler.codec.socksx.v4.Socks4CommandResponse;
import io.netty.contrib.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.contrib.handler.codec.socksx.v4.Socks4CommandType;
import io.netty.contrib.handler.codec.socksx.v4.Socks4ServerDecoder;
import io.netty.contrib.handler.codec.socksx.v4.Socks4ServerEncoder;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.handler.codec.LineBasedFrameDecoder;
import io.netty5.util.internal.SocketUtils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static io.netty5.buffer.BufferUtil.writeAscii;
import static org.assertj.core.api.Assertions.assertThat;

final class Socks4ProxyServer extends ProxyServer {

    Socks4ProxyServer(boolean useSsl, TestMode testMode, InetSocketAddress destination) throws InterruptedException {
        super(useSsl, testMode, destination);
    }

    Socks4ProxyServer(boolean useSsl, TestMode testMode, InetSocketAddress destination, String username)
            throws InterruptedException {
        super(useSsl, testMode, destination, username, null);
    }

    @Override
    protected void configure(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        switch (testMode) {
        case INTERMEDIARY:
            p.addLast(new Socks4ServerDecoder());
            p.addLast(Socks4ServerEncoder.INSTANCE);
            p.addLast(new Socks4IntermediaryHandler());
            break;
        case TERMINAL:
            p.addLast(new Socks4ServerDecoder());
            p.addLast(Socks4ServerEncoder.INSTANCE);
            p.addLast(new Socks4TerminalHandler());
            break;
        case UNRESPONSIVE:
            p.addLast(UnresponsiveHandler.INSTANCE);
            break;
        }
    }

    private boolean authenticate(ChannelHandlerContext ctx, Socks4CommandRequest req) {
        assertThat(req.type()).isEqualTo(Socks4CommandType.CONNECT);

        if (testMode != TestMode.INTERMEDIARY) {
            ctx.pipeline().addBefore(ctx.name(), "lineDecoder", new LineBasedFrameDecoder(64, false, true));
        }

        boolean authzSuccess;
        if (username != null) {
            authzSuccess = username.equals(req.userId());
        } else {
            authzSuccess = true;
        }
        return authzSuccess;
    }

    private final class Socks4IntermediaryHandler extends IntermediaryHandler {

        private SocketAddress intermediaryDestination;

        @Override
        protected boolean handleProxyProtocol(ChannelHandlerContext ctx, Object msg) {
            Socks4CommandRequest req = (Socks4CommandRequest) msg;
            Socks4CommandResponse res;

            if (!authenticate(ctx, req)) {
                res = new DefaultSocks4CommandResponse(Socks4CommandStatus.IDENTD_AUTH_FAILURE);
            } else {
                res = new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS);
                intermediaryDestination = SocketUtils.socketAddress(req.dstAddr(), req.dstPort());
            }

            ctx.write(res);

            ctx.pipeline().remove(Socks4ServerDecoder.class);
            ctx.pipeline().remove(Socks4ServerEncoder.class);

            return true;
        }

        @Override
        protected SocketAddress intermediaryDestination() {
            return intermediaryDestination;
        }
    }

    private final class Socks4TerminalHandler extends TerminalHandler {
        @Override
        protected boolean handleProxyProtocol(ChannelHandlerContext ctx, Object msg) {
            Socks4CommandRequest req = (Socks4CommandRequest) msg;
            boolean authzSuccess = authenticate(ctx, req);

            Socks4CommandResponse res;
            boolean sendGreeting = false;
            if (!authzSuccess) {
                res = new DefaultSocks4CommandResponse(Socks4CommandStatus.IDENTD_AUTH_FAILURE);
            } else if (!req.dstAddr().equals(destination.getHostString()) ||
                       req.dstPort() != destination.getPort()) {
                res = new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED);
            } else {
                res = new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS);
                sendGreeting = true;
            }

            ctx.write(res);

            ctx.pipeline().remove(Socks4ServerDecoder.class);
            ctx.pipeline().remove(Socks4ServerEncoder.class);

            if (sendGreeting) {
                ctx.write(writeAscii(ctx.bufferAllocator(), "0\n"));
            }

            return true;
        }
    }
}
