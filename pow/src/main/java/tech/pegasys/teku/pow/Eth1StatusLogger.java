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

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.time.Duration;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Eth1StatusLogger {
  private static final Logger LOG = LogManager.getLogger();
  private static final Duration LOG_INTERVAL = Duration.ofSeconds(30);

  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;

  private Optional<Cancellable> activeReporter = Optional.empty();

  public Eth1StatusLogger(final AsyncRunner asyncRunner, final TimeProvider timeProvider) {
    this.asyncRunner = asyncRunner;
    this.timeProvider = timeProvider;
  }

  synchronized void success() {
    activeReporter.ifPresent(
        reporter -> {
          reporter.cancel();
          activeReporter = Optional.empty();
        });
  }

  synchronized void fail() {
    if (activeReporter.isEmpty()) {
      final UInt64 outageStartInSeconds = timeProvider.getTimeInSeconds();
      final Cancellable reporter =
          asyncRunner.runWithFixedDelay(
              () -> reportOutage(outageStartInSeconds),
              LOG_INTERVAL,
              error -> LOG.error("Failed to check Eth1 status", error));
      activeReporter = Optional.of(reporter);
    }
  }

  private void reportOutage(final UInt64 outageStartInSeconds) {
    STATUS_LOG.eth1ServiceDown(
        timeProvider.getTimeInSeconds().minus(outageStartInSeconds).longValue());
  }
}
