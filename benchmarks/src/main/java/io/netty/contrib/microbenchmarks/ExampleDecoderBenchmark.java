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
package io.netty.contrib.microbenchmarks;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.contrib.template.ExampleDecoder;
import io.netty.microbench.channel.EmbeddedChannelWriteReleaseHandlerContext;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Fork(1)
@Threads(1)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
public class ExampleDecoderBenchmark {
    private ExampleDecoder decoder;
    private ByteBuf content;
    private ChannelHandlerContext context;

    @Param({ "true", "false" })
    public boolean pooledAllocator;

    @Setup(Level.Trial)
    public void setUp() {
        byte[] bytes = new byte[256];
        content = Unpooled.buffer(bytes.length);
        content.writeBytes(bytes);
        ByteBuf testContent = Unpooled.unreleasableBuffer(content.asReadOnly());

        decoder = new ExampleDecoder();
        context = new EmbeddedChannelWriteReleaseHandlerContext(pooledAllocator ? PooledByteBufAllocator.DEFAULT :
                UnpooledByteBufAllocator.DEFAULT, decoder) {
            @Override
            protected void handleException(Throwable t) {
                throw new AssertionError("Unexpected exception", t);
            }
        };
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        content.release();
        content = null;
    }

    @Benchmark
    public void useDecoder() throws Exception {
        decoder.channelRead(context, content);
    }
}
