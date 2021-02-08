/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.networking.p2p.libp2p;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class FirewallTest {

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void testFirewallNotPropagateTimeoutExceptionUpstream() throws Exception {
    Firewall firewall = new Firewall(Duration.ofMillis(100));
    EmbeddedChannel channel =
        new EmbeddedChannel(
            firewall,
            new ChannelInboundHandlerAdapter() {
              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                  throws Exception {
                super.exceptionCaught(ctx, cause);
              }
            });
    channel.writeOneOutbound("a");
    executeAllScheduledTasks(channel, 5);
    Assertions.assertThatCode(channel::checkException).doesNotThrowAnyException();
    Assertions.assertThat(channel.isOpen()).isFalse();
  }

  private void executeAllScheduledTasks(EmbeddedChannel channel, long maxWaitSeconds)
      throws TimeoutException, InterruptedException {
    long waitTime = 0;
    while (waitTime < maxWaitSeconds * 1000) {
      long l = channel.runScheduledPendingTasks();
      if (l < 0) break;
      long ms = l / 1_000_000;
      waitTime += ms;
      Thread.sleep(ms);
    }
    if (waitTime >= maxWaitSeconds * 1000) {
      throw new TimeoutException();
    }
  }
}
