package tech.pegasys.artemis.util.async;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AsyncEventTracker<K, V> {
  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          new ThreadFactoryBuilder().setDaemon(true).setNameFormat("event-timeout-%d").build());
  private final ConcurrentMap<K, CompletableFuture<V>> requests = new ConcurrentHashMap<>();
  private final EventBus eventBus;

  public AsyncEventTracker(final EventBus eventBus) {
    this.eventBus = eventBus;
  }

  public CompletableFuture<V> sendRequest(final K key, final Object eventToSend) {
    final CompletableFuture<V> future =
        requests.computeIfAbsent(key, k -> new CompletableFuture<>());
    eventBus.post(eventToSend);
    executor.schedule(
        () ->
            future.completeExceptionally(
                new TimeoutException("Timeout waiting for async event response with key " + key)),
        5,
        TimeUnit.SECONDS);
    return future;
  }

  public void onResponse(final K key, final V value) {
    final CompletableFuture<V> future = requests.remove(key);
    if (future != null) {
      future.complete(value);
    }
  }
}
