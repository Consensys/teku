/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.networking.p2p.libp2p.rpc;

import java.util.concurrent.atomic.AtomicInteger;

/** Limits the number of active inbound RPC streams across every transport and connection. */
public class InboundRpcStreamLimiter {

  private final int maximumActiveStreams;
  private final AtomicInteger activeStreams = new AtomicInteger();

  public InboundRpcStreamLimiter(final int maximumActiveStreams) {
    if (maximumActiveStreams < 1) {
      throw new IllegalArgumentException("Maximum active inbound RPC streams must be positive");
    }
    this.maximumActiveStreams = maximumActiveStreams;
  }

  public boolean tryAcquire() {
    int currentActiveStreams;
    do {
      currentActiveStreams = activeStreams.get();
      if (currentActiveStreams >= maximumActiveStreams) {
        return false;
      }
    } while (!activeStreams.compareAndSet(currentActiveStreams, currentActiveStreams + 1));
    return true;
  }

  public void release() {
    final int remainingStreams = activeStreams.decrementAndGet();
    if (remainingStreams < 0) {
      activeStreams.incrementAndGet();
      throw new IllegalStateException("Released an inbound RPC stream that was not acquired");
    }
  }

  public int getMaximumActiveStreams() {
    return maximumActiveStreams;
  }

  int getActiveStreams() {
    return activeStreams.get();
  }
}
