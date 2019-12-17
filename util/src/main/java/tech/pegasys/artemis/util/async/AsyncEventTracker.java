package tech.pegasys.artemis.util.async;

import com.google.common.eventbus.EventBus;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AsyncEventTracker<K, V> {

  private final ConcurrentMap<K, CompletableFuture<V>> requests = new ConcurrentHashMap<>();
  private final EventBus eventBus;

  public AsyncEventTracker(final EventBus eventBus) {
    this.eventBus = eventBus;
  }

  public CompletableFuture<V> sendRequest(final K key, final Object eventToSend) {
    final CompletableFuture<V> future =
        requests.computeIfAbsent(key, k -> new CompletableFuture<>());
    eventBus.post(eventToSend);
    return future;
  }

  public void onResponse(final K key, final V value) {
    final CompletableFuture<V> future = requests.remove(key);
    if (future != null) {
      future.complete(value);
    }
  }
}
