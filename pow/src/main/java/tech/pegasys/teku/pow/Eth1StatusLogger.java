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

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Eth1StatusLogger {
  private final AtomicBoolean timerActive = new AtomicBoolean();
  private final AtomicInteger failCount = new AtomicInteger(0);
  private final long interval;
  private final TimerTask timerTask;
  private Timer timer;

  public Eth1StatusLogger(final long interval) {
    this.interval = interval;
    this.timerTask =
        new TimerTask() {
          @Override
          public void run() {
            if (failCount.get() > 1) {
              STATUS_LOG.eth1ServiceDown(Eth1StatusLogger.this.interval);
              failCount.set(0);
            } else {
              stop();
            }
          }
        };
  }

  private void start() {
    this.timer = new Timer();
    this.timer.scheduleAtFixedRate(timerTask, interval / 2, interval);
    this.timerActive.set(true);
  }

  private void stop() {
    this.timer.cancel();
    this.timerActive.set(false);
    this.failCount.set(0);
    this.timer = null;
  }

  synchronized void incrementFail() {
    if (!this.timerActive.get()) {
      start();
    }
    failCount.incrementAndGet();
  }
}
