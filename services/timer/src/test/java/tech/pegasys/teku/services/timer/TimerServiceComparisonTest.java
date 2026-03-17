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

package tech.pegasys.teku.services.timer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import tech.pegasys.teku.service.serviceutils.Service;

class TimerServiceComparisonTest {

  // Short interval for tests so wall-clock time stays low
  private static final int TICK_INTERVAL_MS = 100;

  private Service serviceUnderTest;
  private static final Logger LOG = LogManager.getLogger();

  @AfterEach
  void tearDown() throws Exception {
    if (serviceUnderTest != null) {
      serviceUnderTest.stop().get(5, TimeUnit.SECONDS);
      serviceUnderTest = null;
    }
  }

  /**
   * For short (instant) operations both services should fire a similar number of ticks within the
   * same wall-clock window.
   */
  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shortOperations_bothTimerServicesBehaveEquivalently() throws Exception {
    final AtomicInteger timerCounter = new AtomicInteger();
    final AtomicInteger quartzCounter = new AtomicInteger();

    final int timerCount =
        countTicks(
            new TimerService(timerCounter::incrementAndGet, TICK_INTERVAL_MS), timerCounter, 350);
    final int quartzCount =
        countTicks(
            new QuartzTimerService(quartzCounter::incrementAndGet, TICK_INTERVAL_MS),
            quartzCounter,
            350);

    LOG.debug("Quartz timer count {}, Timer service count {}", quartzCount, timerCount);
    assertThat(timerCount).isBetween(2, 5);
    assertThat(quartzCount).isBetween(2, 5);
  }

  /**
   * When the first tick is slow (4× the interval), neither service should produce a catch-up burst.
   *
   * <p>TimerService uses {@code scheduleWithFixedDelay}, which waits the full interval after each
   * completion. After the slow tick completes, the next tick fires one interval later — missed
   * periods are simply not scheduled.
   *
   * <p>QuartzTimerService uses {@code withMisfireHandlingInstructionNextWithRemainingCount} with a
   * misfireThreshold equal to the interval. Triggers that are more than one interval late are
   * skipped entirely, so no catch-up burst occurs either.
   *
   * <p>Both services should therefore produce a similar, moderate tick count (~5–6 in the window).
   */
  @Test
  @DisabledOnOs(OS.WINDOWS)
  void longOperations_bothServicesSkipMissedTicksWithoutCatchup() throws Exception {
    final AtomicInteger timerCounter = new AtomicInteger();
    final AtomicInteger quartzCounter = new AtomicInteger();

    // Slow tick sleeps 4× the interval; window is ~10 intervals total
    final int slowMs = TICK_INTERVAL_MS * 4;
    final int timerCount =
        countTicks(
            new TimerService(slowOnFirstHandler(timerCounter, slowMs), TICK_INTERVAL_MS),
            timerCounter,
            1000);
    final int quartzCount =
        countTicks(
            new QuartzTimerService(slowOnFirstHandler(quartzCounter, slowMs), TICK_INTERVAL_MS),
            quartzCounter,
            1000);

    // Without a catch-up burst the count stays low for both services.
    // (A scheduleAtFixedRate implementation would produce many more ticks from the burst.)
    LOG.debug("Quartz timer count {}, Timer service count {}", quartzCount, timerCount);
    assertThat(timerCount).isBetween(4, 8);
    assertThat(quartzCount).isBetween(4, 8);
  }

  // --- helpers ---

  private int countTicks(final Service service, final AtomicInteger counter, final long windowMs)
      throws Exception {
    serviceUnderTest = service;
    service.start().get(5, TimeUnit.SECONDS);
    Thread.sleep(windowMs);
    service.stop().get(5, TimeUnit.SECONDS);
    serviceUnderTest = null;
    return counter.get();
  }

  /**
   * Returns a handler that increments {@code counter} on every tick and sleeps {@code sleepMs} on
   * the very first call to simulate a slow operation.
   */
  private TimeTickHandler slowOnFirstHandler(final AtomicInteger counter, final int sleepMs) {
    final AtomicBoolean first = new AtomicBoolean(true);
    return () -> {
      counter.incrementAndGet();
      if (first.compareAndSet(true, false)) {
        try {
          Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    };
  }
}
