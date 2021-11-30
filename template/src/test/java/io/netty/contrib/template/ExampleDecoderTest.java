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
package io.netty.contrib.template;

import io.netty.buffer.api.Buffer;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.netty.buffer.api.DefaultGlobalBufferAllocator.DEFAULT_GLOBAL_BUFFER_ALLOCATOR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the basic functionality of the {@link ExampleDecoder}.
 */
public class ExampleDecoderTest {
    private EmbeddedChannel channel;

    @BeforeEach
    public void setUp() throws Exception {
        channel = new EmbeddedChannel(new ExampleDecoder());
    }

    @AfterEach
    public void tearDown() throws Exception {
        channel.finish();
    }

    @Test
    public void shouldDecodeRequestWithSimpleXml() {
        write("some data");

        Buffer result = channel.readInbound();
        assertThat(result.readCharSequence(result.readableBytes(), UTF_8)).isEqualTo("some data");
    }

    private void write(String content) {
        Buffer buffer = DEFAULT_GLOBAL_BUFFER_ALLOCATOR.copyOf(content.getBytes(UTF_8));
        assertThat(channel.writeInbound(buffer)).isEqualTo(true);
    }
}
