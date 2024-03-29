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

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.ReadBufferAllocator;
import io.netty5.handler.codec.http.DefaultFullHttpRequest;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.HttpClientCodec;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.headers.DefaultHttpHeadersFactory;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpResponse;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http.HttpUtil;
import io.netty5.handler.codec.http.HttpVersion;
import io.netty5.handler.codec.http.LastHttpContent;
import io.netty5.handler.codec.http.headers.HttpHeadersFactory;
import io.netty5.util.AsciiString;
import io.netty5.util.concurrent.Future;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static java.util.Objects.requireNonNull;

/**
 * Handler that establishes a blind forwarding proxy tunnel using
 * <a href="https://datatracker.ietf.org/doc/html/rfc7231#section-4.3.6">HTTP/1.1 CONNECT</a> request. It can be used to
 * establish plaintext or secure tunnels.
 * <p>
 * HTTP users who need to connect to a
 * <a href="https://datatracker.ietf.org/doc/html/rfc7230#page-10">message-forwarding HTTP proxy agent</a> instead of a
 * tunneling proxy should not use this handler.
 */
public final class HttpProxyHandler extends ProxyHandler {

    private static final String PROTOCOL = "http";
    private static final String AUTH_BASIC = "basic";
    private static final byte[] BASIC_BYTES = "Basic ".getBytes(StandardCharsets.UTF_8);

    // Wrapper for the HttpClientCodec to prevent it to be removed by other handlers by mistake (for example the
    // WebSocket*Handshaker.
    //
    // See:
    // - https://github.com/netty/netty/issues/5201
    // - https://github.com/netty/netty/issues/5070
    private final HttpClientCodecWrapper codecWrapper = new HttpClientCodecWrapper();
    private final String username;
    private final String password;
    private final CharSequence authorization;
    private final HttpHeaders outboundHeaders;
    private final boolean ignoreDefaultPortsInConnectHostHeader;
    private HttpResponseStatus status;
    private HttpHeaders inboundHeaders;

    public HttpProxyHandler(SocketAddress proxyAddress) {
        this(proxyAddress, null);
    }

    public HttpProxyHandler(SocketAddress proxyAddress, HttpHeaders headers) {
        this(proxyAddress, headers, false);
    }

    public HttpProxyHandler(SocketAddress proxyAddress,
                            HttpHeaders headers,
                            boolean ignoreDefaultPortsInConnectHostHeader) {
        super(proxyAddress);
        username = null;
        password = null;
        authorization = null;
        outboundHeaders = headers;
        this.ignoreDefaultPortsInConnectHostHeader = ignoreDefaultPortsInConnectHostHeader;
    }

    public HttpProxyHandler(SocketAddress proxyAddress, String username, String password) {
        this(proxyAddress, username, password, null);
    }

    public HttpProxyHandler(SocketAddress proxyAddress, String username, String password,
                            HttpHeaders headers) {
        this(proxyAddress, username, password, headers, false);
    }

    public HttpProxyHandler(SocketAddress proxyAddress,
                            String username,
                            String password,
                            HttpHeaders headers,
                            boolean ignoreDefaultPortsInConnectHostHeader) {
        super(proxyAddress);
        requireNonNull(username, "username");
        requireNonNull(password, "password");
        this.username = username;
        this.password = password;

        byte[] authzBase64 = Base64.getEncoder().encode(
                (username + ':' + password).getBytes(StandardCharsets.UTF_8));
        byte[] authzHeader = Arrays.copyOf(BASIC_BYTES, 6 + authzBase64.length);
        System.arraycopy(authzBase64, 0, authzHeader, 6, authzBase64.length);

        authorization = new AsciiString(authzHeader, /*copy=*/ false);

        outboundHeaders = headers;
        this.ignoreDefaultPortsInConnectHostHeader = ignoreDefaultPortsInConnectHostHeader;
    }

    @Override
    public String protocol() {
        return PROTOCOL;
    }

