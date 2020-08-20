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

package tech.pegasys.teku.core.stategenerator;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntSupplier;
import java.util.stream.Stream;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.metrics.TekuMetricCategory;
import tech.pegasys.teku.util.collections.LimitedMap;

public class CachingTaskQueue<K, V> {

  private final Counter cachedTaskCounter;
  private final Counter duplicateTaskCounter;
  private final Counter newTaskCounter;
  private final Counter rebasedTaskCounter;

  private final Lock lock = new ReentrantLock();

  private final ConcurrentMap<K, SafeFuture<Optional<V>>> pendingTasks = new ConcurrentHashMap<>();
  private final AtomicInteger activeTasks = new AtomicInteger(0);
  private final Queue<CacheableTask<K, V>> queuedTasks = new ConcurrentLinkedQueue<>();

  private final Map<K, V> cache;
  private final MetricsSystem metricsSystem;
  private final String metricsPrefix;
  private final IntSupplier activeTaskLimit;

  CachingTaskQueue(
      final MetricsSystem metricsSystem,
      final String metricsPrefix,
      final IntSupplier activeTaskLimit,
      final int maxCacheSize) {
    this.metricsSystem = metricsSystem;
    this.metricsPrefix = metricsPrefix;
    this.activeTaskLimit = activeTaskLimit;
    this.cache = LimitedMap.createSoft(maxCacheSize);

    final LabelledMetric<Counter> labelledCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.STORAGE,
            metricsPrefix + "_tasks_total",
            "Total number of tasks requested",
            "type");
    duplicateTaskCounter = labelledCounter.labels("duplicate");
    cachedTaskCounter = labelledCounter.labels("cached");
    newTaskCounter = labelledCounter.labels("new");
    rebasedTaskCounter = labelledCounter.labels("rebase");
  }

  public static <K, V> CachingTaskQueue<K, V> create(
      final MetricsSystem metricsSystem, final String metricsPrefix, final int maxCacheSize) {
    return new CachingTaskQueue<>(
        metricsSystem,
        metricsPrefix,
        () -> Math.max(2, Runtime.getRuntime().availableProcessors()),
        maxCacheSize);
  }

  public void startMetrics() {
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.STORAGE,
        metricsPrefix + "_tasks_requested",
        "Number of tasks requested but not yet completed",
        pendingTasks::size);
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.STORAGE,
        metricsPrefix + "_tasks_active",
        "Number of tasks actively being processed",
        activeTasks::get);
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.STORAGE,
        metricsPrefix + "_tasks_queued",
        "Number of tasks queued for later processing",
        queuedTasks::size);
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.STORAGE,
        metricsPrefix + "_cache_size",
        "Number of checkpoint states held in the in-memory store",
        cache::size);
  }

  public SafeFuture<Optional<V>> perform(final CacheableTask<K, V> task) {
    lock.lock();
    try {
      // Check if a completed result is available
      final V cachedResult = cache.get(task.getKey());
      if (cachedResult != null) {
        cachedTaskCounter.inc();
        return SafeFuture.completedFuture(Optional.of(cachedResult));
      }

      // Check if the task is already scheduled
      final SafeFuture<Optional<V>> currentPendingTask = pendingTasks.get(task.getKey());
      if (currentPendingTask != null) {
        duplicateTaskCounter.inc();
        return currentPendingTask;
      }

      // Check if there's a better starting point (in cache or in progress)
      final Optional<SafeFuture<Optional<V>>> newBase =
          task.streamIntermediateSteps()
              .map(
                  key ->
                      Optional.ofNullable(cache.get(key))
                          .map(value -> SafeFuture.completedFuture(Optional.of(value)))
                          .orElse(pendingTasks.get(key)))
              .filter(Objects::nonNull)
              .findFirst();
      if (newBase.isPresent()) {
        rebasedTaskCounter.inc();
        return newBase.get().thenCompose(ancestorResult -> queueTask(task.rebase(ancestorResult)));
      }

      // Schedule the task for execution
      newTaskCounter.inc();
      return queueTask(task);
    } finally {
      lock.unlock();
    }
  }

  public Optional<V> getIfAvailable(final K key) {
    return Optional.of(cache.get(key));
  }

  private SafeFuture<Optional<V>> queueTask(final CacheableTask<K, V> task) {
    final SafeFuture<Optional<V>> generationResult = new SafeFuture<>();
    pendingTasks.put(task.getKey(), generationResult);
    queuedTasks.add(task);
    tryProcessNext();
    return generationResult;
  }

  private void tryProcessNext() {
    lock.lock();
    try {
      while (activeTasks.get() < activeTaskLimit.getAsInt() && !queuedTasks.isEmpty()) {
        processNext();
      }
    } finally {
      lock.unlock();
    }
  }

  private void processNext() {
    final CacheableTask<K, V> task = queuedTasks.poll();
    if (task == null) {
      activeTasks.decrementAndGet();
      return;
    }
    activeTasks.incrementAndGet();
    task.performTask()
        .thenPeek(result -> result.ifPresent(value -> cacheResult(task.getKey(), value)))
        .whenComplete((result, error) -> completePendingTask(task, result, error))
        .alwaysRun(
            () -> {
              activeTasks.decrementAndGet();
              tryProcessNext();
            })
        .reportExceptions();
  }

  private void completePendingTask(
      final CacheableTask<K, V> task, final Optional<V> result, final Throwable error) {
    lock.lock();
    try {
      final SafeFuture<Optional<V>> future = pendingTasks.remove(task.getKey());
      if (error != null) {
        future.completeExceptionally(error);
      } else {
        future.complete(result);
      }
    } finally {
      lock.unlock();
    }
  }

  private void cacheResult(final K key, final V value) {
    lock.lock();
    try {
      cache.put(key, value);
    } finally {
      lock.unlock();
    }
  }

  public interface CacheableTask<K, V> {
    /**
     * The key that uniquely identifies this task. Two tasks with equal keys should also have
     * equivalent results.
     */
    K getKey();

    /**
     * Return the keys for intermediate steps that this task can be rebased on top of, in order of
     * desireability (ie the best starting point first).
     *
     * @return stream of intermediate step keys.
     */
    Stream<K> streamIntermediateSteps();

    /**
     * Return a new CacheableTask which starts from the supplied value. The value must be the value
     * generated by one of the intermediate steps returned by {@link #streamIntermediateSteps()}.
     *
     * @param newBaseValue the intermediate value to start generation from.
     * @return the new task.
     */
    CacheableTask<K, V> rebase(final V newBaseValue);

    default CacheableTask<K, V> rebase(final Optional<V> newBaseValue) {
      return newBaseValue.map(this::rebase).orElse(this);
    }

    /**
     * Perform the task to create the value.
     *
     * @return a future that contains the final task output or empty optional if the task could not
     *     access the required input data.
     */
    SafeFuture<Optional<V>> performTask();
  }
}
