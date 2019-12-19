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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class AsyncEventTracker<K, V> {
  private final ConcurrentMap<K, GoodFuture<V>> requests = new ConcurrentHashMap<>();
  private final EventBus eventBus;

  public AsyncEventTracker(final EventBus eventBus) {
    this.eventBus = eventBus;
  }

  public GoodFuture<V> sendRequest(final K key, final Object eventToSend) {
    final GoodFuture<V> future = requests.computeIfAbsent(key, k -> new GoodFuture<>());
    eventBus.post(eventToSend);
    return future.orTimeout(5, TimeUnit.SECONDS);
  }

  public void onResponse(final K key, final V value) {
    final GoodFuture<V> future = requests.remove(key);
    if (future != null) {
      future.complete(value);
    }
  }
}
