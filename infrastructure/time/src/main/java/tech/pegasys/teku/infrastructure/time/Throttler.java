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

package tech.pegasys.teku.infrastructure.time;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Throttler<TResource> {
  // The wrapped resouce can be invoked at most once every throttling period
  private final TResource resource;
  private final UInt64 throttlingPeriod;
  private final AtomicReference<UInt64> lastInvoked = new AtomicReference<>(null);

  public Throttler(final TResource resource, final UInt64 throttlingPeriod) {
    this.resource = resource;
    this.throttlingPeriod = throttlingPeriod;
  }

  public void invoke(final UInt64 currentTime, Consumer<TResource> invocation) {
    if (updateLastInvoked(currentTime)) {
      invocation.accept(resource);
    }
  }

  private boolean updateLastInvoked(final UInt64 currentTime) {
    final AtomicBoolean updated = new AtomicBoolean(false);
    lastInvoked.updateAndGet(
        last -> {
          if (last == null || last.plus(throttlingPeriod).isLessThanOrEqualTo(currentTime)) {
            updated.set(true);
            return currentTime;
          }
          return last;
        });
    return updated.get();
  }
}
