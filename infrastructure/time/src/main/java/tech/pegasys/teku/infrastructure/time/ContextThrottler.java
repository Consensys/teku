/*
 * Copyright Consensys Software Inc., 2025
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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Thread-safe (if appropriate map provided) context base throttler
 *
 * @param <TResource> Invoked resource
 * @param <TContext> Context which will be compared before invocation
 */
public class ContextThrottler<TResource, TContext> {
  // The wrapped resource can be invoked at most once every throttling period for matching context
  private final TResource resource;
  private final UInt64 throttlingPeriod;
  private final Map<TContext, AtomicReference<UInt64>> contextCache;

  public ContextThrottler(
      final TResource resource,
      final UInt64 throttlingPeriod,
      final Map<TContext, AtomicReference<UInt64>> contextCache) {
    checkNotNull(throttlingPeriod, "Missing throttling period");
    this.resource = resource;
    this.throttlingPeriod = throttlingPeriod;
    this.contextCache = contextCache;
  }

  public void invoke(
      final UInt64 currentTime, final TContext context, final Consumer<TResource> invocation) {
    checkNotNull(currentTime, "Missing current time");
    if (updateLastInvoked(currentTime, context)) {
      invocation.accept(resource);
    }
  }

  /**
   * If the event at the current time should be throttled, does not update lastInvoked for provided
   * context and returns false. Otherwise, updates lastInvoked for that context and returns true.
   *
   * @param currentTime The current time
   * @param context Invokation context
   * @return True if lastInvoked was updated (event should not be throttled), false otherwise
   */
  private boolean updateLastInvoked(final UInt64 currentTime, final TContext context) {
    final AtomicReference<UInt64> oldTime =
        contextCache.computeIfAbsent(context, __ -> new AtomicReference<>(UInt64.MAX_VALUE));
    if (oldTime.compareAndSet(UInt64.MAX_VALUE, currentTime)) {
      // first appearance in cache
      return true;
    } else {
      final UInt64 old = oldTime.get();
      if (currentTime.isGreaterThanOrEqualTo(old.plus(throttlingPeriod))
          && oldTime.compareAndSet(old, currentTime)) {
        // already in cache and throttlingPeriod has passed
        return true;
      }
    }
    return false;
  }
}
