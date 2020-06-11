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

package tech.pegasys.teku.pow;

import static tech.pegasys.teku.logging.StatusLogger.STATUS_LOG;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Eth1StatusLogger {
  private static final int LOG_INTERVAL = 30000;
  private final AtomicBoolean timerActive = new AtomicBoolean(false);
  private Instant startInstant;
  private ScheduledExecutorService executor;

  private void start() {
    this.startInstant = Instant.now();
    this.timerActive.set(true);
    this.executor = Executors.newScheduledThreadPool(1);
    executor.scheduleAtFixedRate(
        () ->
            STATUS_LOG.eth1ServiceDown(Duration.between(startInstant, Instant.now()).getSeconds()),
        LOG_INTERVAL,
        LOG_INTERVAL,
        TimeUnit.MILLISECONDS);
  }

  private void stop() {
    this.executor.shutdownNow();
    this.executor = null;
    this.timerActive.set(false);
    this.startInstant = null;
  }

  synchronized void fail() {
    if (!this.timerActive.get()) {
      start();
    }
  }

  synchronized void success() {
    if (this.timerActive.get()) {
      stop();
    }
  }
}
