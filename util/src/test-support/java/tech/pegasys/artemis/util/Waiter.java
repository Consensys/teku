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

package tech.pegasys.artemis.util;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.awaitility.Awaitility;
import org.awaitility.pollinterval.IterativePollInterval;

/**
 * A simpler wrapper around Awaitility that directs people towards best practices for waiting. The
 * native Awaitility wrapper has a number of "gotchas" that can lead to intermittency which this
 * wrapper aims to prevent.
 */
public class Waiter {

  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private static final Duration INITIAL_POLL_INTERVAL = Duration.ofMillis(200);
  private static final Duration MAX_POLL_INTERVAL = Duration.ofSeconds(5);

  public static void waitFor(final Condition assertion) {
    Awaitility.waitAtMost(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .ignoreExceptions()
        .pollInterval(
            IterativePollInterval.iterative(Waiter::nextPollInterval, INITIAL_POLL_INTERVAL))
        .untilAsserted(assertion::run);
  }

  private static Duration nextPollInterval(final Duration duration) {
    final Duration nextInterval = duration.multipliedBy(2);
    return nextInterval.compareTo(MAX_POLL_INTERVAL) <= 0 ? nextInterval : MAX_POLL_INTERVAL;
  }

  public static <T> T waitFor(final CompletableFuture<T> future)
      throws InterruptedException, ExecutionException, TimeoutException {
    return future.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }

  public interface Condition {
    void run() throws Throwable;
  }
}
