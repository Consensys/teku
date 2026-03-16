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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;

public class TimerService extends Service {
  private static final Logger LOG = LogManager.getLogger();
  public static final double TIME_TICKER_REFRESH_RATE = 2; // per sec

  private final TimeTickHandler timeTickHandler;
  private final long intervalMs;
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            final Thread t = new Thread(r, "TimeTick");
            t.setDaemon(true);
            return t;
          });
  private ScheduledFuture<?> scheduledTask;

  public TimerService(final TimeTickHandler timeTickHandler) {
    this(timeTickHandler, (long) ((1.0 / TIME_TICKER_REFRESH_RATE) * 1000));
  }

  TimerService(final TimeTickHandler timeTickHandler, final long intervalMs) {
    LOG.debug("Initialize TimerService with tick interval: {} ms", intervalMs);
    this.timeTickHandler = timeTickHandler;
    this.intervalMs = intervalMs;
  }

  @Override
  public SafeFuture<?> doStart() {
    scheduledTask =
        scheduler.scheduleWithFixedDelay(
            timeTickHandler::onTick, 0, intervalMs, TimeUnit.MILLISECONDS);
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<?> doStop() {
    scheduledTask.cancel(false);
    scheduler.shutdown();
    return SafeFuture.COMPLETE;
  }
}
