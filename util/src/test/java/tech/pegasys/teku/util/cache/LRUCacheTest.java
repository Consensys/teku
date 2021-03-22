package tech.pegasys.teku.util.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class LRUCacheTest {

  @Test
  void concurrencyTest() {
    Random random = new Random();
    int threadsCount = 16;
    int cacheMaxSize = 256;
    LRUCache<Integer, Integer> cache = new LRUCache<>(cacheMaxSize);
    ExecutorService executor = Executors.newFixedThreadPool(threadsCount);

    CompletableFuture[] futures = Stream
        .generate(() -> CompletableFuture.runAsync(() -> {
          while (!Thread.interrupted()) {
            for (int i = 0; i < cacheMaxSize * 16; i++) {
              int key = random.nextInt(cacheMaxSize * 2);
              Integer value = cache.get(key, idx -> idx);
              assertThat(value).isEqualTo(key);
            }
            assertThat(cache.size()).isLessThanOrEqualTo(cacheMaxSize);
            for (int i = 0; i < cacheMaxSize; i++) {
              int key = random.nextInt(cacheMaxSize * 2);
              cache.invalidate(key);
            }
            assertThat(cache.size()).isLessThanOrEqualTo(cacheMaxSize);

            if (random.nextInt(threadsCount * 2) == 0) {
              cache.clear();
            }

            Cache<Integer, Integer> cache1 = cache.copy();
            for (int i = 0; i < cacheMaxSize * 16; i++) {
              int key = random.nextInt(cacheMaxSize * 2);
              Integer value = cache1.get(key, idx -> idx);
              assertThat(value).isEqualTo(key);
            }
            assertThat(cache1.size()).isLessThanOrEqualTo(cacheMaxSize);
            for (int i = 0; i < cacheMaxSize; i++) {
              int key = random.nextInt(cacheMaxSize * 2);
              cache1.invalidate(key);
              assertThat(cache1.size()).isLessThanOrEqualTo(cacheMaxSize);
            }
          }
        }, executor)).limit(threadsCount).toArray(CompletableFuture[]::new);

    CompletableFuture<Object> any = CompletableFuture.anyOf(futures);

    System.out.println("Waiting if any thread fails...");
    assertThatThrownBy(() -> any.get(5, TimeUnit.SECONDS))
        .isInstanceOf(TimeoutException.class);
    System.out.println("Shutting down...");
    executor.shutdownNow();
  }
}
