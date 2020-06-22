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

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.async.Cancellable;
import tech.pegasys.teku.util.time.TimeProvider;

public class Eth1StatusLogger {
  private static final Logger LOG = LogManager.getLogger();
  private static final int LOG_INTERVAL = 30000;

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
      final UnsignedLong outageStartInSeconds = timeProvider.getTimeInSeconds();
      final Cancellable reporter =
          asyncRunner.runWithFixedDelay(
              () -> reportOutage(outageStartInSeconds),
              LOG_INTERVAL,
              TimeUnit.MILLISECONDS,
              error -> LOG.error("Failed to check Eth1 status", error));
      activeReporter = Optional.of(reporter);
    }
  }

  private void reportOutage(final UnsignedLong outageStartInSeconds) {
    STATUS_LOG.eth1ServiceDown(
        timeProvider.getTimeInSeconds().minus(outageStartInSeconds).longValue());
  }
}
