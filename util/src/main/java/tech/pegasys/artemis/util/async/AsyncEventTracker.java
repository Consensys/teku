/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.util.async;

import com.google.common.eventbus.EventBus;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class AsyncEventTracker<K, V> {
  private final ConcurrentMap<K, SafeFuture<V>> requests = new ConcurrentHashMap<>();
  private final EventBus eventBus;

  public AsyncEventTracker(final EventBus eventBus) {
    this.eventBus = eventBus;
  }

  public SafeFuture<V> sendRequest(final K key, final Object eventToSend, final Duration timeout) {
    final SafeFuture<V> future = requests.computeIfAbsent(key, k -> new SafeFuture<>());
    eventBus.post(eventToSend);
    return future
        .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
        .whenComplete((res, err) -> requests.remove(key, future));
  }

  public void onResponse(final K key, final V value) {
    final SafeFuture<V> future = requests.remove(key);
    if (future != null) {
      future.complete(value);
    }
  }
}