    @Override
    public String authScheme() {
        return authorization != null? AUTH_BASIC : AUTH_NONE;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    @Override
    protected void addCodec(ChannelHandlerContext ctx) throws Exception {
        ChannelPipeline p = ctx.pipeline();
        String name = ctx.name();
        p.addBefore(name, null, codecWrapper);
    }

    @Override
    protected void removeEncoder(ChannelHandlerContext ctx) throws Exception {
        codecWrapper.codec.removeOutboundHandler();
    }

    @Override
    protected void removeDecoder(ChannelHandlerContext ctx) throws Exception {
        codecWrapper.codec.removeInboundHandler();
    }

    @Override
    protected Object newInitialMessage(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress raddr = destinationAddress();

        String hostString = HttpUtil.formatHostnameForHttp(raddr);
        int port = raddr.getPort();
        String url = hostString + ':' + port;
        String hostHeader = ignoreDefaultPortsInConnectHostHeader && (port == 80 || port == 443) ?
                hostString :
                url;

        HttpHeadersFactory httpHeadersFactory = DefaultHttpHeadersFactory.headersFactory().withNameValidation(false).withValueValidation(false);
        HttpHeadersFactory httpTrailersFactory = DefaultHttpHeadersFactory.trailersFactory().withNameValidation(false).withValueValidation(false);

        FullHttpRequest req = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.CONNECT,
                url,
                ctx.bufferAllocator().allocate(0), httpHeadersFactory, httpTrailersFactory);

        req.headers().set(HttpHeaderNames.HOST, hostHeader);

        if (authorization != null) {
            req.headers().set(HttpHeaderNames.PROXY_AUTHORIZATION, authorization);
        }

        if (outboundHeaders != null) {
            req.headers().add(outboundHeaders);
        }

        return req;
    }

    @Override
    protected boolean handleResponse(ChannelHandlerContext ctx, Object response) throws Exception {
        if (response instanceof HttpResponse) {
            if (status != null) {
                throw new HttpProxyConnectException(exceptionMessage("too many responses"), /*headers=*/ null);
            }
            HttpResponse res = (HttpResponse) response;
            status = res.status();
            inboundHeaders = res.headers();
        }

        boolean finished = response instanceof LastHttpContent;
        if (finished) {
            if (status == null) {
                throw new HttpProxyConnectException(exceptionMessage("missing response"), inboundHeaders);
            }
            if (status.code() != 200) {
                throw new HttpProxyConnectException(exceptionMessage("status: " + status), inboundHeaders);
            }
        }

        return finished;
    }

    /**
     * Specific case of a connection failure, which may include headers from the proxy.
     */
    public static final class HttpProxyConnectException extends ProxyConnectException {
        private static final long serialVersionUID = -8824334609292146066L;

        private final HttpHeaders headers;

        /**
         * @param message The failure message.
         * @param headers Header associated with the connection failure.  May be {@code null}.
         */
        public HttpProxyConnectException(String message, HttpHeaders headers) {
            super(message);
            this.headers = headers;
        }

        /**
         * Returns headers, if any.  May be {@code null}.
         */
        public HttpHeaders headers() {
            return headers;
        }
    }

    private static final class HttpClientCodecWrapper implements ChannelHandler {
        final HttpClientCodec codec = new HttpClientCodec();

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            codec.handlerAdded(ctx);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            codec.handlerRemoved(ctx);
        }

        @Override
        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            codec.channelExceptionCaught(ctx, cause);
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            codec.channelRegistered(ctx);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            codec.channelUnregistered(ctx);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            codec.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            codec.channelInactive(ctx);
        }

        @Override
        public void channelShutdown(ChannelHandlerContext ctx, ChannelShutdownDirection direction) throws Exception {
            codec.channelShutdown(ctx, direction);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            codec.channelRead(ctx, msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            codec.channelReadComplete(ctx);
        }

        @Override
        public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
            codec.channelInboundEvent(ctx, evt);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            codec.channelWritabilityChanged(ctx);
        }

        @Override
        public Future<Void> bind(ChannelHandlerContext ctx, SocketAddress localAddress) {
            return codec.bind(ctx, localAddress);
        }

        @Override
        public Future<Void> connect(
                ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress) {
            return codec.connect(ctx, remoteAddress, localAddress);
        }

        @Override
        public Future<Void> disconnect(ChannelHandlerContext ctx) {
            return codec.disconnect(ctx);
        }

        @Override
        public Future<Void> close(ChannelHandlerContext ctx) {
            return codec.close(ctx);
        }

        @Override
        public Future<Void> shutdown(ChannelHandlerContext ctx, ChannelShutdownDirection direction) {
            return codec.shutdown(ctx, direction);
        }

        @Override
        public Future<Void> register(ChannelHandlerContext ctx) {
            return codec.register(ctx);
        }

        @Override
        public Future<Void> deregister(ChannelHandlerContext ctx) {
            return codec.deregister(ctx);
        }

        @Override
        public void read(ChannelHandlerContext ctx, ReadBufferAllocator readBufferAllocator) {
            codec.read(ctx, readBufferAllocator);
        }

        @Override
        public long pendingOutboundBytes(ChannelHandlerContext ctx) {
            return codec.pendingOutboundBytes(ctx);
        }

        @Override
        public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
           return codec.write(ctx, msg);
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            codec.flush(ctx);
        }

        @Override
        public Future<Void> sendOutboundEvent(ChannelHandlerContext ctx, Object event) {
            return codec.sendOutboundEvent(ctx, event);
        }
    }
}
