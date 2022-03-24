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

import io.netty5.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.nio.NioHandler;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.handler.codec.LineBasedFrameDecoder;
import io.netty5.handler.ssl.SslContext;
import io.netty5.handler.ssl.SslContextBuilder;
import io.netty5.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty5.handler.ssl.util.SelfSignedCertificate;
import io.netty5.resolver.NoopAddressResolverGroup;
import io.netty5.util.CharsetUtil;
import io.netty5.util.concurrent.DefaultThreadFactory;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.SocketUtils;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Disabled("buffer migration")
public class ProxyHandlerTest {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ProxyHandlerTest.class);

    private static final InetSocketAddress DESTINATION = InetSocketAddress.createUnresolved("destination.com", 42);
    private static final InetSocketAddress BAD_DESTINATION = SocketUtils.socketAddress("1.2.3.4", 5);
    private static final String USERNAME = "testUser";
    private static final String PASSWORD = "testPassword";
    private static final String BAD_USERNAME = "badUser";
    private static final String BAD_PASSWORD = "badPassword";

    static final EventLoopGroup group = new MultithreadEventLoopGroup(3,
            new DefaultThreadFactory("proxy", true), NioHandler.newFactory());

    static final SslContext serverSslCtx;
    static final SslContext clientSslCtx;

    static {
        SslContext sctx;
        SslContext cctx;
        try {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sctx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
            cctx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        } catch (Exception e) {
            throw new Error(e);
        }
        serverSslCtx = sctx;
        clientSslCtx = cctx;
    }

    static final ProxyServer deadHttpProxy = new HttpProxyServer(false, TestMode.UNRESPONSIVE, null);
    static final ProxyServer interHttpProxy = new HttpProxyServer(false, TestMode.INTERMEDIARY, null);
    static final ProxyServer anonHttpProxy = new HttpProxyServer(false, TestMode.TERMINAL, DESTINATION);
    static final ProxyServer httpProxy =
            new HttpProxyServer(false, TestMode.TERMINAL, DESTINATION, USERNAME, PASSWORD);

    static final ProxyServer deadHttpsProxy = new HttpProxyServer(true, TestMode.UNRESPONSIVE, null);
    static final ProxyServer interHttpsProxy = new HttpProxyServer(true, TestMode.INTERMEDIARY, null);
    static final ProxyServer anonHttpsProxy = new HttpProxyServer(true, TestMode.TERMINAL, DESTINATION);
    static final ProxyServer httpsProxy =
            new HttpProxyServer(true, TestMode.TERMINAL, DESTINATION, USERNAME, PASSWORD);

    static final ProxyServer deadSocks4Proxy = new Socks4ProxyServer(false, TestMode.UNRESPONSIVE, null);
    static final ProxyServer interSocks4Proxy = new Socks4ProxyServer(false, TestMode.INTERMEDIARY, null);
    static final ProxyServer anonSocks4Proxy = new Socks4ProxyServer(false, TestMode.TERMINAL, DESTINATION);
    static final ProxyServer socks4Proxy = new Socks4ProxyServer(false, TestMode.TERMINAL, DESTINATION, USERNAME);

    static final ProxyServer deadSocks5Proxy = new Socks5ProxyServer(false, TestMode.UNRESPONSIVE, null);
    static final ProxyServer interSocks5Proxy = new Socks5ProxyServer(false, TestMode.INTERMEDIARY, null);
    static final ProxyServer anonSocks5Proxy = new Socks5ProxyServer(false, TestMode.TERMINAL, DESTINATION);
    static final ProxyServer socks5Proxy =
            new Socks5ProxyServer(false, TestMode.TERMINAL, DESTINATION, USERNAME, PASSWORD);

    private static final Collection<ProxyServer> allProxies = Arrays.asList(
            deadHttpProxy, interHttpProxy, anonHttpProxy, httpProxy,
            deadHttpsProxy, interHttpsProxy, anonHttpsProxy, httpsProxy,
            deadSocks4Proxy, interSocks4Proxy, anonSocks4Proxy, socks4Proxy,
            deadSocks5Proxy, interSocks5Proxy, anonSocks5Proxy, socks5Proxy
    );

    // set to non-zero value in case you need predictable shuffling of test cases
    // look for "Seed used: *" debug message in test logs
    private static final long reproducibleSeed = 0L;

    public static List<Object[]> testItems() {

        List<TestItem> items = Arrays.asList(

                // HTTP -------------------------------------------------------

                new SuccessTestItem(
                        "Anonymous HTTP proxy: successful connection, AUTO_READ on",
                        DESTINATION,
                        true,
                        new HttpProxyHandler(anonHttpProxy.address())),

                new SuccessTestItem(
                        "Anonymous HTTP proxy: successful connection, AUTO_READ off",
                        DESTINATION,
                        false,
                        new HttpProxyHandler(anonHttpProxy.address())),

                new FailureTestItem(
                        "Anonymous HTTP proxy: rejected connection",
                        BAD_DESTINATION, "status: 403",
                        new HttpProxyHandler(anonHttpProxy.address())),

                new FailureTestItem(
                        "HTTP proxy: rejected anonymous connection",
                        DESTINATION, "status: 401",
                        new HttpProxyHandler(httpProxy.address())),

                new SuccessTestItem(
                        "HTTP proxy: successful connection, AUTO_READ on",
                        DESTINATION,
                        true,
                        new HttpProxyHandler(httpProxy.address(), USERNAME, PASSWORD)),

                new SuccessTestItem(
                        "HTTP proxy: successful connection, AUTO_READ off",
                        DESTINATION,
                        false,
                        new HttpProxyHandler(httpProxy.address(), USERNAME, PASSWORD)),

                new FailureTestItem(
                        "HTTP proxy: rejected connection",
                        BAD_DESTINATION, "status: 403",
                        new HttpProxyHandler(httpProxy.address(), USERNAME, PASSWORD)),

                new FailureTestItem(
                        "HTTP proxy: authentication failure",
                        DESTINATION, "status: 401",
                        new HttpProxyHandler(httpProxy.address(), BAD_USERNAME, BAD_PASSWORD)),

                new TimeoutTestItem(
                        "HTTP proxy: timeout",
                        new HttpProxyHandler(deadHttpProxy.address())),

                // HTTPS ------------------------------------------------------

                new SuccessTestItem(
                        "Anonymous HTTPS proxy: successful connection, AUTO_READ on",
                        DESTINATION,
                        true,
                        clientSslCtx.newHandler(PooledByteBufAllocator.DEFAULT),
                        new HttpProxyHandler(anonHttpsProxy.address())),

                new SuccessTestItem(
                        "Anonymous HTTPS proxy: successful connection, AUTO_READ off",
                        DESTINATION,
                        false,
                        clientSslCtx.newHandler(PooledByteBufAllocator.DEFAULT),
                        new HttpProxyHandler(anonHttpsProxy.address())),

                new FailureTestItem(
                        "Anonymous HTTPS proxy: rejected connection",
                        BAD_DESTINATION, "status: 403",
                        clientSslCtx.newHandler(PooledByteBufAllocator.DEFAULT),
                        new HttpProxyHandler(anonHttpsProxy.address())),

                new FailureTestItem(
                        "HTTPS proxy: rejected anonymous connection",
                        DESTINATION, "status: 401",
                        clientSslCtx.newHandler(PooledByteBufAllocator.DEFAULT),
                        new HttpProxyHandler(httpsProxy.address())),

                new SuccessTestItem(
                        "HTTPS proxy: successful connection, AUTO_READ on",
                        DESTINATION,
                        true,
                        clientSslCtx.newHandler(PooledByteBufAllocator.DEFAULT),
                        new HttpProxyHandler(httpsProxy.address(), USERNAME, PASSWORD)),

                new SuccessTestItem(
                        "HTTPS proxy: successful connection, AUTO_READ off",
                        DESTINATION,
                        false,
                        clientSslCtx.newHandler(PooledByteBufAllocator.DEFAULT),
                        new HttpProxyHandler(httpsProxy.address(), USERNAME, PASSWORD)),

                new FailureTestItem(
                        "HTTPS proxy: rejected connection",
                        BAD_DESTINATION, "status: 403",
                        clientSslCtx.newHandler(PooledByteBufAllocator.DEFAULT),
                        new HttpProxyHandler(httpsProxy.address(), USERNAME, PASSWORD)),

                new FailureTestItem(
                        "HTTPS proxy: authentication failure",
                        DESTINATION, "status: 401",
                        clientSslCtx.newHandler(PooledByteBufAllocator.DEFAULT),
                        new HttpProxyHandler(httpsProxy.address(), BAD_USERNAME, BAD_PASSWORD)),

                new TimeoutTestItem(
                        "HTTPS proxy: timeout",
                        clientSslCtx.newHandler(PooledByteBufAllocator.DEFAULT),
                        new HttpProxyHandler(deadHttpsProxy.address())),

                // SOCKS4 -----------------------------------------------------

                new SuccessTestItem(
                        "Anonymous SOCKS4: successful connection, AUTO_READ on",
                        DESTINATION,
                        true,
                        new Socks4ProxyHandler(anonSocks4Proxy.address())),

                new SuccessTestItem(
                        "Anonymous SOCKS4: successful connection, AUTO_READ off",
                        DESTINATION,
                        false,
                        new Socks4ProxyHandler(anonSocks4Proxy.address())),

                new FailureTestItem(
                        "Anonymous SOCKS4: rejected connection",
                        BAD_DESTINATION, "status: REJECTED_OR_FAILED",
                        new Socks4ProxyHandler(anonSocks4Proxy.address())),

                new FailureTestItem(
                        "SOCKS4: rejected anonymous connection",
                        DESTINATION, "status: IDENTD_AUTH_FAILURE",
                        new Socks4ProxyHandler(socks4Proxy.address())),

                new SuccessTestItem(
                        "SOCKS4: successful connection, AUTO_READ on",
                        DESTINATION,
                        true,
                        new Socks4ProxyHandler(socks4Proxy.address(), USERNAME)),

                new SuccessTestItem(
                        "SOCKS4: successful connection, AUTO_READ off",
                        DESTINATION,
                        false,
                        new Socks4ProxyHandler(socks4Proxy.address(), USERNAME)),

                new FailureTestItem(
                        "SOCKS4: rejected connection",
                        BAD_DESTINATION, "status: REJECTED_OR_FAILED",
                        new Socks4ProxyHandler(socks4Proxy.address(), USERNAME)),

                new FailureTestItem(
                        "SOCKS4: authentication failure",
                        DESTINATION, "status: IDENTD_AUTH_FAILURE",
                        new Socks4ProxyHandler(socks4Proxy.address(), BAD_USERNAME)),

                new TimeoutTestItem(
                        "SOCKS4: timeout",
                        new Socks4ProxyHandler(deadSocks4Proxy.address())),

                // SOCKS5 -----------------------------------------------------

                new SuccessTestItem(
                        "Anonymous SOCKS5: successful connection, AUTO_READ on",
                        DESTINATION,
                        true,
                        new Socks5ProxyHandler(anonSocks5Proxy.address())),

                new SuccessTestItem(
                        "Anonymous SOCKS5: successful connection, AUTO_READ off",
                        DESTINATION,
                        false,
                        new Socks5ProxyHandler(anonSocks5Proxy.address())),

                new FailureTestItem(
                        "Anonymous SOCKS5: rejected connection",
                        BAD_DESTINATION, "status: FORBIDDEN",
                        new Socks5ProxyHandler(anonSocks5Proxy.address())),

                new FailureTestItem(
                        "SOCKS5: rejected anonymous connection",
                        DESTINATION, "unexpected authMethod: PASSWORD",
                        new Socks5ProxyHandler(socks5Proxy.address())),

                new SuccessTestItem(
                        "SOCKS5: successful connection, AUTO_READ on",
                        DESTINATION,
                        true,
                        new Socks5ProxyHandler(socks5Proxy.address(), USERNAME, PASSWORD)),

                new SuccessTestItem(
                        "SOCKS5: successful connection, AUTO_READ off",
                        DESTINATION,
                        false,
                        new Socks5ProxyHandler(socks5Proxy.address(), USERNAME, PASSWORD)),

                new FailureTestItem(
                        "SOCKS5: rejected connection",
                        BAD_DESTINATION, "status: FORBIDDEN",
                        new Socks5ProxyHandler(socks5Proxy.address(), USERNAME, PASSWORD)),

                new FailureTestItem(
                        "SOCKS5: authentication failure",
                        DESTINATION, "authStatus: FAILURE",
                        new Socks5ProxyHandler(socks5Proxy.address(), BAD_USERNAME, BAD_PASSWORD)),

                new TimeoutTestItem(
                        "SOCKS5: timeout",
                        new Socks5ProxyHandler(deadSocks5Proxy.address())),

                // HTTP + HTTPS + SOCKS4 + SOCKS5

                new SuccessTestItem(
                        "Single-chain: successful connection, AUTO_READ on",
                        DESTINATION,
                        true,
                        new Socks5ProxyHandler(interSocks5Proxy.address()), // SOCKS5
                        new Socks4ProxyHandler(interSocks4Proxy.address()), // SOCKS4
                        clientSslCtx.newHandler(PooledByteBufAllocator.DEFAULT),
                        new HttpProxyHandler(interHttpsProxy.address()), // HTTPS
                        new HttpProxyHandler(interHttpProxy.address()), // HTTP
                        new HttpProxyHandler(anonHttpProxy.address())),

                new SuccessTestItem(
                        "Single-chain: successful connection, AUTO_READ off",
                        DESTINATION,
                        false,
                        new Socks5ProxyHandler(interSocks5Proxy.address()), // SOCKS5
                        new Socks4ProxyHandler(interSocks4Proxy.address()), // SOCKS4
                        clientSslCtx.newHandler(PooledByteBufAllocator.DEFAULT),
                        new HttpProxyHandler(interHttpsProxy.address()), // HTTPS
                        new HttpProxyHandler(interHttpProxy.address()), // HTTP
                        new HttpProxyHandler(anonHttpProxy.address())),

                // (HTTP + HTTPS + SOCKS4 + SOCKS5) * 2

                new SuccessTestItem(
                        "Double-chain: successful connection, AUTO_READ on",
                        DESTINATION,
                        true,
                        new Socks5ProxyHandler(interSocks5Proxy.address()), // SOCKS5
                        new Socks4ProxyHandler(interSocks4Proxy.address()), // SOCKS4
                        clientSslCtx.newHandler(PooledByteBufAllocator.DEFAULT),
                        new HttpProxyHandler(interHttpsProxy.address()), // HTTPS
                        new HttpProxyHandler(interHttpProxy.address()), // HTTP
                        new Socks5ProxyHandler(interSocks5Proxy.address()), // SOCKS5
                        new Socks4ProxyHandler(interSocks4Proxy.address()), // SOCKS4
                        clientSslCtx.newHandler(PooledByteBufAllocator.DEFAULT),
                        new HttpProxyHandler(interHttpsProxy.address()), // HTTPS
                        new HttpProxyHandler(interHttpProxy.address()), // HTTP
                        new HttpProxyHandler(anonHttpProxy.address())),

                new SuccessTestItem(
                        "Double-chain: successful connection, AUTO_READ off",
                        DESTINATION,
                        false,
                        new Socks5ProxyHandler(interSocks5Proxy.address()), // SOCKS5
                        new Socks4ProxyHandler(interSocks4Proxy.address()), // SOCKS4
                        clientSslCtx.newHandler(PooledByteBufAllocator.DEFAULT),
                        new HttpProxyHandler(interHttpsProxy.address()), // HTTPS
                        new HttpProxyHandler(interHttpProxy.address()), // HTTP
                        new Socks5ProxyHandler(interSocks5Proxy.address()), // SOCKS5
                        new Socks4ProxyHandler(interSocks4Proxy.address()), // SOCKS4
                        clientSslCtx.newHandler(PooledByteBufAllocator.DEFAULT),
                        new HttpProxyHandler(interHttpsProxy.address()), // HTTPS
                        new HttpProxyHandler(interHttpProxy.address()), // HTTP
                        new HttpProxyHandler(anonHttpProxy.address()))
        );

        // Convert the test items to the list of constructor parameters.
        List<Object[]> params = new ArrayList<>(items.size());
        for (Object i: items) {
            params.add(new Object[] { i });
        }

        // Randomize the execution order to increase the possibility of exposing failure dependencies.
        long seed = reproducibleSeed == 0L? System.currentTimeMillis() : reproducibleSeed;
        logger.debug("Seed used: {}\n", seed);
        Collections.shuffle(params, new Random(seed));

        return params;
    }

    @AfterAll
    public static void stopServers() {
        for (ProxyServer p: allProxies) {
            p.stop();
        }
    }

    @BeforeEach
    public void clearServerExceptions() throws Exception {
        for (ProxyServer p: allProxies) {
            p.clearExceptions();
        }
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("testItems")
    public void test(TestItem testItem) throws Exception {
        testItem.test();
    }

    @AfterEach
    public void checkServerExceptions() throws Exception {
        for (ProxyServer p: allProxies) {
            p.checkExceptions();
        }
    }

    private static final class SuccessTestHandler extends SimpleChannelInboundHandler<Object> {

        final Queue<String> received = new LinkedBlockingQueue<>();
        final Queue<Throwable> exceptions = new LinkedBlockingQueue<>();
        volatile int eventCount;

        private static void readIfNeeded(ChannelHandlerContext ctx) {
            if (!ctx.channel().config().isAutoRead()) {
                ctx.read();
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ctx.writeAndFlush(Unpooled.copiedBuffer("A\n", CharsetUtil.US_ASCII));
            readIfNeeded(ctx);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof ProxyConnectionEvent) {
                eventCount ++;

                if (eventCount == 1) {
                    // Note that ProxyConnectionEvent can be triggered multiple times when there are multiple
                    // ProxyHandlers in the pipeline.  Therefore, we send the 'B' message only on the first event.
                    ctx.writeAndFlush(Unpooled.copiedBuffer("B\n", CharsetUtil.US_ASCII));
                }
                readIfNeeded(ctx);
            }
        }

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
            String str = ((ByteBuf) msg).toString(CharsetUtil.US_ASCII);
            received.add(str);
            if ("2".equals(str)) {
                ctx.writeAndFlush(Unpooled.copiedBuffer("C\n", CharsetUtil.US_ASCII));
            }
            readIfNeeded(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            exceptions.add(cause);
            ctx.close();
        }
    }

    private static final class FailureTestHandler extends SimpleChannelInboundHandler<Object> {

        final Queue<Throwable> exceptions = new LinkedBlockingQueue<>();

        /**
         * A latch that counts down when:
         * - a pending write attempt in {@link #channelActive(ChannelHandlerContext)} finishes, or
         * - the channel is closed.
         * By waiting until the latch goes down to 0, we can make sure all assertion failures related with all write
         * attempts have been recorded.
         */
        final CountDownLatch latch = new CountDownLatch(2);

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ctx.writeAndFlush(Unpooled.copiedBuffer("A\n", CharsetUtil.US_ASCII)).addListener(
                    future -> {
                        latch.countDown();
                        if (!(future.cause() instanceof ProxyConnectException)) {
                            exceptions.add(new AssertionError(
                                    "Unexpected failure cause for initial write: " + future.cause()));
                        }
                    });
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            latch.countDown();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof ProxyConnectionEvent) {
                fail("Unexpected event: " + evt);
            }
        }

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
            fail("Unexpected message: " + msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            exceptions.add(cause);
            ctx.close();
        }
    }

    private abstract static class TestItem {
        final String name;
        final InetSocketAddress destination;
        final ChannelHandler[] clientHandlers;

        protected TestItem(String name, InetSocketAddress destination, ChannelHandler... clientHandlers) {
            this.name = name;
            this.destination = destination;
            this.clientHandlers = clientHandlers;
        }

        abstract void test() throws Exception;

        protected void assertProxyHandlers(boolean success) {
            for (ChannelHandler h: clientHandlers) {
                if (h instanceof ProxyHandler) {
                    ProxyHandler ph = (ProxyHandler) h;
                    String type = StringUtil.simpleClassName(ph);
                    Future<Channel> f = ph.connectFuture();
                    if (!f.isDone()) {
                        logger.warn("{}: not done", type);
                    } else if (f.isSuccess()) {
                        if (success) {
                            logger.debug("{}: success", type);
                        } else {
                            logger.warn("{}: success", type);
                        }
                    } else {
                        if (success) {
                            logger.warn("{}: failure", type, f.cause());
                        } else {
                            logger.debug("{}: failure", type, f.cause());
                        }
                    }
                }
            }

            for (ChannelHandler h: clientHandlers) {
                if (h instanceof ProxyHandler) {
                    ProxyHandler ph = (ProxyHandler) h;
                    assertTrue(ph.connectFuture().isDone());
                    assertEquals(success, ph.connectFuture().isSuccess());
                }
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class SuccessTestItem extends TestItem {

        private final int expectedEventCount;
        // Probably we need to be more flexible here and as for the configuration map,
        // not a single key. But as far as it works for now, I'm leaving the impl.
        // as is, in case we need to cover more cases (like, AUTO_CLOSE, TCP_NODELAY etc)
        // feel free to replace this boolean with either config or method to setup bootstrap
        private final boolean autoRead;

        SuccessTestItem(String name,
                        InetSocketAddress destination,
                        boolean autoRead,
                        ChannelHandler... clientHandlers) {
            super(name, destination, clientHandlers);
            int expectedEventCount = 0;
            for (ChannelHandler h: clientHandlers) {
                if (h instanceof ProxyHandler) {
                    expectedEventCount++;
                }
            }

            this.expectedEventCount = expectedEventCount;
            this.autoRead = autoRead;
        }

        @Override
        protected void test() throws Exception {
            final SuccessTestHandler testHandler = new SuccessTestHandler();
            Bootstrap b = new Bootstrap();
            b.group(group);
            b.channel(NioSocketChannel.class);
            b.option(ChannelOption.AUTO_READ, autoRead);
            b.resolver(NoopAddressResolverGroup.INSTANCE);
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(clientHandlers);
                    p.addLast(new LineBasedFrameDecoder(64));
                    p.addLast(testHandler);
                }
            });

            Channel channel = b.connect(destination).get();
            boolean finished = channel.closeFuture().await(10, TimeUnit.SECONDS);

            logger.debug("Received messages: {}", testHandler.received);

            if (testHandler.exceptions.isEmpty()) {
                logger.debug("No recorded exceptions on the client side.");
            } else {
                for (Throwable t : testHandler.exceptions) {
                    logger.debug("Recorded exception on the client side: {}", t);
                }
            }

            assertProxyHandlers(true);

            assertThat(testHandler.received.toArray()).containsExactly("0", "1", "2", "3");
            assertThat(testHandler.exceptions.toArray()).isEmpty();
            assertThat(testHandler.eventCount).isEqualTo(expectedEventCount);
            assertTrue(finished);
        }
    }

    private static final class FailureTestItem extends TestItem {

        private final String expectedMessage;

        FailureTestItem(
                String name, InetSocketAddress destination, String expectedMessage, ChannelHandler... clientHandlers) {
            super(name, destination, clientHandlers);
            this.expectedMessage = expectedMessage;
        }

        @Override
        protected void test() throws Exception {
            final FailureTestHandler testHandler = new FailureTestHandler();
            Bootstrap b = new Bootstrap();
            b.group(group);
            b.channel(NioSocketChannel.class);
            b.resolver(NoopAddressResolverGroup.INSTANCE);
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(clientHandlers);
                    p.addLast(new LineBasedFrameDecoder(64));
                    p.addLast(testHandler);
                }
            });

            Channel channel = b.connect(destination).get();
            boolean finished = channel.closeFuture().await(10, TimeUnit.SECONDS);
            finished &= testHandler.latch.await(10, TimeUnit.SECONDS);

            logger.debug("Recorded exceptions: {}", testHandler.exceptions);

            assertProxyHandlers(false);

            assertThat(testHandler.exceptions.size()).isOne();
            Throwable e = testHandler.exceptions.poll();
            assertThat(e).isInstanceOf(ProxyConnectException.class);
            assertThat(String.valueOf(e)).contains(expectedMessage);
            assertTrue(finished);
        }
    }

    private static final class TimeoutTestItem extends TestItem {

        TimeoutTestItem(String name, ChannelHandler... clientHandlers) {
            super(name, null, clientHandlers);
        }

        @Override
        protected void test() throws Exception {
            final long TIMEOUT = 2000;
            for (ChannelHandler h: clientHandlers) {
                if (h instanceof ProxyHandler) {
                    ((ProxyHandler) h).setConnectTimeoutMillis(TIMEOUT);
                }
            }

            final FailureTestHandler testHandler = new FailureTestHandler();
            Bootstrap b = new Bootstrap();
            b.group(group);
            b.channel(NioSocketChannel.class);
            b.resolver(NoopAddressResolverGroup.INSTANCE);
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(clientHandlers);
                    p.addLast(new LineBasedFrameDecoder(64));
                    p.addLast(testHandler);
                }
            });

            Channel channel = b.connect(DESTINATION).get();
            Future<Void> cf = channel.closeFuture();
            boolean finished = cf.await(TIMEOUT * 2, TimeUnit.MILLISECONDS);
            finished &= testHandler.latch.await(TIMEOUT * 2, TimeUnit.MILLISECONDS);

            logger.debug("Recorded exceptions: {}", testHandler.exceptions);

            assertProxyHandlers(false);

            assertThat(testHandler.exceptions.size()).isOne();
            Throwable e = testHandler.exceptions.poll();
            assertThat(e).isInstanceOf(ProxyConnectException.class);
            assertThat(String.valueOf(e)).contains("timeout");
            assertTrue(finished);
        }
    }
}
